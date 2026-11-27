#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <time.h>
#include <signal.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <inttypes.h>
#include <poll.h>
#include <pthread.h>

/* Read-only display-cache recorder. No UART, memory writes, or network socket. */
#define SOURCE_SHA "df0c10a7c72a710ff5fb703c39e97a47e69f4fc2032e0fb6be54000784665473"
#define APP_GLOBAL 0x1aced0u
#define REG_OFFSET 0xfe8u
#define REG_COUNT 112u
#define PREF_OFFSET 0x5bu
#define PREF_COUNT 13u
static const char *procroot="/proc";
static volatile sig_atomic_t stopped=0;
static int idle_trial=0,stream_mode=0;
static void stop(int s){(void)s;stopped=1;}
static _Noreturn void fail(const char *s){dprintf(STDERR_FILENO,"RP_CAPTURE_ERROR: %s\n",s);_exit(2);}
static void path(char *out,size_t n,long pid,const char *leaf){
 if(snprintf(out,n,"%s/%ld/%s",procroot,pid,leaf)>=(int)n)fail("path too long");
}
static uint32_t le32(const unsigned char *p){return (uint32_t)p[0]|(uint32_t)p[1]<<8|(uint32_t)p[2]<<16|(uint32_t)p[3]<<24;}
static uint16_t le16(const unsigned char *p){return (uint16_t)p[0]|(uint16_t)p[1]<<8;}
static void exact(int fd,uint64_t off,void *b,size_t n){
 size_t done=0;while(done<n){ssize_t r=pread(fd,(char *)b+done,n-done,(off_t)(off+done));if(r<0&&errno==EINTR)continue;if(r<=0)fail("short or failed process-memory read");done+=(size_t)r;}
}
static uint64_t starttime(long pid){
 char p[512],b[4096];path(p,sizeof p,pid,"stat");FILE *f=fopen(p,"r");if(!f)fail("process disappeared");
 if(!fgets(b,sizeof b,f)){fclose(f);fail("cannot read process identity");}fclose(f);
 char *x=strrchr(b,')');if(!x||x[1]!=' ')fail("invalid process stat");x+=2;
 char *save=NULL,*t=strtok_r(x," ", &save);for(int field=3;field<22&&t;field++)t=strtok_r(NULL," ",&save);
 if(!t)fail("incomplete process stat");char *end;errno=0;uint64_t v=strtoull(t,&end,10);if(errno||end==t||(*end&&*end!='\n'))fail("invalid process start time");return v;
}
static int mapped(long pid,uint64_t lo,uint64_t n){
 char p[512],line[2048],perm[8];unsigned long long a,b;path(p,sizeof p,pid,"maps");FILE *f=fopen(p,"r");if(!f)fail("cannot inspect memory mappings");int ok=0;
 while(fgets(line,sizeof line,f))if(sscanf(line,"%llx-%llx %7s",&a,&b,perm)==3&&perm[0]=='r'&&perm[1]=='w'&&lo>=a&&lo+n<=b){ok=1;break;}
 fclose(f);return ok;
}
static struct stat exestat(long pid){char p[512];struct stat s;path(p,sizeof p,pid,"exe");if(stat(p,&s))fail("cannot inspect executable");return s;}
static int sameexe(struct stat a,struct stat b){return a.st_dev==b.st_dev&&a.st_ino==b.st_ino&&a.st_size==b.st_size&&a.st_mtim.tv_sec==b.st_mtim.tv_sec&&a.st_mtim.tv_nsec==b.st_mtim.tv_nsec&&a.st_ctim.tv_sec==b.st_ctim.tv_sec&&a.st_ctim.tv_nsec==b.st_ctim.tv_nsec;}
static void verify_hash(long pid){
 char executable[512];
 path(executable,sizeof executable,pid,"exe");
 int pipes[2];if(pipe(pipes))fail("hash pipe failed");pid_t child=fork();if(child<0)fail("hash fork failed");
 if(child==0){
  close(pipes[0]);
  if(dup2(pipes[1],STDOUT_FILENO)<0)_exit(127);
  if(pipes[1]!=STDOUT_FILENO)close(pipes[1]);
  execl("/usr/bin/sha256sum","sha256sum",executable,(char*)NULL);
  if(errno==ENOENT)execl("/bin/sha256sum","sha256sum",executable,(char*)NULL);
  _exit(127);
 }
 close(pipes[1]);char out[1024];size_t used=0;ssize_t n;while((n=read(pipes[0],out+used,sizeof out-1-used))>0){used+=(size_t)n;if(used==sizeof out-1)break;}close(pipes[0]);out[used]=0;
 int status;if(stopped)kill(child,SIGTERM);while(waitpid(child,&status,0)<0){if(errno!=EINTR)fail("hash wait failed");if(stopped)kill(child,SIGTERM);}
 if(!WIFEXITED(status)||WEXITSTATUS(status)||used<65||strncmp(out,SOURCE_SHA,64)||out[64]!=' ')fail("unsupported display executable SHA256");
}
static uint64_t ns(int clock){struct timespec t;if(clock_gettime(clock,&t))fail("clock failed");return (uint64_t)t.tv_sec*1000000000u+(uint64_t)t.tv_nsec;}
static long number(const char *s,long min,long max){char *end;errno=0;long v=strtol(s,&end,10);if(errno||end==s||*end||v<min||v>max)fail("invalid numeric argument");return v;}
/* A stream is leased by the phone. No recordings are written on the machine.
   Each ACK follows the phone's durable append. Missing/invalid ACK ends the lease. */
