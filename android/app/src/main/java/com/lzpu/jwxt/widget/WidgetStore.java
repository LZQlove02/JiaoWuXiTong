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

    // 组件背景样式
    public static final String STYLE_GLASS = "glass";
    public static final String STYLE_TRANSPARENT = "transparent";
    public static final String STYLE_OPAQUE = "opaque";

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

    // 样式按组件实例保存，同一个尺寸可以放多个不同样式
    private static String styleKey(int appWidgetId) {
        return "style_" + appWidgetId;
    }

    public static void writeStyle(Context context, int appWidgetId, String style) {
        prefs(context).edit().putString(styleKey(appWidgetId), normalizeStyle(style)).apply();
    }

    public static String readStyle(Context context, int appWidgetId) {
        return normalizeStyle(prefs(context).getString(styleKey(appWidgetId), STYLE_GLASS));
    }

    public static void clearStyles(Context context, int[] appWidgetIds) {
        if (appWidgetIds == null || appWidgetIds.length == 0) return;
        SharedPreferences.Editor editor = prefs(context).edit();
        for (int id : appWidgetIds) {
            editor.remove(styleKey(id));
        }
        editor.apply();
    }

    // 点一下「样式」按钮的循环顺序：玻璃 → 透明 → 不透明 → 玻璃
    public static String nextStyle(String style) {
        if (STYLE_GLASS.equals(style)) return STYLE_TRANSPARENT;
        if (STYLE_TRANSPARENT.equals(style)) return STYLE_OPAQUE;
        return STYLE_GLASS;
    }

    public static String styleLabel(Context context, String style) {
        if (STYLE_TRANSPARENT.equals(style)) {
            return context.getString(com.lzpu.jwxt.R.string.widget_style_transparent);
        }
        if (STYLE_OPAQUE.equals(style)) {
            return context.getString(com.lzpu.jwxt.R.string.widget_style_opaque);
        }
        return context.getString(com.lzpu.jwxt.R.string.widget_style_glass);
    }

    private static String normalizeStyle(String value) {
        if (STYLE_TRANSPARENT.equals(value) || STYLE_OPAQUE.equals(value)) return value;
        return STYLE_GLASS;
    }
}