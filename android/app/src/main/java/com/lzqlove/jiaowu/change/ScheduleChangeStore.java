package com.lzqlove.jiaowu.change;

import android.content.Context;
import android.content.SharedPreferences;

import com.lzqlove.jiaowu.LocalCipher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 变更引擎的本地存储：基线、30 天日志、开关与登录失效提示日期。
 * 后台任务与前台共用同一份基线，读写统一走这里。
 */
public final class ScheduleChangeStore {

    private static final String PREFS_NAME = "jiaowu_schedule_change";
    private static final String KEY_BASELINE = "baseline";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_NOTIFICATION_ASKED = "notification_asked";
    private static final String KEY_LAST_EXPIRED = "last_session_expired";
    private static final int MAX_ENTRIES = 200;
    private static final int MAX_AGE_DAYS = 30;

    private ScheduleChangeStore() {
    }

    public static final class Baseline {
        public TermInfo term;
        public String studentId = "";
        public List<ScheduleSession> sessions = new ArrayList<>();
        public Map<Integer, String> sectionTimes = defaultSectionTimes();
        public String savedAt = "";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ---------- 基线 ----------

    public static Baseline readBaseline(Context context) {
        String raw = LocalCipher.open(prefs(context).getString(KEY_BASELINE, ""));
        if (raw == null || raw.isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(raw);
            Baseline baseline = new Baseline();
            baseline.term = TermInfo.fromJson(json.optJSONObject("term"));
            if (baseline.term == null || !baseline.term.isValid()) return null;
            baseline.studentId = json.optString("studentId");
            JSONArray sessions = json.optJSONArray("sessions");
            if (sessions != null) {
                for (int i = 0; i < sessions.length(); i += 1) {
                    ScheduleSession session = ScheduleSession.fromJson(sessions.optJSONObject(i));
                    if (session != null) baseline.sessions.add(session);
                }
            }
            Map<Integer, String> times = readSectionTimes(json.optJSONObject("sectionTimes"));
            if (!times.isEmpty()) baseline.sectionTimes = times;
            baseline.savedAt = json.optString("savedAt");
            return baseline;
        } catch (JSONException error) {
            return null;
        }
    }

    public static void writeBaseline(Context context, Baseline baseline) {
        if (baseline == null || baseline.term == null) return;
        try {
            JSONObject json = new JSONObject();
            json.put("term", baseline.term.toJson());
            json.put("studentId", baseline.studentId);
            JSONArray sessions = new JSONArray();
            for (ScheduleSession session : baseline.sessions) sessions.put(session.toJson());
            json.put("sessions", sessions);
            JSONObject times = new JSONObject();
            for (Map.Entry<Integer, String> item : baseline.sectionTimes.entrySet()) {
                times.put(String.valueOf(item.getKey()), item.getValue());
            }
            json.put("sectionTimes", times);
            json.put("savedAt", baseline.savedAt);
            prefs(context).edit().putString(KEY_BASELINE, LocalCipher.seal(json.toString())).apply();
        } catch (JSONException error) {
            // 写不进去时保留旧基线，下一次再试
        }
    }

    public static void clearBaseline(Context context) {
        prefs(context).edit().remove(KEY_BASELINE).apply();
    }