static void write_timeout(int signal){(void)signal;_exit(4);}
static uint64_t acknowledge(uint64_t seq){
 char expected[64],line[64];size_t used=0;
 snprintf(expected,sizeof expected,"ACK %" PRIu64,seq);
 uint64_t ack_start=ns(CLOCK_MONOTONIC),deadline=ack_start+15000000000ull;
 while(!stopped){
  uint64_t now=ns(CLOCK_MONOTONIC);if(now>=deadline)fail("phone acknowledgement timed out");
  struct pollfd p={STDIN_FILENO,POLLIN,0};int wait_ms=(int)((deadline-now)/1000000ull)+1;if(wait_ms>200)wait_ms=200;int rc=poll(&p,1,wait_ms);
  if(rc<0&&errno==EINTR)continue;if(rc<0)fail("acknowledgement poll failed");if(!rc)continue;
  char c;ssize_t n=read(STDIN_FILENO,&c,1);if(n<0&&errno==EINTR)continue;if(n!=1)fail("phone disconnected");
  if(c=='\n'){if(!used)continue;line[used]=0;if(strcmp(line,expected))fail("invalid phone acknowledgement");return ns(CLOCK_MONOTONIC)-ack_start;}
  if(c=='\r')continue;if(used>=sizeof line-1)fail("oversized acknowledgement");line[used++]=c;
 }
 return 0;
}
/* Fixed RAM queue: the sampler never waits for network or phone storage.
   Overflow stops explicitly instead of dropping samples. No machine log files. */
