package com.lzpu.jwxt.change;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

/**
 * 规范化后的一节「会话」：课程 + 星期 + 节次 + 周次集合 + 教室。
 * 判定时教室不参与 key（只换教室不算变更），但仍随会话保存，用于「换教室」历史。
 */
public final class ScheduleSession {

    public final String courseId;
    public final String courseName;
    public final int weekday;
    public final int startSection;
    public final int endSection;
    public final int[] weeks;
    public final String room;

    public ScheduleSession(String courseId, String courseName, int weekday,
                           int startSection, int endSection, int[] weeks, String room) {
        this.courseId = courseId == null ? "" : courseId;
        this.courseName = courseName == null ? "" : courseName;
        this.weekday = weekday;
        this.startSection = startSection;
        this.endSection = endSection;
        this.weeks = normalizeWeeks(weeks);
        this.room = room == null ? "" : room;
    }

    private static int[] normalizeWeeks(int[] source) {
        if (source == null || source.length == 0) {
            int[] fallback = new int[16];
            for (int i = 0; i < fallback.length; i += 1) fallback[i] = i + 1;
            return fallback;
        }
        int[] copy = Arrays.copyOf(source, source.length);
        Arrays.sort(copy);
        int unique = 0;
        for (int i = 0; i < copy.length; i += 1) {
            if (i == 0 || copy[i] != copy[i - 1]) {
                copy[unique] = copy[i];
                unique += 1;
            }
        }
        return unique == copy.length ? copy : Arrays.copyOf(copy, unique);
    }

    public String weeksSignature() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < weeks.length; i += 1) {
            if (i > 0) builder.append(',');
            builder.append(weeks[i]);
        }
        return builder.toString();
    }

    /** 判定键：不含教室。 */
    public String key() {
        return courseId + "|" + weekday + "|" + startSection + "|" + endSection + "|" + weeksSignature();
    }

    /** 解析去重键：与 JS mapSchedule 的 seen key 一致（含教室）。 */
    public String dedupeKey() {
        return key() + "|" + room;
    }

    public boolean containsWeek(int week) {
        for (int item : weeks) {
            if (item == week) return true;
        }
        return false;
    }

    /** 周次展示文案：连续区间压缩，全单/全双追加 (单)/(双)。 */
    public String weeksLabel() {
        if (weeks.length == 0) return "";
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < weeks.length) {
            int start = weeks[index];
            int end = start;
            // 连续周（差 1）或单双周（差 2）都压成区间，其余保持离散
            int step = index + 1 < weeks.length ? weeks[index + 1] - weeks[index] : 0;
            if (step == 1 || step == 2) {
                while (index + 1 < weeks.length && weeks[index + 1] - weeks[index] == step) {
                    end = weeks[index + 1];
                    index += 1;
                }
            }
            if (builder.length() > 0) builder.append(',');
            builder.append(start == end ? String.valueOf(start) : start + "-" + end);
            index += 1;
        }
        builder.append("周");
        if (weeks.length >= 2 && weeks[weeks.length - 1] - weeks[0] == 2 * (weeks.length - 1)) {
            if (weeks[0] % 2 == 1) builder.append("(单)");
            else builder.append("(双)");
        }
        return builder.toString();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("courseId", courseId);
        json.put("courseName", courseName);
        json.put("weekday", weekday);
        json.put("startSection", startSection);
        json.put("endSection", endSection);
        JSONArray weekArray = new JSONArray();
        for (int week : weeks) weekArray.put(week);
        json.put("weeks", weekArray);
        json.put("room", room);
        return json;
    }

    public static ScheduleSession fromJson(JSONObject json) {
        if (json == null) return null;
        JSONArray weekArray = json.optJSONArray("weeks");
        int[] weeks = new int[weekArray == null ? 0 : weekArray.length()];
        for (int i = 0; i < weeks.length; i += 1) weeks[i] = weekArray.optInt(i);
        return new ScheduleSession(
                json.optString("courseId"),
                json.optString("courseName"),
                json.optInt("weekday", 1),
                json.optInt("startSection", 1),
                json.optInt("endSection", 1),
                weeks,
                json.optString("room"));
    }
}