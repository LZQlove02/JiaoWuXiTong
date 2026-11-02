package com.lzqlove.jiaowu.keepalive;

import android.content.Context;
import android.content.SharedPreferences;

/** 「保持登录」开关：默认关闭，仅记录用户是否开启后台会话保活。 */
public final class SessionKeepAliveStore {

    private static final String PREFS = "jiaowu_keepalive";
    private static final String KEY_ENABLED = "enabled";

    private SessionKeepAliveStore() {
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
