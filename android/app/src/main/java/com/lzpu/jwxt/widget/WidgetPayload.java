package com.lzpu.jwxt.widget;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 组件数据模型：解析 JS 侧写入的 payload JSON。
 * 解析失败一律退回「未登录」占位，保证组件不崩、不显示脏数据。
 */
public final class WidgetPayload {

    public static final String FALLBACK_COLOR = "#1B5FAA";

    public boolean loggedIn;
    public boolean scheduleLoaded;
    public String updatedLabel = "";
    public String accountName = "";
    public String termLabel = "";
    public int termWeek;
    public int remainingToday;
    public int unread;
    public String latestNotice = "";
    public String gradeLabel = "";
    public ClassItem next;
    public final List<ClassItem> today = new ArrayList<>();
    public final List<WeekItem> week = new ArrayList<>();

    public static final class ClassItem {
        public String dayLabel = "";
        public int start;
        public int end;
        public String startTime = "";
        public String endTime = "";
        public String name = "";
        public String room = "";
        public String color = FALLBACK_COLOR;

        public String timeRange() {
            if (startTime.isEmpty()) return endTime;
            if (endTime.isEmpty()) return startTime;
            return startTime + "-" + endTime;
        }

        public boolean isToday() {
            return dayLabel.isEmpty() || "今天".equals(dayLabel);
        }
    }

    public static final class WeekItem {
        public int weekday;
        public int start;
        public int end;
        public String name = "";
        public String room = "";
        public String color = FALLBACK_COLOR;
    }

    public static WidgetPayload loggedOut() {
        return new WidgetPayload();
    }

    public static WidgetPayload parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return loggedOut();
        }
        try {
            JSONObject root = new JSONObject(json);
            WidgetPayload payload = new WidgetPayload();
            payload.loggedIn = root.optBoolean("loggedIn", false);
            payload.scheduleLoaded = root.optBoolean("scheduleLoaded", false);
            payload.updatedLabel = root.optString("updatedAtLabel", "");

            JSONObject account = root.optJSONObject("account");
            if (account != null) {
                payload.accountName = account.optString("name", "");
            }

            JSONObject term = root.optJSONObject("term");
            if (term != null) {
                payload.termLabel = term.optString("label", "");
                payload.termWeek = term.optInt("week", 0);
            }

            JSONArray today = root.optJSONArray("today");
            if (today != null) {
                for (int i = 0; i < today.length(); i += 1) {
                    JSONObject item = today.optJSONObject(i);
                    if (item != null) payload.today.add(readClass(item));
                }
            }

            JSONObject next = root.optJSONObject("next");
            if (next != null) {
                payload.next = readClass(next);
            }

            payload.remainingToday = root.optInt("remainingToday", 0);

            JSONObject messages = root.optJSONObject("messages");
            if (messages != null) {
                payload.unread = messages.optInt("unread", 0);
            }

            payload.latestNotice = firstTitle(root.optJSONObject("notices"));
            payload.gradeLabel = readGrades(root.optJSONObject("grades"));

            JSONArray week = root.optJSONArray("week");
            if (week != null) {
                for (int i = 0; i < week.length(); i += 1) {
                    JSONObject item = week.optJSONObject(i);
                    if (item == null) continue;
                    WeekItem entry = new WeekItem();
                    entry.weekday = item.optInt("weekday", 0);
                    entry.start = item.optInt("start", 0);
                    entry.end = item.optInt("end", 0);
                    entry.name = item.optString("name", "");
                    entry.room = item.optString("room", "");
                    entry.color = item.optString("color", FALLBACK_COLOR);
                    payload.week.add(entry);
                }
            }
            return payload;
        } catch (Exception error) {
            return loggedOut();
        }
    }

    private static ClassItem readClass(JSONObject item) {
        ClassItem entry = new ClassItem();
        entry.dayLabel = item.optString("dayLabel", "");
        entry.start = item.optInt("start", 0);
        entry.end = item.optInt("end", 0);
        entry.startTime = item.optString("startTime", "");
        entry.endTime = item.optString("endTime", "");
        entry.name = item.optString("name", "");
        entry.room = item.optString("room", "");
        entry.color = item.optString("color", FALLBACK_COLOR);
        return entry;
    }

    private static String firstTitle(JSONObject block) {
        if (block == null) return "";
        JSONArray latest = block.optJSONArray("latest");
        if (latest == null || latest.length() == 0) return "";
        JSONObject first = latest.optJSONObject(0);
        return first == null ? "" : first.optString("title", "");
    }

    private static String readGrades(JSONObject grades) {
        if (grades == null) return "";
        double gpa = grades.optDouble("gpa", Double.NaN);
        double credits = grades.optDouble("totalCredits", 0);
        StringBuilder builder = new StringBuilder();
        if (!Double.isNaN(gpa)) {
            builder.append("GPA ").append(trimNumber(gpa));
        }
        if (credits > 0) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append(trimNumber(credits)).append(" 学分");
        }
        return builder.toString();
    }

    private static String trimNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(Math.round(value * 100) / 100.0);
    }
}