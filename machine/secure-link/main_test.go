package main

import (
	"bufio"
	"context"
	"crypto/sha256"
	"crypto/subtle"
	"crypto/tls"
	"encoding/hex"
	"fmt"
	"io"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func setup(t *testing.T, run runner) (string, credentials, pairing, <-chan error) {
	t.Helper()
	c, p, e := createCredentials()
	if e != nil {
		t.Fatal(e)
	}
	conf, e := newTLS(c)
	if e != nil {
		t.Fatal(e)
	}
	ln, e := net.Listen("tcp4", "127.0.0.1:0")
	if e != nil {
		t.Fatal(e)
	}
	done := make(chan error, 1)
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(func() { cancel(); ln.Close() })
	go func() {
		conn, e := ln.Accept()
		if e == nil {
			e = handle(ctx, conn, conf, c.Token, run)
		}
		done <- e
	}()
	return ln.Addr().String(), c, p, done
}
func dial(addr, pin string) (*tls.Conn, error) {
	return tls.Dial("tcp4", addr, &tls.Config{MinVersion: tls.VersionTLS12, InsecureSkipVerify: true, VerifyConnection: func(s tls.ConnectionState) error {
		h := sha256.Sum256(s.PeerCertificates[0].Raw)
		if subtle.ConstantTimeCompare([]byte(hex.EncodeToString(h[:])), []byte(pin)) != 1 {
			return fmt.Errorf("pin rejected")
		}
		return nil
	}})
}
func finish(t *testing.T, ch <-chan error) {
	t.Helper()
	select {
	case <-ch:
	case <-time.After(4 * time.Second):
		t.Fatal("session failed to terminate")
	}
}
func TestPairedStreamAndAck(t *testing.T) {
	addr, c, p, done := setup(t, func(ctx context.Context, r io.Reader, w io.Writer) error {
		fmt.Fprintln(w, `{"type":"sample","seq":0}`)
		s, e := bufio.NewReader(r).ReadString('\n')
		if e != nil {
			return e
		}
		if s != "ACK 0\n" {
			return fmt.Errorf("wrong ack")
		}
		_, e = fmt.Fprintln(w, "ACK_SAVED")
		return e
	})
	conn, e := dial(addr, p.CertificateSHA256)
	if e != nil {
		t.Fatal(e)
	}
	defer conn.Close()
	fmt.Fprintln(conn, "RP/1 "+c.Token)
	r := bufio.NewReader(conn)
	if s, e := r.ReadString('\n'); e != nil || !strings.Contains(s, "sample") {
		t.Fatal(s, e)
	}
	fmt.Fprintln(conn, "ACK 0")
	if s, e := r.ReadString('\n'); e != nil || s != "ACK_SAVED\n" {
		t.Fatal(s, e)
	}
	finish(t, done)
}
func TestBadTokenNeverStartsRecorder(t *testing.T) {
	var calls atomic.Int32
	addr, _, p, done := setup(t, func(context.Context, io.Reader, io.Writer) error { calls.Add(1); return nil })
	conn, e := dial(addr, p.CertificateSHA256)
	if e != nil {
		t.Fatal(e)
	}
	fmt.Fprintln(conn, "RP/1 "+strings.Repeat("0", 64))
	finish(t, done)
	conn.Close()
	if calls.Load() != 0 {
		t.Fatal("unauthorized execution")
	}
}
func TestWrongPinRejected(t *testing.T) {
	addr, _, _, done := setup(t, func(context.Context, io.Reader, io.Writer) error { t.Error("recorder started"); return nil })
	conn, e := dial(addr, strings.Repeat("0", 64))
	if conn != nil {
		conn.Close()
	}
	if e == nil {
		t.Fatal("wrong pin accepted")
	}
	finish(t, done)
}
func TestPlaintextRejected(t *testing.T) {
	addr, _, _, done := setup(t, func(context.Context, io.Reader, io.Writer) error { t.Error("recorder started"); return nil })
	c, e := net.Dial("tcp4", addr)
	if e != nil {
		t.Fatal(e)
	}
	fmt.Fprint(c, "GET / HTTP/1.1\r\n\r\n")
	c.Close()
	finish(t, done)
}
func TestMalformedAckCancels(t *testing.T) {
	for _, line := range []string{"rm -rf /", "ACK -1", "ACK 18446744073709551616", strings.Repeat("A", 100)} {
		t.Run(line[:min(8, len(line))], func(t *testing.T) {
			addr, c, p, done := setup(t, func(ctx context.Context, r io.Reader, w io.Writer) error {
				fmt.Fprintln(w, "READY")
				<-ctx.Done()
				return ctx.Err()
			})
			conn, e := dial(addr, p.CertificateSHA256)
			if e != nil {
				t.Fatal(e)
			}
			fmt.Fprintln(conn, "RP/1 "+c.Token)
			bufio.NewReader(conn).ReadString('\n')
			fmt.Fprintln(conn, line)
			finish(t, done)
			conn.Close()
		})
	}
}
func TestDisconnectCancels(t *testing.T) {
	addr, c, p, done := setup(t, func(ctx context.Context, r io.Reader, w io.Writer) error {
		fmt.Fprintln(w, "READY")
		<-ctx.Done()
		return ctx.Err()
	})
	conn, e := dial(addr, p.CertificateSHA256)
	if e != nil {
		t.Fatal(e)
	}
	fmt.Fprintln(conn, "RP/1 "+c.Token)
	bufio.NewReader(conn).ReadString('\n')
	conn.Close()
	finish(t, done)
}
func TestCredentialsNotOverwritten(t *testing.T) {
	d := filepath.Join(t.TempDir(), "pair")
	if e := initialize(d); e != nil {
		t.Fatal(e)
	}
	before, _ := os.ReadFile(filepath.Join(d, "device-private.json"))
	if e := initialize(d); e == nil {
		t.Fatal("overwrote existing credentials")
	}
	after, _ := os.ReadFile(filepath.Join(d, "device-private.json"))
	if string(before) != string(after) {
		t.Fatal("credentials changed")
	}
	info, _ := os.Stat(filepath.Join(d, "device-private.json"))
	if info.Mode().Perm() != 0600 {
		t.Fatal("wrong permissions")
	}
}
func TestRecorderHashAndAddressGuard(t *testing.T) {
	p := filepath.Join(t.TempDir(), "recorder")
	os.WriteFile(p, []byte("bad"), 0700)
	if verifyRecorder(p) == nil {
		t.Fatal("corrupt recorder accepted")
	}
	for _, ip := range []string{"0.0.0.0", "8.8.8.8", "::1"} {
		if allowedIP(net.ParseIP(ip)) {
			t.Fatal(ip)
		}
	}
	if !allowedIP(net.ParseIP("192.168.1.20")) {
		t.Fatal("private IP rejected")
	}
}
func TestJavaClient(t *testing.T) {
	classes := os.Getenv("RP_JAVA_CLASSES")
	if classes == "" {
		t.Skip("RP_JAVA_CLASSES not provided")
	}
	addr, c, p, done := setup(t, func(ctx context.Context, r io.Reader, w io.Writer) error {
		fmt.Fprintln(w, `{"type":"sample","seq":0}`)
		s, e := bufio.NewReader(r).ReadString('\n')
		if e != nil || s != "ACK 0\n" {
			return fmt.Errorf("bad Java ACK: %s %v", s, e)
		}
		_, e = fmt.Fprintln(w, "ACK_SAVED")
		return e
	})
	_, port, _ := net.SplitHostPort(addr)
	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()
	out, e := exec.CommandContext(ctx, "java", "-cp", classes, "TransportCheck", port, p.CertificateSHA256, c.Token).CombinedOutput()
	if e != nil {
		t.Fatalf("%s %v", out, e)
	}
	t.Log(string(out))
	finish(t, done)
}

func TestJavaRejectsWrongPairing(t *testing.T) {
	classes := os.Getenv("RP_JAVA_CLASSES")
	if classes == "" {
		t.Skip("RP_JAVA_CLASSES not provided")
	}
	for _, mode := range []string{"pin", "token"} {
		t.Run(mode, func(t *testing.T) {
			var calls atomic.Int32
			addr, c, p, done := setup(t, func(context.Context, io.Reader, io.Writer) error { calls.Add(1); return nil })
			pin, token := p.CertificateSHA256, c.Token
			if mode == "pin" {
				pin = strings.Repeat("0", 64)
			} else {
				token = strings.Repeat("0", 64)
			}
			_, port, _ := net.SplitHostPort(addr)
			ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
			defer cancel()
			if e := exec.CommandContext(ctx, "java", "-cp", classes, "TransportCheck", port, pin, token).Run(); e == nil {
				t.Fatal("incorrect pairing accepted")
			}
			finish(t, done)
			if calls.Load() != 0 {
				t.Fatal("unauthorized recorder launch")
			}
		})
	}
}

func TestDisconnectKillsChildProcess(t *testing.T) {
	exited := make(chan struct{})
	addr, c, p, done := setup(t, func(ctx context.Context, r io.Reader, w io.Writer) error {
		cmd := exec.CommandContext(ctx, "/bin/sleep", "30")
		cmd.WaitDelay = 2 * time.Second
		if e := cmd.Start(); e != nil {
			return e
		}
		fmt.Fprintln(w, "READY")
		e := cmd.Wait()
		close(exited)
		return e
	})
	conn, e := dial(addr, p.CertificateSHA256)
	if e != nil {
		t.Fatal(e)
	}
	fmt.Fprintln(conn, "RP/1 "+c.Token)
	if _, e = bufio.NewReader(conn).ReadString('\n'); e != nil {
		t.Fatal(e)
	}
	conn.Close()
	finish(t, done)
	select {
	case <-exited:
	default:
		t.Fatal("child still alive")
	}
}

func TestDisplayExecutableDiscovery(t *testing.T) {
	root := t.TempDir()
	add := func(pid, comm, exe string) {
		t.Helper()
		dir := filepath.Join(root, pid)
		if err := os.Mkdir(dir, 0700); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(dir, "comm"), []byte(comm+"\n"), 0600); err != nil {
			t.Fatal(err)
		}
		if exe != "" {
			if err := os.Symlink(exe, filepath.Join(dir, "exe")); err != nil {
				t.Fatal(err)
			}
		}
	}
	add("769", "audio_play", "/new_customer/demo_v9")
	add("770", "demo_v9", "/tmp/other")
	add("771", "demo_v9", "")
	add("772", "demo_v9", "/new_customer/demo_v9 (deleted)")
	pid, err := displayPIDAt(root)
	if err != nil || pid != "769" {
		t.Fatalf("got %q %v", pid, err)
	}
	add("773", "renamed_thread", "/customer/demo_v9")
	if _, err = displayPIDAt(root); err == nil {
		t.Fatal("multiple display processes accepted")
	}
	if err = os.Remove(filepath.Join(root, "769", "exe")); err != nil {
		t.Fatal(err)
	}
	pid, err = displayPIDAt(root)
	if err != nil || pid != "773" {
		t.Fatalf("customer path: %q %v", pid, err)
	}
	if err = os.Remove(filepath.Join(root, "773", "exe")); err != nil {
		t.Fatal(err)
	}
	if _, err = displayPIDAt(root); err == nil {
		t.Fatal("missing display accepted")
	}
}

func TestExplicitPIDSelection(t *testing.T) {
	root := t.TempDir()
	for _, pid := range []string{"769", "770"} {
		if err := os.Mkdir(filepath.Join(root, pid), 0700); err != nil {
			t.Fatal(err)
		}
		if err := os.Symlink("/new_customer/demo_v9", filepath.Join(root, pid, "exe")); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := displayPIDAt(root); err == nil || !strings.Contains(err.Error(), "769 and 770") {
		t.Fatalf("ambiguity diagnostics: %v", err)
	}
	if pid, err := verifiedPIDAt(root, "769"); err != nil || pid != "769" {
		t.Fatalf("selection: %s %v", pid, err)
	}
	for _, bad := range []string{"0", "-1", "../769", "0769", "2147483648", "777", "769;echo x"} {
		if _, err := verifiedPIDAt(root, bad); err == nil {
			t.Fatalf("accepted invalid PID %q", bad)
		}
	}
	if err := os.Remove(filepath.Join(root, "769", "exe")); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink("/bin/sh", filepath.Join(root, "769", "exe")); err != nil {
		t.Fatal(err)
	}
	if _, err := verifiedPIDAt(root, "769"); err == nil {
		t.Fatal("accepted wrong executable")
	}
}
