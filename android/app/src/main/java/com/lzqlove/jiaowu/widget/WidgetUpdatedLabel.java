package com.lzqlove.jiaowu.widget;

import android.content.Context;

import com.lzqlove.jiaowu.R;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 「更新于」文案：把 (updatedAtDate, updatedAtLabel) 换算成「刚刚更新 / 5 分钟前更新 /
 * 今天 19:58 更新 / 昨天 09:20 更新 / 11月20日 09:20 更新 / 2025年11月20日 09:20 更新」。
 *
 * 纯逻辑，不碰 View，三个组件共用；老 payload 没有 updatedAtDate 时退回原来的「更新于 19:58」。
 */
public final class WidgetUpdatedLabel {

    public static final int KIND_UNKNOWN = 0;
    public static final int KIND_JUST_NOW = 1;
    public static final int KIND_MINUTES = 2;
    public static final int KIND_TODAY = 3;
    public static final int KIND_YESTERDAY = 4;
    public static final int KIND_DATE = 5;
    public static final int KIND_DATE_YEAR = 6;

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60L * MINUTE_MS;

    public static final class Label {
        public final int kind;
        public final int minutes;
        public final int year;
        public final int month;
        public final int day;
        /** HH:mm；kind 为 UNKNOWN 时就是原样显示的时刻（可能为空）。 */
        public final String clock;

        Label(int kind, int minutes, int year, int month, int day, String clock) {
            this.kind = kind;
            this.minutes = minutes;
            this.year = year;
            this.month = month;
            this.day = day;
            this.clock = clock;
        }
    }

    private WidgetUpdatedLabel() {
    }

    /** date 为 yyyy-MM-dd，clock 为 HH:mm；任一项解析不了就退回 UNKNOWN。 */
    public static Label parse(String date, String clock, Calendar now) {
        String time = clock == null ? "" : clock.trim();
        if (time.isEmpty()) return new Label(KIND_UNKNOWN, 0, 0, 0, 0, "");

        Date parsedDate = parseDate(date);
        int[] hourMinute = parseClock(time);
        if (parsedDate == null || hourMinute == null) {
            return new Label(KIND_UNKNOWN, 0, 0, 0, 0, time);
        }

        Calendar at = Calendar.getInstance();
        at.setTime(parsedDate);
        at.set(Calendar.HOUR_OF_DAY, hourMinute[0]);
        at.set(Calendar.MINUTE, hourMinute[1]);
        at.set(Calendar.SECOND, 0);
        at.set(Calendar.MILLISECOND, 0);

        Calendar ref = now == null ? Calendar.getInstance() : (Calendar) now.clone();

        // 未来时间（时钟回拨 / 抓取时间超前）按刚刚处理，绝不显示负数
        long diff = Math.max(0L, ref.getTimeInMillis() - at.getTimeInMillis());
        int year = at.get(Calendar.YEAR);
        int month = at.get(Calendar.MONTH) + 1;
        int day = at.get(Calendar.DAY_OF_MONTH);

        // 一小时内优先看差值：23:58 抓的、00:03 刷新时「5 分钟前」比「昨天 23:58」更有用
        if (diff < MINUTE_MS) return new Label(KIND_JUST_NOW, 0, year, month, day, time);
        if (diff < HOUR_MS) {
            return new Label(KIND_MINUTES, (int) (diff / MINUTE_MS), year, month, day, time);
        }
        if (isSameDay(at, ref)) return new Label(KIND_TODAY, 0, year, month, day, time);

        Calendar yesterday = (Calendar) ref.clone();
        yesterday.add(Calendar.DAY_OF_MONTH, -1);
        if (isSameDay(at, yesterday)) return new Label(KIND_YESTERDAY, 0, year, month, day, time);
        if (year == ref.get(Calendar.YEAR)) return new Label(KIND_DATE, 0, year, month, day, time);
        return new Label(KIND_DATE_YEAR, 0, year, month, day, time);
    }

    public static String text(Context context, String date, String clock) {
        return text(context, parse(date, clock, Calendar.getInstance()));
    }

    public static String text(Context context, Label label) {
        if (label == null) return "";
        switch (label.kind) {
            case KIND_JUST_NOW:
                return context.getString(R.string.widget_time_just_now);
            case KIND_MINUTES:
                return context.getString(R.string.widget_time_minutes_ago, label.minutes);
            case KIND_TODAY:
                return context.getString(R.string.widget_time_today, label.clock);
            case KIND_YESTERDAY:
                return context.getString(R.string.widget_time_yesterday, label.clock);
            case KIND_DATE:
                return context.getString(R.string.widget_time_date, label.month, label.day, label.clock);
            case KIND_DATE_YEAR:
                return context.getString(R.string.widget_time_date_year,
                    label.year, label.month, label.day, label.clock);
            default:
                // 老 payload 只有 HH:mm：保持原来的「更新于 19:58」，不要退化成一句怪话
                return label.clock.isEmpty()
                    ? ""
                    : context.getString(R.string.widget_updated_at, label.clock);
        }
    }

    private static boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
            && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
            && a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH);
    }

    private static Date parseDate(String date) {
        String text = date == null ? "" : date.trim();
        if (text.isEmpty()) return null;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        try {
            return format.parse(text, new ParsePosition(0));
        } catch (RuntimeException error) {
            return null;
        }
    }

    /** "19:58" → {19, 58}；"9:5" 也接受，其它一律 null。 */
    private static int[] parseClock(String clock) {
        String[] parts = clock.split(":");
        if (parts.length != 2) return null;
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
            return new int[]{hour, minute};
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
