package com.rotarypumpers.meraki;
import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
/** Token encrypted with a non-exportable Android Keystore key; excluded from journal backups. */
final class ForumCredentials {
    private static final String ALIAS="rp_journal_connection_v1";
    private final android.content.SharedPreferences prefs;
    ForumCredentials(Context c){prefs=c.getSharedPreferences("forum_connection",Context.MODE_PRIVATE);}
    private javax.crypto.SecretKey key()throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();}
        return (javax.crypto.SecretKey)store.getKey(ALIAS,null);
    }
    void save(String base,String token,int uid,String username)throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        String enc=Base64.getEncoder().encodeToString(c.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        if(!prefs.edit().putString("base",base).putInt("uid",uid).putString("username",username).putString("iv",Base64.getEncoder().encodeToString(c.getIV())).putString("token",enc).commit())throw new java.io.IOException("Could not save connection");
    }
    String token()throws Exception {Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.getDecoder().decode(prefs.getString("iv",""))));return new String(c.doFinal(Base64.getDecoder().decode(prefs.getString("token",""))),StandardCharsets.UTF_8);}
    boolean configured(){return prefs.contains("token");}String base(){return prefs.getString("base","https://www.rotarypumpers.com");}int uid(){return prefs.getInt("uid",0);}String username(){return prefs.getString("username","");}
    void clear(){prefs.edit().clear().commit();}
}