    private static Map<Integer, String> readSectionTimes(JSONObject json) {
        Map<Integer, String> times = new LinkedHashMap<>();
        if (json == null) return times;
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                int section = Integer.parseInt(key);
                String range = json.optString(key);
                if (section > 0 && !range.isEmpty()) times.put(section, range);
            } catch (NumberFormatException ignored) {
                // 非数字键直接忽略
            }
        }
        return times;
    }

    // ---------- 变更日志 ----------

    public static List<ChangeEntry> readEntries(Context context) {
        List<ChangeEntry> entries = new ArrayList<>();
        String raw = LocalCipher.open(prefs(context).getString(KEY_ENTRIES, ""));
        if (raw == null || raw.isEmpty()) return entries;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i += 1) {
                ChangeEntry entry = ChangeEntry.fromJson(array.optJSONObject(i));
                if (entry != null && entry.type != null && !entry.type.isEmpty()) entries.add(entry);
            }
        } catch (JSONException error) {
            return new ArrayList<>();
        }
        sortNewestFirst(entries);
        return entries;
    }

    public static void writeEntries(Context context, List<ChangeEntry> entries, Calendar now) {
        List<ChangeEntry> pruned = prune(entries, now);
        try {
            JSONArray array = new JSONArray();
            for (ChangeEntry entry : pruned) array.put(entry.toJson());
            prefs(context).edit().putString(KEY_ENTRIES, LocalCipher.seal(array.toString())).apply();
        } catch (JSONException error) {
            // 单条写入失败不阻塞主流程
        }
    }

    private static List<ChangeEntry> prune(List<ChangeEntry> entries, Calendar now) {
        String today = DateUtils.isoNow(now);
        List<ChangeEntry> kept = new ArrayList<>();
        for (ChangeEntry entry : entries) {
            int days = DateUtils.daysBetween(entry.detectedAt, today);
            if (days != Integer.MIN_VALUE && days > MAX_AGE_DAYS) continue;
            kept.add(entry);
        }
        sortNewestFirst(kept);
        if (kept.size() > MAX_ENTRIES) kept = new ArrayList<>(kept.subList(0, MAX_ENTRIES));
        return kept;
    }

    private static void sortNewestFirst(List<ChangeEntry> entries) {
        Collections.sort(entries, new Comparator<ChangeEntry>() {
            @Override
            public int compare(ChangeEntry left, ChangeEntry right) {
                int byTime = String.valueOf(right.detectedAt).compareTo(String.valueOf(left.detectedAt));
                if (byTime != 0) return byTime;
                return String.valueOf(right.id).compareTo(String.valueOf(left.id));
            }
        });
    }

    public static void markAllRead(Context context) {
        List<ChangeEntry> entries = readEntries(context);
        for (ChangeEntry entry : entries) entry.read = true;
        writeEntries(context, entries, Calendar.getInstance());
    }

    public static void markNotified(Context context, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        List<ChangeEntry> entries = readEntries(context);
        for (ChangeEntry entry : entries) {
            if (ids.contains(entry.id)) entry.notified = true;
        }
        writeEntries(context, entries, Calendar.getInstance());
    }

    public static int unreadCount(Context context) {
        if (!isEnabled(context)) return 0;
        int count = 0;
        for (ChangeEntry entry : readEntries(context)) {
            // 换教室只进历史，不参与提醒条 / 红点 / 组件角标
            if (!entry.read && entry.isRemindable()) count += 1;
        }
        return count;
    }

    // ---------- 设置 ----------

    public static boolean isEnabled(Context context) {
        // 默认关闭：后台检查需用户首次开启前阅读说明并主动同意
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static boolean hasAskedNotification(Context context) {
        return prefs(context).getBoolean(KEY_NOTIFICATION_ASKED, false);
    }

    public static void markNotificationAsked(Context context) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply();
    }

    public static String readLastSessionExpiredDate(Context context) {
        return prefs(context).getString(KEY_LAST_EXPIRED, "");
    }

    public static void writeLastSessionExpiredDate(Context context, String dayKey) {
        prefs(context).edit().putString(KEY_LAST_EXPIRED, dayKey == null ? "" : dayKey).apply();
    }

    public static void clearLastSessionExpiredDate(Context context) {
        prefs(context).edit().remove(KEY_LAST_EXPIRED).apply();
    }

    public static void clearAll(Context context) {
        prefs(context).edit().clear().apply();
    }

    // ---------- 节次时间表 ----------

    /** 与 src/utils/date.js 的 SECTION_TIMES 一致；基线缺失时兜底。 */
    public static Map<Integer, String> defaultSectionTimes() {
        Map<Integer, String> times = new LinkedHashMap<>();
        times.put(1, "08:20-09:00");
        times.put(2, "09:05-09:45");
        times.put(3, "10:05-10:45");
        times.put(4, "10:50-11:30");
        times.put(5, "11:35-12:15");
        times.put(6, "14:30-15:10");
        times.put(7, "15:15-15:55");
        times.put(8, "16:15-16:55");
        times.put(9, "17:00-17:40");
        times.put(10, "19:30-20:10");
        times.put(11, "20:15-20:55");
        times.put(12, "21:00-21:40");
        return times;
    }

    public static Map<Integer, String> readSectionTimes(Context context) {
        Baseline baseline = readBaseline(context);
        if (baseline == null || baseline.sectionTimes.isEmpty()) return defaultSectionTimes();
        return baseline.sectionTimes;
    }
}