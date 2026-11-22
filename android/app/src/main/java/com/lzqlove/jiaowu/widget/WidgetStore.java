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
            String sealed = LocalCipher.seal(json == null ? "" : json);
            if (sealed == null) {
                // 加密失败时保留上一份 payload，不写明文
                return;
            }
            prefs(context).edit()
                    .putString(KEY_PAYLOAD, sealed)
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

    // 组件被移除时清掉这个实例的全部设置：样式 + 文字色 + 周次页
    public static void clearInstanceSettings(Context context, int[] appWidgetIds) {
        if (appWidgetIds == null || appWidgetIds.length == 0) return;
        SharedPreferences.Editor editor = prefs(context).edit();
        for (int id : appWidgetIds) {
            editor.remove(styleKey(id));
            editor.remove(textColorKey(id));
            editor.remove(weekOffsetKey(id));
        }
        editor.apply();
    }

    // 4x4 当前看哪一周：0 = 本周，1 = 下周，按实例保存（两个 4x4 可以各看各的）
    private static String weekOffsetKey(int appWidgetId) {
        return "weekOffset_" + appWidgetId;
    }

    public static void writeWeekOffset(Context context, int appWidgetId, int offset) {
        prefs(context).edit().putInt(weekOffsetKey(appWidgetId), offset == 1 ? 1 : 0).apply();
    }

    public static int readWeekOffset(Context context, int appWidgetId) {
        return prefs(context).getInt(weekOffsetKey(appWidgetId), 0) == 1 ? 1 : 0;
    }

    // 文字颜色按组件实例保存，空串表示跟随主题（自动）
    private static String textColorKey(int appWidgetId) {
        return "textColor_" + appWidgetId;
    }

    public static void writeTextColor(Context context, int appWidgetId, String color) {
        prefs(context).edit().putString(textColorKey(appWidgetId), normalizeTextColor(color)).apply();
    }

    public static String readTextColor(Context context, int appWidgetId) {
        return normalizeTextColor(prefs(context).getString(textColorKey(appWidgetId), ""));
    }

    // 校验统一走 WidgetTheme.parseTextColor，这里只负责落盘成规范写法；
    // 非法值一律视为「自动」，避免把解析不了的值写进组件
    private static String normalizeTextColor(String value) {
        if (WidgetTheme.parseTextColor(value) == 0) return "";
        return value.trim().toUpperCase();
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