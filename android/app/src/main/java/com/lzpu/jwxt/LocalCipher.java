package com.lzpu.jwxt;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 本地个人数据的加密存储工具：使用 Android Keystore 的 AES-GCM 密钥，
 * 用于桌面组件 payload、课表变更基线与成绩基线，避免课程名、学号等明文落盘。
 * 读取旧版本明文时按原样返回，下一次写入自动升级为密文。
 */
public final class LocalCipher {

    private static final String PREFIX = "enc1:";
    private static final String ALIAS = "jwxt_local_data_key";
    private static final int IV_LENGTH = 12;

    private LocalCipher() {
    }

    public static String seal(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception error) {
            // Keystore 不可用时退回明文，保证功能不中断；调用方仍受应用沙箱保护
            return plain;
        }
    }

    public static String open(String stored) {
        if (stored == null || stored.isEmpty()) return stored;
        if (!stored.startsWith(PREFIX)) return stored;
        try {
            byte[] combined = Base64.decode(stored.substring(PREFIX.length()), Base64.NO_WRAP);
            if (combined.length <= IV_LENGTH) return "";
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception error) {
            // 密钥失效或数据损坏时按空值处理，由下次同步重建
            return "";
        }
    }

    private static SecretKey getKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(ALIAS)) {
            return (SecretKey) keyStore.getKey(ALIAS, null);
        }
        synchronized (LocalCipher.class) {
            keyStore.load(null);
            if (keyStore.containsAlias(ALIAS)) {
                return (SecretKey) keyStore.getKey(ALIAS, null);
            }
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            return generator.generateKey();
        }
    }
}
