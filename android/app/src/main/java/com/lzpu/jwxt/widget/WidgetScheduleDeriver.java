package com.lzpu.jwxt.widget;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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

        // 下一节：今天还有没结束的就取最近一节，否则往后 7 天找最近有课的一天
        for (WidgetPayload.ClassItem cls : result.today) {
            if (endMinutes(cls) >= nowMinutes) {
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
}