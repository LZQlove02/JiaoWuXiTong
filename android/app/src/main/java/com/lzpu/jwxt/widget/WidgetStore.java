package com.lzpu.jwxt.widget;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 组件数据存储：App 私有 SharedPreferences，不导出、不参与备份。
 * JS 侧通过 WidgetBridge 写入，组件进程只读。
 */
public final class WidgetStore {

    private static final String PREFS_NAME = "lzpu_widget";
    private static final String KEY_PAYLOAD = "payload";
    private static final String KEY_THEME = "theme";

    private WidgetStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void writePayload(Context context, String json) {
        prefs(context).edit().putString(KEY_PAYLOAD, json == null ? "" : json).apply();
    }

    public static String readPayload(Context context) {
        return prefs(context).getString(KEY_PAYLOAD, "");
    }

    public static void clearPayload(Context context) {
        prefs(context).edit().remove(KEY_PAYLOAD).apply();
    }

    public static void writeTheme(Context context, String theme) {
        prefs(context).edit().putString(KEY_THEME, normalizeTheme(theme)).apply();
    }

    public static String readTheme(Context context) {
        return normalizeTheme(prefs(context).getString(KEY_THEME, WidgetTheme.SYSTEM));
    }

    private static String normalizeTheme(String value) {
        if (WidgetTheme.LIGHT.equals(value) || WidgetTheme.DARK.equals(value)) return value;
        return WidgetTheme.SYSTEM;
    }
}