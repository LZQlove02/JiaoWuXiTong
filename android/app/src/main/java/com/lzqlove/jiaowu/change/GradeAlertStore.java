package com.lzqlove.jiaowu.change;

import android.content.Context;
import android.content.SharedPreferences;

import com.lzqlove.jiaowu.LocalCipher;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 成绩提醒的本地基线：按学号 + 学期隔离，只保存每门课的状态（pass/fail/pending）。
 * 不保存分数、教师或成绩单原文，降低本地敏感信息。
 */
public final class GradeAlertStore {

    private static final String PREFS_NAME = "jiaowu_grade_alert";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_BASELINE = "baseline";

    private GradeAlertStore() {
    }

    public static final class Baseline {
        public TermInfo term;
        public String studentId = "";
        public Map<String, String> courses = new HashMap<>();
        public String savedAt = "";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        // 默认关闭：与课表变更提醒一致，首次开启前需要用户明确同意
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static Baseline readBaseline(Context context) {
        String raw = LocalCipher.open(prefs(context).getString(KEY_BASELINE, ""));
        if (raw == null || raw.isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(raw);
            Baseline baseline = new Baseline();
            baseline.term = TermInfo.fromJson(json.optJSONObject("term"));
            if (baseline.term == null || !baseline.term.isValid()) return null;
            baseline.studentId = json.optString("studentId", "");
            baseline.savedAt = json.optString("savedAt", "");
            JSONObject courses = json.optJSONObject("courses");
            if (courses != null) {
                Iterator<String> keys = courses.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String status = courses.optString(key, "");
                    if (!key.isEmpty() && isKnownStatus(status)) {
                        baseline.courses.put(key, status);
                    }
                }
            }
            return baseline;
        } catch (JSONException error) {
            return null;
        }
    }

    public static void writeBaseline(Context context, TermInfo term, String studentId,
                                     Map<String, String> courses, String savedAt) {
        if (term == null || !term.isValid()) return;
        try {
            JSONObject json = new JSONObject();
            json.put("term", term.toJson());
            json.put("studentId", studentId == null ? "" : studentId);
            JSONObject courseJson = new JSONObject();
            if (courses != null) {
                for (Map.Entry<String, String> item : courses.entrySet()) {
                    if (item.getKey() != null && isKnownStatus(item.getValue())) {
                        courseJson.put(item.getKey(), item.getValue());
                    }
                }
            }
            json.put("courses", courseJson);
            json.put("savedAt", savedAt == null ? "" : savedAt);
            String sealed = LocalCipher.seal(json.toString());
            if (sealed == null) {
                // 加密失败时保留旧基线，下一次成功写入再覆盖
                ChangeDebug.warn(context, "成绩基线加密失败，保留旧基线");
                return;
            }
            prefs(context).edit().putString(KEY_BASELINE, sealed).apply();
        } catch (JSONException error) {
            // 写不进去时保留旧基线，下一次成功后再覆盖
        }
    }

    public static void clearBaseline(Context context) {
        prefs(context).edit().remove(KEY_BASELINE).apply();
    }

    private static boolean isKnownStatus(String status) {
        return GradeRecord.STATUS_PASS.equals(status)
                || GradeRecord.STATUS_FAIL.equals(status)
                || GradeRecord.STATUS_PENDING.equals(status);
    }
}
