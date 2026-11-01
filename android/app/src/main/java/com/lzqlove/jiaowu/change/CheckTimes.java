package com.lzqlove.jiaowu.change;

/**
 * 后台检查的两个时段。用「当天分钟数」表示，便于校验与计算下一次触发。
 * 只支持同一天内的两个时段，要求第二个时段比第一个至少晚 60 分钟，不支持跨午夜。
 */
public final class CheckTimes {

    public static final String DEFAULT_MORNING = "07:10";
    public static final String DEFAULT_NOON = "12:50";

    public final int morningMinutes;
    public final int noonMinutes;

    public CheckTimes(int morningMinutes, int noonMinutes) {
        this.morningMinutes = morningMinutes;
        this.noonMinutes = noonMinutes;
    }

    /** 解析 HH:mm；非法返回 null。 */
    public static CheckTimes parse(String morning, String noon) {
        int first = minutesOf(morning);
        int second = minutesOf(noon);
        if (first < 0 || second < 0) return null;
        return new CheckTimes(first, second);
    }

    public static CheckTimes defaults() {
        return parse(DEFAULT_MORNING, DEFAULT_NOON);
    }

    public boolean isValid() {
        if (morningMinutes < 0 || morningMinutes >= 24 * 60) return false;
        if (noonMinutes < 0 || noonMinutes >= 24 * 60) return false;
        return noonMinutes - morningMinutes >= 60;
    }

    public static boolean isValid(String value) {
        return minutesOf(value) >= 0;
    }

    public static int minutesOf(String value) {
        String text = value == null ? "" : value.trim();
        if (!text.matches("\\d{1,2}:\\d{2}")) return -1;
        String[] parts = text.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return -1;
        return hour * 60 + minute;
    }

    public static String format(int minutes) {
        int value = Math.max(0, Math.min(minutes, 23 * 60 + 59));
        return String.format(java.util.Locale.US, "%02d:%02d", value / 60, value % 60);
    }

    public String morningText() {
        return format(morningMinutes);
    }

    public String noonText() {
        return format(noonMinutes);
    }
}
