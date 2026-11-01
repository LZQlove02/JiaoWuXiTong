package com.lzqlove.jiaowu.widget;

import android.content.Context;
import android.content.SharedPreferences;

import com.lzqlove.jiaowu.LocalCipher;

import org.json.JSONObject;

/**
 * 组件数据存储：App 私有 SharedPreferences，不导出、不参与备份。
 * payload 使用 Keystore AES-GCM 加密，避免课程名等明文落盘。
 * JS 侧通过 WidgetBridge 写入，组件进程只读。
 */
public final class WidgetStore {

    private static final String PREFS_NAME = "jiaowu_widget";
    private static final String KEY_PAYLOAD = "payload";
    private static final String KEY_THEME = "theme";
    // JS 同步与原生变更引擎都会改写 payload，读改写必须串行
    private static final Object PAYLOAD_LOCK = new Object();

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
        synchronized (PAYLOAD_LOCK) {
            prefs(context).edit()
                    .putString(KEY_PAYLOAD, LocalCipher.seal(json == null ? "" : json))
                    .apply();
        }
    }

    public static String readPayload(Context context) {
        synchronized (PAYLOAD_LOCK) {
            return LocalCipher.open(prefs(context).getString(KEY_PAYLOAD, ""));
        }
    }

    public static void clearPayload(Context context) {
        synchronized (PAYLOAD_LOCK) {
            prefs(context).edit().remove(KEY_PAYLOAD).apply();
        }
    }

    /**
     * JS 同步专用：状态未加载完时 payload 不带 changeBadge，此时保留原生刚写入的角标。
     * 读旧值 + 合并 + 写回在同一把锁内，避免与后台检查的 markChangeBadge 互相覆盖。
     */
    public static void writePayloadPreservingBadge(Context context, String json) {
        synchronized (PAYLOAD_LOCK) {
            String merged = json;
            try {
                JSONObject incoming = new JSONObject(json);
                if (!incoming.has("changeBadge")) {
                    String old = readPayload(context);
                    if (old != null && !old.isEmpty()) {
                        incoming.put("changeBadge",
                                new JSONObject(old).optBoolean("changeBadge", false));
                    }
                    merged = incoming.toString();
                }
            } catch (Exception ignored) {
                // payload 不是合法 JSON 时按原样写入，由组件兜底
            }
            writePayload(context, merged);
        }
    }

    /**
     * 只改 payload 里的变更角标，不引入第二份组件数据源。
     * 后台检查发现变更、标记已读或关闭开关时由变更引擎调用。
     */
    public static boolean markChangeBadge(Context context, boolean visible) {
        synchronized (PAYLOAD_LOCK) {
            String raw = readPayload(context);
            if (raw == null || raw.isEmpty()) return false;
            try {
                JSONObject json = new JSONObject(raw);
                if (json.optBoolean("changeBadge", false) == visible) return false;
                json.put("changeBadge", visible);
                writePayload(context, json.toString());
                return true;
            } catch (Exception ignored) {
                // payload 不是合法 JSON 时保持原样，等 JS 下次同步覆盖
                return false;
            }
        }
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


    private static String normalizeStyle(String value) {
        if (STYLE_TRANSPARENT.equals(value) || STYLE_OPAQUE.equals(value)) return value;
        return STYLE_GLASS;
    }
}