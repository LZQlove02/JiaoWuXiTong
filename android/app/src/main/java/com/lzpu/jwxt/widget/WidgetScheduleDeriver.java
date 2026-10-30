package com.lzpu.jwxt.widget;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 原生侧「今天有哪些课 / 下一节是哪节 / 今天还剩几节」的推导，口径与
 * src/widget/payload.js 的 buildWidgetPayload + pickNextClass 一致。
 *
 * 用途：App 一整天没被打开时，payload 里的 today / next 还是昨天的；midnight 闹钟
 * 调用本类按 week[] + 当前时间重算，再写回 payload 并刷新组件。
 *
 * 纯逻辑，不依赖 Android 框架，便于单测。
 */
final class WidgetScheduleDeriver {

    private static final String[] WEEKDAY_LABELS = {"一", "二", "三", "四", "五", "六", "日"};

    private static final Comparator<WidgetPayload.WeekItem> BY_START =
        new Comparator<WidgetPayload.WeekItem>() {
            @Override
            public int compare(WidgetPayload.WeekItem a, WidgetPayload.WeekItem b) {
                return a.start - b.start;
            }
        };

    static final class Result {
        final List<WidgetPayload.ClassItem> today = new ArrayList<>();
        WidgetPayload.ClassItem next;
        int remainingToday;
    }

    private WidgetScheduleDeriver() {
    }

    /**
     * 快照是否已经跨周：payload 的 week[] 只代表同步时的那一周，周一 00:05 重算时
     * 若「当天的本周一」与 weekAnchor 不一致，说明 week[] 还是上一周的课，
     * 拿去推 today/next 会把上周的课当成今天上，必须降级成「打开 App 同步」。
     *
     * 老版本 payload 没有 weekAnchor 时按「不陈旧」处理，保持原有行为。
     */
    static boolean isStale(WidgetPayload payload, Calendar now) {
        if (payload == null || payload.weekAnchor == null || payload.weekAnchor.isEmpty()) {
            return false;
        }
        return !payload.weekAnchor.equals(mondayOf(now));
    }

    /**
     * 2x2 / 4x2 的日期前缀：下一节课不在今天时返回「周X」，今天返回空串。
     * 优先用 payload 里的 weekday（同步时就定死了，渲染时刻跨日也不会错位）；
     * 老 payload 没有 weekday 时退回 dayLabel，「明天」再按当天的 Calendar 推算。
     */
    static String dayPrefix(WidgetPayload.ClassItem item, Calendar now) {
        if (item == null || item.isToday()) return "";
        String byWeekday = weekdayName(item.weekday);
        if (!byWeekday.isEmpty()) return byWeekday;
        String label = item.dayLabel == null ? "" : item.dayLabel.trim();
        if (label.isEmpty()) return "";
        if (label.startsWith("周")) return label;
        if ("明天".equals(label)) {
            // Calendar.DAY_OF_WEEK 周日=1…周六=7 → 换算成周一=0…周日=6
            int todayIndex = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            return "周" + WEEKDAY_LABELS[(todayIndex + 1) % 7];
        }
        return label;
    }

    /** 当天所在周的周一，格式与 JS 侧 weekAnchorOf 一致：yyyy-MM-dd（本地日期）。 */
    static String weekdayName(int weekday) {
        if (weekday < 1 || weekday > 7) return "";
        return "周" + WEEKDAY_LABELS[weekday - 1];
    }

    // 当天所在周的周一，格式与 JS 侧 weekAnchorOf 一致：yyyy-MM-dd（本地日期）
    static String mondayOf(Calendar now) {
        Calendar day = (Calendar) now.clone();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        // Calendar.DAY_OF_WEEK：周日 1、周一 2 … 周六 7；换算成「距本周一几天」
        int shift = (day.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        day.add(Calendar.DAY_OF_MONTH, -shift);
        return String.format(Locale.US, "%04d-%02d-%02d",
            day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
    }

    static Result derive(WidgetPayload payload, Calendar now) {
        Result result = new Result();
        if (payload == null || payload.week.isEmpty()) return result;

        int weekday = weekdayOf(now);
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        List<WidgetPayload.WeekItem> todayItems = new ArrayList<>();
        for (WidgetPayload.WeekItem item : payload.week) {
            if (item.weekday == weekday) todayItems.add(item);
        }
        Collections.sort(todayItems, BY_START);

        for (WidgetPayload.WeekItem item : todayItems) {
            WidgetPayload.ClassItem cls = toClass(item, "今天");
            result.today.add(cls);
            if (endMinutes(cls) >= nowMinutes) result.remainingToday += 1;
        }

        // 下一节：今天还没开始上课的最近一节（进行中的不算），否则往后 7 天找最近有课的一天
        for (WidgetPayload.ClassItem cls : result.today) {
            if (startMinutes(cls) > nowMinutes) {
                result.next = cls;
                break;
            }
        }
        if (result.next == null) {
            for (int offset = 1; offset <= 7; offset += 1) {
                int target = ((weekday - 1 + offset) % 7) + 1;
                List<WidgetPayload.WeekItem> candidates = new ArrayList<>();
                for (WidgetPayload.WeekItem item : payload.week) {
                    if (item.weekday == target) candidates.add(item);
                }
                if (candidates.isEmpty()) continue;
                Collections.sort(candidates, BY_START);
                result.next = toClass(candidates.get(0),
                    offset == 1 ? "明天" : "周" + WEEKDAY_LABELS[target - 1]);
                break;
            }
        }
        return result;
    }

    // 周一=1 … 周日=7，与 JS 侧 getWeekday 一致
    private static int weekdayOf(Calendar now) {
        int day = now.get(Calendar.DAY_OF_WEEK);
        return day == Calendar.SUNDAY ? 7 : day - 1;
    }

    private static WidgetPayload.ClassItem toClass(WidgetPayload.WeekItem item, String dayLabel) {
        WidgetPayload.ClassItem cls = new WidgetPayload.ClassItem();
        cls.dayLabel = dayLabel;
        cls.start = item.start;
        cls.end = item.end;
        cls.weekday = item.weekday;
        cls.startTime = item.startTime;
        cls.endTime = item.endTime;
        cls.name = item.name;
        cls.room = item.room;
        return cls;
    }

    // "09:40" -> 580；缺失或异常时返回 MAX_VALUE（当作「还没结束」）
    private static int endMinutes(WidgetPayload.ClassItem item) {
        if (item.endTime == null || item.endTime.isEmpty()) return Integer.MAX_VALUE;
        String[] parts = item.endTime.split(":");
        if (parts.length != 2) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException error) {
            return Integer.MAX_VALUE;
        }
    }

    // "14:30" -> 870；缺失或异常时返回 MAX_VALUE（当作「还没开始」，与
    // src/widget/payload.js 的 startAt === null 分支一致，宁可显示也不跳过）
    private static int startMinutes(WidgetPayload.ClassItem item) {
        if (item.startTime == null || item.startTime.isEmpty()) return Integer.MAX_VALUE;
        String[] parts = item.startTime.split(":");
        if (parts.length != 2) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException error) {
            return Integer.MAX_VALUE;
        }
    }
}