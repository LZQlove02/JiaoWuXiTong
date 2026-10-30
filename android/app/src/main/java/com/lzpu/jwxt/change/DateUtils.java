package com.lzpu.jwxt.change;

import java.util.Calendar;

/**
 * 课表变更用的日期工具。minSdk 22 没有 java.time，统一用 Calendar。
 * 口径与 src/utils/date.js / src/utils/term.js 保持一致。
 */
final class DateUtils {

    private static final long DAY_MS = 86400000L;

    private DateUtils() {
    }

    static Calendar atMidnight(int year, int monthIndex, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, monthIndex);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    /** 解析 yyyy-MM-dd；失败返回 null。 */
    static int[] parseDate(String iso) {
        if (iso == null) return null;
        String[] parts = iso.trim().split("-");
        if (parts.length != 3) return null;
        try {
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            if (month < 1 || month > 12 || day < 1 || day > 31) return null;
            return new int[]{year, month - 1, day};
        } catch (NumberFormatException error) {
            return null;
        }
    }

    /** 当前周：term.startDate 起算，开学前按第 1 周，钳制 1..20。 */
    static int currentWeek(String startDate, Calendar now) {
        int[] start = parseDate(startDate);
        if (start == null) return 1;
        Calendar from = atMidnight(start[0], start[1], start[2]);
        Calendar today = atMidnight(
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH));
        long days = Math.round((today.getTimeInMillis() - from.getTimeInMillis()) / (double) DAY_MS);
        if (days < 0) return 1;
        int week = (int) Math.floor(days / 7.0) + 1;
        return Math.min(Math.max(week, 1), 20);
    }

    /** 第 week 周、星期 weekday（1=周一）对应的日期。 */
    static Calendar occurrence(String startDate, int week, int weekday) {
        int[] start = parseDate(startDate);
        if (start == null) return null;
        int offset = (week - 1) * 7 + (weekday - 1);
        Calendar day = atMidnight(start[0], start[1], start[2]);
        day.add(Calendar.DAY_OF_MONTH, offset);
        return day;
    }

    /** "HH:mm" → 当天分钟数；解析失败返回 -1。 */
    static int minutes(String clock) {
        if (clock == null) return -1;
        String[] parts = clock.trim().split(":");
        if (parts.length < 2) return -1;
        try {
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    /**
     * 判断「本周某天的课」是否已经结束。
     * sectionRange 形如 "08:20-09:00"；缺失时按 23:59 处理，宁可提醒也不吞掉。
     */
    static boolean isPast(Calendar occurrenceDay, String sectionRange, Calendar now) {
        if (occurrenceDay == null) return false;
        Calendar dayStart = atMidnight(
                occurrenceDay.get(Calendar.YEAR),
                occurrenceDay.get(Calendar.MONTH),
                occurrenceDay.get(Calendar.DAY_OF_MONTH));
        Calendar today = atMidnight(
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH));
        long diff = Math.round((dayStart.getTimeInMillis() - today.getTimeInMillis()) / (double) DAY_MS);
        if (diff < 0) return true;
        if (diff > 0) return false;

        int endMinute = 23 * 60 + 59;
        if (sectionRange != null) {
            int dash = sectionRange.lastIndexOf('-');
            if (dash >= 0) {
                int parsed = minutes(sectionRange.substring(dash + 1));
                if (parsed >= 0) endMinute = parsed;
            }
        }
        int nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        return nowMinute > endMinute;
    }

    /** 带本地时区偏移的 ISO 时间，格式与 JS 侧一致：2026-10-30T07:10:12+08:00。 */
    static String isoNow(Calendar now) {
        String base = String.format(
                java.util.Locale.US,
                "%04d-%02d-%02dT%02d:%02d:%02d",
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH),
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                now.get(Calendar.SECOND));
        int offsetMinutes = (now.get(Calendar.ZONE_OFFSET) + now.get(Calendar.DST_OFFSET)) / 60000;
        char sign = offsetMinutes >= 0 ? '+' : '-';
        int abs = Math.abs(offsetMinutes);
        return base + String.format(java.util.Locale.US, "%c%02d:%02d", sign, abs / 60, abs % 60);
    }

    /** yyyy-MM-dd（取 ISO 字符串前 10 位）。 */
    static String dayKey(String iso) {
        if (iso == null || iso.length() < 10) return "";
        return iso.substring(0, 10);
    }

    /** 两个 yyyy-MM-dd 相差天数；解析失败返回 Integer.MIN_VALUE。 */
    static int daysBetween(String olderIso, String newerIso) {
        int[] older = parseDate(dayKey(olderIso));
        int[] newer = parseDate(dayKey(newerIso));
        if (older == null || newer == null) return Integer.MIN_VALUE;
        Calendar a = atMidnight(older[0], older[1], older[2]);
        Calendar b = atMidnight(newer[0], newer[1], newer[2]);
        return (int) Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / (double) DAY_MS);
    }
}