package com.rotarypumpers.meraki;
import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.rotarypumpers.meraki.core.SecurePairing;
final class SecureLinkCredentials {
 private static final String ALIAS="rp_secure_machine_v1";
 private final android.content.SharedPreferences prefs;
 SecureLinkCredentials(Context context){prefs=context.getSharedPreferences("secure_machine",Context.MODE_PRIVATE);}
 private SecretKey key()throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(!store.containsAlias(ALIAS)){KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();}
  return (SecretKey)store.getKey(ALIAS,null);
 }
 void save(SecurePairing pairing)throws Exception{
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
  String encrypted=Base64.getEncoder().encodeToString(c.doFinal(pairing.encode().getBytes(StandardCharsets.UTF_8)));
  if(!prefs.edit().putString("iv",Base64.getEncoder().encodeToString(c.getIV())).putString("data",encrypted).commit())throw new java.io.IOException("Could not save pairing");
 }
 SecurePairing load()throws Exception{
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.getDecoder().decode(prefs.getString("iv",""))));
  return SecurePairing.decode(new String(c.doFinal(Base64.getDecoder().decode(prefs.getString("data",""))),StandardCharsets.UTF_8));
 }
 boolean configured(){return prefs.contains("data");}
 void clear()throws Exception{if(!prefs.edit().clear().commit())throw new java.io.IOException("Could not forget pairing");}
}
