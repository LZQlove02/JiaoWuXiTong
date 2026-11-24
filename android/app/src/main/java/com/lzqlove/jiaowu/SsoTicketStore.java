package com.lzqlove.jiaowu;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 统一认证票据的加密副本，仅供后台静默续期使用。
 * 不保存密码；WebView Cookie 是运行时的权威来源，这里只是一份 Keystore 加密的副本，
 * 让闹钟唤醒的后台进程不必初始化 WebView 也能换票。
 * 换票成功时写回 CAS 轮换后的新票据；判定票据失效时清空。
 */
final class SsoTicketStore {

    private static final String PREFS = "jiaowu_sso";
    private static final String KEY_TICKET = "castgc_cipher";

    private SsoTicketStore() {
    }

    static void save(Context context, String ticket) {
        if (ticket == null || ticket.isEmpty()) return;
        String sealed = LocalCipher.seal(ticket);
        if (sealed == null) return;
        prefs(context).edit().putString(KEY_TICKET, sealed).apply();
    }

    static String load(Context context) {
        String sealed = prefs(context).getString(KEY_TICKET, "");
        if (sealed == null || sealed.isEmpty()) return "";
        String opened = LocalCipher.open(sealed);
        return opened == null ? "" : opened;
    }

    static void clear(Context context) {
        prefs(context).edit().remove(KEY_TICKET).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
