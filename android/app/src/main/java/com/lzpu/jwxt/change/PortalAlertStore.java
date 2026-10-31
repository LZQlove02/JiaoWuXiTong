package com.lzpu.jwxt.change;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 通知/消息提醒的本地基线。默认关闭，用户开启后第一次检查静默建立基线。
 * 只保存内容 ID，不保存标题、正文或发件人。
 */
public final class PortalAlertStore {

    private static final String PREFS_NAME = "lzpu_portal_alert";
    private static final String KEY_NOTICES_ENABLED = "notices_enabled";
    private static final String KEY_MESSAGES_ENABLED = "messages_enabled";
    private static final String KEY_NOTICES_IDS = "notices_ids";
    private static final String KEY_MESSAGES_IDS = "messages_ids";
    private static final String KEY_NOTICES_READY = "notices_ready";
    private static final String KEY_MESSAGES_READY = "messages_ready";
    private static final String KEY_STUDENT_ID = "student_id";

    private PortalAlertStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isNoticesEnabled(Context context) {
        return prefs(context).getBoolean(KEY_NOTICES_ENABLED, false);
    }

    public static boolean isMessagesEnabled(Context context) {
        return prefs(context).getBoolean(KEY_MESSAGES_ENABLED, false);
    }

    public static boolean isAnyEnabled(Context context) {
        return isNoticesEnabled(context) || isMessagesEnabled(context);
    }

    public static void setNoticesEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_NOTICES_ENABLED, enabled).apply();
    }

    public static void setMessagesEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_MESSAGES_ENABLED, enabled).apply();
    }

    public static String readStudentId(Context context) {
        return prefs(context).getString(KEY_STUDENT_ID, "");
    }

    public static void writeStudentId(Context context, String studentId) {
        prefs(context).edit().putString(KEY_STUDENT_ID, studentId == null ? "" : studentId).apply();
    }

    public static boolean isReady(Context context, boolean notices) {
        return prefs(context).getBoolean(notices ? KEY_NOTICES_READY : KEY_MESSAGES_READY, false);
    }

    public static void setReady(Context context, boolean notices, boolean ready) {
        prefs(context).edit()
                .putBoolean(notices ? KEY_NOTICES_READY : KEY_MESSAGES_READY, ready)
                .apply();
    }

    public static Set<String> readIds(Context context, boolean notices) {
        String raw = prefs(context).getString(notices ? KEY_NOTICES_IDS : KEY_MESSAGES_IDS, "");
        Set<String> ids = new HashSet<>();
        if (raw == null || raw.isEmpty()) return ids;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i += 1) {
                String id = array.optString(i, "").trim();
                if (!id.isEmpty()) ids.add(id);
            }
        } catch (JSONException error) {
            return new HashSet<>();
        }
        return ids;
    }

    public static void writeIds(Context context, boolean notices, Set<String> ids) {
        JSONArray array = new JSONArray();
        if (ids != null) {
            for (String id : ids) {
                if (id != null && !id.isEmpty()) array.put(id);
            }
        }
        prefs(context).edit()
                .putString(notices ? KEY_NOTICES_IDS : KEY_MESSAGES_IDS, array.toString())
                .apply();
    }

    public static void resetType(Context context, boolean notices) {
        prefs(context).edit()
                .remove(notices ? KEY_NOTICES_IDS : KEY_MESSAGES_IDS)
                .remove(notices ? KEY_NOTICES_READY : KEY_MESSAGES_READY)
                .apply();
    }

    public static void clearBaselines(Context context) {
        prefs(context).edit()
                .remove(KEY_NOTICES_IDS)
                .remove(KEY_MESSAGES_IDS)
                .remove(KEY_NOTICES_READY)
                .remove(KEY_MESSAGES_READY)
                .remove(KEY_STUDENT_ID)
                .apply();
    }

    /** 单测/调试辅助：只保留最新的 ID 列表。 */
    public static void writeRecentIds(Context context, boolean notices, List<String> current) {
        writeIds(context, notices, new HashSet<>(PortalAlertEngine.recentList(current)));
    }
}
