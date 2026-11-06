package com.lzqlove.jiaowu;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 使用 Android Keystore 中的 AES-GCM 密钥加密保存登录 Cookie。
 * 不保存密码，不保存明文 Cookie。
 *
 * 进程内缓存一份解密后的值，前台请求插件与后台保活共用，
 * 避免后台更新 Cookie 后前台仍在使用旧的会话标识。
 */
public final class AuthCookieStore {

    private static final String PREFS = "auth_session";
    private static final String KEY = "cookie";
    private static final String ALIAS = "jwxt_auth_cookie_key";
    private static final int IV_LENGTH = 12;
    private static final String TAG = "AuthCookieStore";

    private static String cached;
    private static boolean loaded;

    private AuthCookieStore() {
    }

    static synchronized void save(Context context, String value) {
        if (value == null) return;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            prefs(context).edit()
                    .putString(KEY, Base64.encodeToString(combined, Base64.NO_WRAP))
                    .apply();
            cached = value;
            loaded = true;
            Log.i(TAG, "save ok, length=" + value.length());
        } catch (Exception error) {
            Log.e(TAG, "save failed: " + error.getClass().getSimpleName()
                    + " " + error.getMessage());
        }
    }

    public static synchronized String load(Context context) {
        if (loaded) return cached;
        try {
            String stored = prefs(context).getString(KEY, null);
            if (stored == null) {
                Log.i(TAG, "load: no stored cookie");
                cached = null;
                loaded = true;
                return null;
            }
            byte[] combined = Base64.decode(stored, Base64.NO_WRAP);
            if (combined.length <= IV_LENGTH) {
                Log.w(TAG, "load: stored cookie too short (" + combined.length + " bytes)");
                cached = null;
                loaded = true;
                return null;
            }
            // 小米等 ROM 在闹钟唤醒进程时 Keystore 服务可能尚未就绪，
            // 首次解密偶发失败时短暂等待后重试一次，避免把临时故障当成无会话。
            String decrypted = decryptWithRetry(combined);
            if (decrypted == null) {
                // 仍失败：不写入缓存，下一次调用重试；调用方需用 hasStoredCookie 判断是否真无会话
                return null;
            }
            cached = decrypted;
            loaded = true;
            Log.i(TAG, "load ok, length=" + decrypted.length());
            return cached;
        } catch (Exception error) {
            Log.e(TAG, "load failed: " + error.getClass().getSimpleName()
                    + " " + error.getMessage());
            return null;
        }
    }

    private static String decryptWithRetry(byte[] combined) {
        for (int attempt = 0; attempt < 2; attempt += 1) {
            try {
                byte[] iv = new byte[IV_LENGTH];
                System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
                byte[] encrypted = new byte[combined.length - IV_LENGTH];
                System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
                return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            } catch (Exception error) {
                Log.w(TAG, "decrypt attempt " + (attempt + 1) + " failed: "
                        + error.getClass().getSimpleName() + " " + error.getMessage());
                if (attempt == 0) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }
        Log.e(TAG, "decrypt failed after 2 attempts");
        return null;
    }

    /**
     * 不解密，只判断是否存过 Cookie。用于区分「本机确实没有会话」和
     * 「Keystore 临时不可用导致解密失败」两种 null，避免后者误取消后台保活闹钟。
     */
    public static synchronized boolean hasStoredCookie(Context context) {
        return prefs(context).contains(KEY);
    }

    static synchronized void clear(Context context) {
        prefs(context).edit().remove(KEY).apply();
        cached = null;
        loaded = true;
    }

    /**
     * 把响应的 Set-Cookie 合并进本地会话：只保留 name=value，值为空视为删除。
     * 前后台共用同一份缓存，服务端轮换会话标识后不会继续使用旧值。
     */
    public static synchronized void mergeSetCookies(Context context, List<String> setCookies) {
        if (setCookies == null || setCookies.isEmpty()) return;
        Map<String, String> jar = new LinkedHashMap<>();
        putCookiePairs(jar, load(context));
        boolean changed = false;
        for (String header : setCookies) {
            String pair = header.split(";", 2)[0].trim();
            int index = pair.indexOf('=');
            if (index <= 0) continue;
            String name = pair.substring(0, index).trim();
            String value = pair.substring(index + 1).trim();
            if (name.isEmpty()) continue;
            if (value.isEmpty()) {
                if (jar.remove(name) != null) changed = true;
            } else if (!value.equals(jar.put(name, value))) {
                changed = true;
            }
        }
        if (!changed) return;
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : jar.entrySet()) {
            if (builder.length() > 0) builder.append("; ");
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        save(context, builder.toString());
    }

    private static void putCookiePairs(Map<String, String> target, String header) {
        if (header == null || header.isEmpty()) return;
        for (String part : header.split(";")) {
            String pair = part.trim();
            int index = pair.indexOf('=');
            if (index <= 0) continue;
            target.put(pair.substring(0, index).trim(), pair.substring(index + 1).trim());
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SecretKey getKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(ALIAS)) {
            SecretKey key = (SecretKey) keyStore.getKey(ALIAS, null);
            if (key == null) Log.w(TAG, "getKey: alias exists but key is null");
            return key;
        }
        Log.i(TAG, "getKey: generating new key");
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        SecretKey key = generator.generateKey();
        Log.i(TAG, "getKey: new key generated");
        return key;
    }
}
