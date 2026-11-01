package com.lzqlove.jiaowu.change;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * kbList → 规范化会话。规则必须与 src/api/zf.js 的 mapSchedule / parseWeeks /
 * parseSections / cleanRoom 完全一致，否则课表显示与变更判定会对不上。
 */
public final class ScheduleParser {

    private static final Pattern WEEK_RANGE = Pattern.compile("(\\d+)\\s*[-~至]\\s*(\\d+)\\s*周?");
    private static final Pattern WEEK_SINGLE = Pattern.compile("(\\d+)\\s*周");
    private static final Pattern SECTION_RANGE = Pattern.compile("(\\d+)\\s*[-~至]\\s*(\\d+)");
    private static final Pattern SECTION_SINGLE = Pattern.compile("(\\d+)");
    private static final Pattern ROOM_COUNT = Pattern.compile("\\(\\d+\\s*人\\)");

    private ScheduleParser() {
    }

    public static List<ScheduleSession> parse(String body) throws JSONException {
        JSONObject root = new JSONObject(body);
        return parse(root);
    }

    public static List<ScheduleSession> parse(JSONObject root) throws JSONException {
        List<ScheduleSession> sessions = new ArrayList<>();
        if (root == null) return sessions;
        JSONArray list = root.optJSONArray("kbList");
        if (list == null) return sessions;

        Map<String, String> names = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < list.length(); i += 1) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            String code = text(item, "kch");
            String name = text(item, "kcmc");
            String courseId = !code.isEmpty() ? code : name;
            if (courseId.isEmpty() || name.isEmpty()) continue;
            if (!names.containsKey(courseId)) names.put(courseId, name);

            int weekday = intValue(text(item, "xqj"), 1);
            if (weekday < 1) weekday = 1;
            String sectionText = text(item, "jcs");
            if (sectionText.isEmpty()) sectionText = text(item, "jc");
            int[] sections = parseSections(sectionText);
            int[] weeks = parseWeeks(text(item, "zcd"));
            String room = cleanRoom(text(item, "cdmc"));

            ScheduleSession session = new ScheduleSession(
                    courseId, name, weekday, sections[0], sections[1], weeks, room);
            if (!seen.add(session.dedupeKey())) continue;
            sessions.add(session);
        }

        // 与 JS 一致：同一门课的课名取第一次出现的值
        List<ScheduleSession> normalized = new ArrayList<>(sessions.size());
        for (ScheduleSession session : sessions) {
            normalized.add(new ScheduleSession(
                    session.courseId,
                    names.getOrDefault(session.courseId, session.courseName),
                    session.weekday,
                    session.startSection,
                    session.endSection,
                    session.weeks,
                    session.room));
        }
        return normalized;
    }

    private static String text(JSONObject json, String key) {
        Object value = json.opt(key);
        if (value == null || value == JSONObject.NULL) return "";
        return String.valueOf(value).trim();
    }

    private static int intValue(String text, int fallback) {
        if (text == null || text.isEmpty()) return fallback;
        try {
            return (int) Double.parseDouble(text);
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    static int[] parseSections(String text) {
        String value = text == null ? "" : text;
        Matcher range = SECTION_RANGE.matcher(value);
        if (range.find()) {
            return new int[]{Integer.parseInt(range.group(1)), Integer.parseInt(range.group(2))};
        }
        Matcher single = SECTION_SINGLE.matcher(value);
        if (single.find()) {
            int section = Integer.parseInt(single.group(1));
            return new int[]{section, section};
        }
        return new int[]{1, 1};
    }

    static int[] parseWeeks(String text) {
        if (text == null || text.trim().isEmpty()) return defaultWeeks();
        TreeSet<Integer> weeks = new TreeSet<>();
        Matcher range = WEEK_RANGE.matcher(text);
        while (range.find()) {
            int start = Integer.parseInt(range.group(1));
            int end = Integer.parseInt(range.group(2));
            for (int week = start; week <= end; week += 1) weeks.add(week);
        }
        Matcher single = WEEK_SINGLE.matcher(text);
        while (single.find()) {
            weeks.add(Integer.parseInt(single.group(1)));
        }
        // 与 JS 一致：先把空结果补成 1–20 周，再按单双周过滤；
        // 过滤后为空就保持为空（例如「2周(单)」这种自相矛盾的写法）。
        if (weeks.isEmpty()) {
            for (int week = 1; week <= 20; week += 1) weeks.add(week);
        }

        boolean odd = text.contains("单");
        boolean even = text.contains("双");
        List<Integer> result = new ArrayList<>(weeks.size());
        for (int week : weeks) {
            if (odd && !even && week % 2 == 0) continue;
            if (even && !odd && week % 2 == 1) continue;
            result.add(week);
        }
        int[] array = new int[result.size()];
        for (int i = 0; i < result.size(); i += 1) array[i] = result.get(i);
        return array;
    }

    static String cleanRoom(String value) {
        return ROOM_COUNT.matcher(value == null ? "" : value).replaceAll("").trim();
    }

    private static int[] defaultWeeks() {
        int[] weeks = new int[20];
        for (int i = 0; i < weeks.length; i += 1) weeks[i] = i + 1;
        return weeks;
    }
}