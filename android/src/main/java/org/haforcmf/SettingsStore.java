package org.haforcmf;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AndroidKeyStore AES-GCM encryption for persistent HA credentials and preferences. */
final class SettingsStore {
    private static final String STORE="ha_for_cmf_config_v05";
    private static final String KEY_ALIAS="ha_for_cmf_aes_v05";
    private static SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SecretKey existing=(SecretKey)store.getKey(KEY_ALIAS,null);
        if(existing!=null)return existing;
        KeyGenerator generator=KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }
    static void save(Context context,String field,String value) {
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key());
            byte[] iv=cipher.getIV();
            byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result=Arrays.copyOf(iv,iv.length+encrypted.length);
            System.arraycopy(encrypted,0,result,iv.length,encrypted.length);
            context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit()
                    .putString(field,Base64.encodeToString(result,Base64.NO_WRAP)).apply();
        } catch(Exception ex) {
            android.util.Log.e("HA-for-CMF","Settings encryption failed",ex);
        }
    }
    static String load(Context context,String field) {
        String stored=context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
                .getString(field,null);
        if(stored==null)return "";
        try {
            byte[] data=Base64.decode(stored,Base64.NO_WRAP);
            if(data.length<29)return "";
            byte[] iv=Arrays.copyOfRange(data,0,12);
            byte[] encrypted=Arrays.copyOfRange(data,12,data.length);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
        }catch(Exception ex) {
            return "";
        }
    }
}
