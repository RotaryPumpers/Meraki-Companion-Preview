package com.rotarypumpers.meraki;
import android.app.*;import android.content.*;import android.net.Uri;import android.view.*;import android.widget.*;
import com.rotarypumpers.meraki.core.*;import java.io.*;import java.nio.*;import java.nio.charset.*;import java.util.concurrent.*;import java.util.function.BooleanSupplier;
/** Pairing is changed only after explicit native confirmation; no code opens a URL. */
final class PairingQrController {
 static final int SCAN=410,FILE=411;
 private final MainActivity a;private final BooleanSupplier allowed;private final Runnable saved;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();private boolean closed,reading;private AlertDialog confirmation;
 PairingQrController(MainActivity a,BooleanSupplier allowed,Runnable saved){this.a=a;this.allowed=allowed;this.saved=saved;}
 private boolean ready(){if(closed)return false;if(!allowed.getAsBoolean()){message("Finish the current task","Disconnect recording and finish any settings update before pairing.");return false;}return true;}
 void scan(){if(!ready())return;a.startActivityForResult(new Intent(a,PairingScanActivity.class),SCAN);}
 void importFile(){if(!ready()||reading)return;a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),FILE);}
 boolean result(int request,int result,Intent data){
  if(request!=SCAN&&request!=FILE)return false;if(result!=Activity.RESULT_OK||data==null||closed)return true;
  if(request==SCAN){String raw=data.getStringExtra(PairingScanActivity.RESULT);data.removeExtra(PairingScanActivity.RESULT);if(ready())review(raw);}
  else{Uri uri=data.getData();if(uri==null||!"content".equals(uri.getScheme())||!ready()||reading)return true;reading=true;worker.execute(()->{String raw=null;try(InputStream in=a.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException();ByteArrayOutputStream bytesOut=new ByteArrayOutputStream();byte[] block=new byte[256];int n;while(bytesOut.size()<2049&&(n=in.read(block,0,Math.min(block.length,2049-bytesOut.size())))!=-1)bytesOut.write(block,0,n);byte[] bytes=bytesOut.toByteArray();if(bytes.length>2048)throw new IOException();raw=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(Exception ignored){}final String value=raw;a.runOnUiThread(()->{reading=false;if(!closed&&ready())review(value);});});}
  return true;
 }
 void review(String raw){
  if(!ready())return;final PairingQrPayload candidate;try{candidate=PairingQrPayload.parse(raw);}catch(Exception e){message("Not a Companion pairing code","Use the code generated from your private phone-pairing.json file. Website links, device-private.json and other QR codes are not accepted. Existing pairing is unchanged.");return;}
  if(confirmation!=null&&confirmation.isShowing())return;
  SecurePairing prior=null;try{SecureLinkCredentials c=new SecureLinkCredentials(a);if(c.configured())prior=c.load();}catch(Exception ignored){}
  String fallback=prior==null?a.getPreferences(0).getString("host",""):prior.host;
  LinearLayout form=new LinearLayout(a);form.setOrientation(1);int pad=dp(20);form.setPadding(pad,dp(4),pad,dp(4));
  TextView description=label("Confirm the Meraki address shown below. Pairing does not start brewing or grinding.");form.addView(description);
  EditText host=new EditText(a);host.setSingleLine(true);host.setHint("Meraki IP address");host.setContentDescription("Meraki IP address");host.setInputType(android.text.InputType.TYPE_CLASS_PHONE);host.setText(candidate.host==null?fallback:candidate.host);form.addView(host);
  if(candidate.host==null)form.addView(label("This older code does not contain an address. Enter the machine’s current home Wi-Fi address."));
  if(prior!=null)form.addView(label("Saving replaces this phone’s saved machine pairing. Your shots and recipes stay intact."));
  TextView error=label("");form.addView(error);ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);scroll.addView(form);
  confirmation=LightDialogs.builder(a).setTitle("Confirm machine pairing").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Pair and connect",null).create();
  confirmation.setOnShowListener(d->{confirmation.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);confirmation.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   if(!ready())return;try{SecurePairing pairing=candidate.withConfirmedAddress(host.getText().toString().trim());savePairing(pairing);}catch(Exception e){error.setText("Could not save pairing. Check the private machine address and try again.");return;}confirmation.dismiss();saved.run();
  });});confirmation.setOnDismissListener(d->confirmation=null);confirmation.show();
 }
 private void savePairing(SecurePairing pairing)throws Exception{
  android.content.SharedPreferences secure=a.getSharedPreferences("secure_machine",0),prefs=a.getPreferences(0);
  String oldIv=secure.getString("iv",null),oldData=secure.getString("data",null),oldHost=prefs.getString("host",null);
  try{new SecureLinkCredentials(a).save(pairing);if(!prefs.edit().putString("host",pairing.host).commit())throw new IOException();}
  catch(Exception e){android.content.SharedPreferences.Editor se=secure.edit();if(oldIv==null)se.remove("iv");else se.putString("iv",oldIv);if(oldData==null)se.remove("data");else se.putString("data",oldData);se.commit();android.content.SharedPreferences.Editor pe=prefs.edit();if(oldHost==null)pe.remove("host");else pe.putString("host",oldHost);pe.commit();throw e;}
 }
 private int dp(int n){return (int)(n*a.getResources().getDisplayMetrics().density);}
 private TextView label(String s){TextView v=new TextView(a);v.setText(s);v.setTextSize(16);v.setPadding(0,dp(6),0,dp(6));v.setTextColor(0xff293323);return v;}
 private void message(String title,String text){if(!closed&&!a.isFinishing()&&!a.isDestroyed())LightDialogs.builder(a).setTitle(title).setMessage(text).setPositiveButton("Close",null).show();}
 void destroy(){closed=true;worker.shutdownNow();if(confirmation!=null)confirmation.dismiss();}
}