#ifndef QUEUE_SLOTS
#define QUEUE_SLOTS 64
#endif
#define ROW_BYTES 2048
static struct { char row[ROW_BYTES]; uint64_t seq,queued_at; } queue[QUEUE_SLOTS];
static unsigned head=0,tail=0,queued=0;
static int producer_done=0;
static pthread_mutex_t queue_lock=PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t queue_ready=PTHREAD_COND_INITIALIZER;
static void enqueue(const char *row,uint64_t seq){
 pthread_mutex_lock(&queue_lock);
 if(queued==QUEUE_SLOTS){pthread_mutex_unlock(&queue_lock);fail("delivery buffer full; recording stopped without dropping samples");}
 memcpy(queue[tail].row,row,strlen(row)+1);queue[tail].seq=seq;queue[tail].queued_at=ns(CLOCK_MONOTONIC);
 tail=(tail+1)%QUEUE_SLOTS;queued++;pthread_cond_signal(&queue_ready);pthread_mutex_unlock(&queue_lock);
}
static void *deliver(void *unused){
 (void)unused;uint64_t idle_since=ns(CLOCK_MONOTONIC),previous_ack_wait=0;
 while(!stopped){
  char row[ROW_BYTES];uint64_t seq,queued_at;
  pthread_mutex_lock(&queue_lock);
  while(!queued&&!producer_done&&!stopped){
   struct timespec until;clock_gettime(CLOCK_REALTIME,&until);until.tv_nsec+=100000000;
   if(until.tv_nsec>=1000000000){until.tv_sec++;until.tv_nsec-=1000000000;}
   pthread_cond_timedwait(&queue_ready,&queue_lock,&until);
   if(ns(CLOCK_MONOTONIC)-idle_since>5000000000ull){pthread_mutex_unlock(&queue_lock);fail("sampler stalled");}
  }
  if(stopped||(!queued&&producer_done)){pthread_mutex_unlock(&queue_lock);break;}
  memcpy(row,queue[head].row,ROW_BYTES);seq=queue[head].seq;queued_at=queue[head].queued_at;head=(head+1)%QUEUE_SLOTS;queued--;
  pthread_mutex_unlock(&queue_lock);
  size_t len=strlen(row);if(len<2)fail("invalid queued record");
  int added=snprintf(row+len-2,sizeof(row)-len+2,",\"delivery_queue_ns\":%" PRIu64 ",\"previous_ack_wait_ns\":%" PRIu64 "}\n",ns(CLOCK_MONOTONIC)-queued_at,previous_ack_wait);
  if(added<0||(size_t)added>=sizeof(row)-len+2)fail("queued record exceeds bound");
  alarm(15);if(fputs(row,stdout)==EOF||fflush(stdout)||ferror(stdout))fail("output write failed");alarm(0);
  previous_ack_wait=acknowledge(seq);idle_since=ns(CLOCK_MONOTONIC);
 }
 return NULL;
}
int main(int argc,char **argv){
#ifdef RP_TEST_ROOT
 const char *r=getenv("RP_TEST_PROC_ROOT");if(r)procroot=r;
#endif
 if(argc==2&&!strcmp(argv[1],"--self-test")){unsigned char test[4]={0x78,0x56,0x34,0x12};if(le32(test)!=0x12345678||le16(test)!=0x5678)fail("byte conversion failed");printf("RP_CAPTURE_SELF_TEST_OK pointer_bits=%zu monotonic_ns=%" PRIu64 " boottime_ns=%" PRIu64 "\n",sizeof(void*)*8,ns(CLOCK_MONOTONIC),ns(CLOCK_BOOTTIME));return 0;}
 if(argc==3&&!strcmp(argv[1],"--idle-trial"))idle_trial=1;
 if(argc==3&&!strcmp(argv[1],"--stream"))stream_mode=1;
 if(argc!=4&&!idle_trial&&!stream_mode){fprintf(stderr,"Usage: rp-capture PID PERIOD_MS SAMPLE_COUNT\nPERIOD_MS: 50..5000; SAMPLE_COUNT: 1..1000000. Ctrl-C stops.\n");return 2;}
 long pid=number(argv[(idle_trial||stream_mode)?2:1],1,2147483647),period=(idle_trial||stream_mode)?500:number(argv[2],50,5000),count=idle_trial?10:stream_mode?14400:number(argv[3],1,1000000);
 struct sigaction action={0};action.sa_handler=stop;sigemptyset(&action.sa_mask);sigaction(SIGHUP,&action,NULL);sigaction(SIGINT,&action,NULL);sigaction(SIGTERM,&action,NULL);sigaction(SIGPIPE,&action,NULL);sigaction(SIGALRM,&action,NULL);if(idle_trial)alarm(10);if(stream_mode){struct sigaction timeout={0};timeout.sa_handler=write_timeout;sigemptyset(&timeout.sa_mask);sigaction(SIGALRM,&timeout,NULL);alarm(10);}
 uint64_t identity=starttime(pid);struct stat exe=exestat(pid);verify_hash(pid);
 if(starttime(pid)!=identity||!sameexe(exe,exestat(pid)))fail("identity changed during verification");
 if(!mapped(pid,APP_GLOBAL,4))fail("app global outside readable writable mapping");
 char p[512];path(p,sizeof p,pid,"mem");int fd=open(p,O_RDONLY|O_CLOEXEC);if(fd<0)fail("cannot open process memory read-only");
 unsigned char ptrbytes[4];exact(fd,APP_GLOBAL,ptrbytes,4);uint32_t app=le32(ptrbytes);
 if(app<65536||app>0x7fffffff||app%4||!mapped(pid,app+PREF_OFFSET,PREF_COUNT)||!mapped(pid,app+REG_OFFSET,REG_COUNT*2))fail("invalid application pointer or cache bounds");
 printf("{\"type\":\"metadata\",\"format\":\"rp-display-cache-v1\",\"stream_protocol\":%d,\"app_pointer\":%u,\"display_sha256\":\"%s\",\"pid\":%ld,\"start_ticks\":%" PRIu64 ",\"register_count\":112,\"period_ms\":%ld,\"source\":\"display_cache\",\"sensor_synchronous\":false}\n",stream_mode?2:0,app,SOURCE_SHA,pid,identity,period);fflush(stdout);if(stream_mode)alarm(0);
 pthread_t sender; if(stream_mode&&pthread_create(&sender,NULL,deliver,NULL))fail("delivery worker startup failed");
 uint64_t next=ns(CLOCK_MONOTONIC),session_start=next,seq=0,previous=0;unsigned char last[REG_COUNT*2]={0};uint64_t unchanged=0;
 while(!stopped&&seq<(uint64_t)count){
  if(stream_mode&&ns(CLOCK_MONOTONIC)-session_start>=7200000000000ull)break;
  if(starttime(pid)!=identity||!sameexe(exe,exestat(pid)))fail("process identity changed");
  if(!mapped(pid,app+REG_OFFSET,REG_COUNT*2)||!mapped(pid,app+PREF_OFFSET,PREF_COUNT))fail("cache mapping changed");
  unsigned char a[REG_COUNT*2],b[REG_COUNT*2],pa[PREF_COUNT],pb[PREF_COUNT];int stable=0;
  uint64_t begin=ns(CLOCK_BOOTTIME);
  for(int retry=0;retry<4;retry++){
   exact(fd,APP_GLOBAL,ptrbytes,4);if(le32(ptrbytes)!=app)fail("application pointer changed");
   exact(fd,app+PREF_OFFSET,pa,sizeof pa);exact(fd,app+REG_OFFSET,a,sizeof a);
   exact(fd,app+PREF_OFFSET,pb,sizeof pb);exact(fd,app+REG_OFFSET,b,sizeof b);
   if(!memcmp(a,b,sizeof a)&&!memcmp(pa,pb,sizeof pa)){stable=1;break;}
  }
  exact(fd,APP_GLOBAL,ptrbytes,4);if(le32(ptrbytes)!=app||starttime(pid)!=identity||!sameexe(exe,exestat(pid)))fail("identity changed during sample");
  uint64_t end=ns(CLOCK_BOOTTIME);if(!stable)fail("cache changed throughout bounded retries");
  if(idle_trial&&(le16(a+6*2)||le16(a+16*2)||le16(a+22*2)||le16(a+24*2)||(le16(a+33*2)&0x5240)))fail("idle trial stopped: grinder, water, brew, steam or pump active");
  if(seq&&!memcmp(last,a,sizeof a))unchanged++;else unchanged=0;memcpy(last,a,sizeof a);
  char row[ROW_BYTES];FILE *output=stream_mode?fmemopen(row,sizeof row,"w"):stdout;if(!output)fail("sample buffer failed");
  fprintf(output,"{\"type\":\"sample\",\"seq\":%" PRIu64 ",\"uptime_ns\":%" PRIu64 ",\"read_duration_ns\":%" PRIu64 ",\"gap_ns\":%" PRIu64 ",\"unchanged_register_samples\":%" PRIu64 ",\"preferences\":[",seq,begin,end-begin,previous?begin-previous:0,unchanged);
  for(unsigned i=0;i<PREF_COUNT;i++)fprintf(output,"%s%u",i?",":"",pa[i]);fprintf(output,"],\"registers\":[");
  for(unsigned i=0;i<REG_COUNT;i++)fprintf(output,"%s%u",i?",":"",le16(a+i*2));fprintf(output,"]}\n");if(fflush(output)||ferror(output))fail("sample formatting failed");if(stream_mode){if(fclose(output))fail("sample formatting failed");enqueue(row,seq);}previous=begin;seq++;
  next+=(uint64_t)period*1000000u;uint64_t now=ns(CLOCK_MONOTONIC);if(next<now)next=now;
  struct timespec ts={(time_t)(next/1000000000u),(long)(next%1000000000u)};
  if(seq<(uint64_t)count)while(!stopped&&clock_nanosleep(CLOCK_MONOTONIC,TIMER_ABSTIME,&ts,NULL)==EINTR){}
 }
 close(fd);if(stream_mode){pthread_mutex_lock(&queue_lock);producer_done=1;pthread_cond_signal(&queue_ready);pthread_mutex_unlock(&queue_lock);pthread_join(sender,NULL);}if(stopped){fprintf(stderr,"RP_CAPTURE_STOPPED after %" PRIu64 " samples\n",seq);return 3;}return 0;
}
