package com.lzqlove.jiaowu.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

/**
 * 跨天重算口径测试：与 src/widget/payload.js 的 buildWidgetPayload / pickNextClass 对齐。
 */
public class WidgetScheduleDeriverTest {

    private static WidgetPayload.WeekItem item(int calendarWeekday, int start, int end,
                                               String startTime, String endTime, String name) {
        WidgetPayload.WeekItem week = new WidgetPayload.WeekItem();
        week.weekday = calendarWeekday == Calendar.SUNDAY ? 7 : calendarWeekday - 1;
        week.start = start;
        week.end = end;
        week.startTime = startTime;
        week.endTime = endTime;
        week.name = name;
        week.room = "T1-101";
        return week;
    }

    private static Calendar at(int calendarWeekday, int hour, int minute) {
        Calendar now = Calendar.getInstance();
        now.set(Calendar.DAY_OF_WEEK, calendarWeekday);
        now.set(Calendar.HOUR_OF_DAY, hour);
        now.set(Calendar.MINUTE, minute);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);
        return now;
    }

    private static WidgetPayload payload(WidgetPayload.WeekItem... items) {
        WidgetPayload payload = new WidgetPayload();
        payload.loggedIn = true;
        payload.scheduleLoaded = true;
        for (WidgetPayload.WeekItem item : items) payload.week.add(item);
        return payload;
    }

    @Test
    public void ongoingClassIsNotNext() {
        // 10:20：毛泽东思想 10:05-11:45 正在进行 → 不算「下一节」，今天没有未开始的课，
        // 顺延到下周一第一节；剩余节数仍把它算进去
        WidgetPayload payload = payload(
            item(Calendar.MONDAY, 1, 2, "08:00", "09:40", "高等数学"),
            item(Calendar.MONDAY, 3, 4, "10:05", "11:45", "毛泽东思想"));

        WidgetScheduleDeriver.Result result =
            WidgetScheduleDeriver.derive(payload, at(Calendar.MONDAY, 10, 20));

        assertEquals(2, result.today.size());
        assertEquals(1, result.remainingToday);
        assertEquals("高等数学", result.next.name);
        assertEquals("周一", result.next.dayLabel);
    }

    @Test
    public void pendingClassIsNextWhileAnotherIsOngoing() {
        // 08:30：高等数学 08:00-09:40 进行中，毛泽东思想 10:05 还没开始 → 后者是下一节
        WidgetPayload payload = payload(
            item(Calendar.MONDAY, 1, 2, "08:00", "09:40", "高等数学"),
            item(Calendar.MONDAY, 3, 4, "10:05", "11:45", "毛泽东思想"));

        WidgetScheduleDeriver.Result result =
            WidgetScheduleDeriver.derive(payload, at(Calendar.MONDAY, 8, 30));

        assertEquals(2, result.remainingToday);
        assertEquals("毛泽东思想", result.next.name);
        assertEquals("今天", result.next.dayLabel);
    }

    @Test
    public void firstClassFinishedPicksSecondAsNext() {
        WidgetPayload payload = payload(
            item(Calendar.MONDAY, 1, 2, "08:00", "09:40", "高等数学"),
            item(Calendar.MONDAY, 3, 4, "10:05", "11:45", "毛泽东思想"));

        WidgetScheduleDeriver.Result result =
            WidgetScheduleDeriver.derive(payload, at(Calendar.MONDAY, 10, 0));

        assertEquals(2, result.today.size());
        assertEquals(1, result.remainingToday);
        assertEquals("毛泽东思想", result.next.name);
    }

    @Test
    public void allFinishedRollsOverToTomorrow() {
        WidgetPayload payload = payload(
            item(Calendar.MONDAY, 1, 2, "08:00", "09:40", "高等数学"),
            item(Calendar.TUESDAY, 1, 2, "08:00", "09:40", "线性代数"));

        WidgetScheduleDeriver.Result result =
            WidgetScheduleDeriver.derive(payload, at(Calendar.MONDAY, 20, 0));

        assertEquals(1, result.today.size());
        assertEquals(0, result.remainingToday);
        assertEquals("线性代数", result.next.name);
        assertEquals("明天", result.next.dayLabel);
    }

    @Test
    public void noClassTodayShowsWeekdayLabel() {
        WidgetPayload payload = payload(
            item(Calendar.FRIDAY, 1, 2, "08:00", "09:40", "大学物理"));

        WidgetScheduleDeriver.Result result =
            WidgetScheduleDeriver.derive(payload, at(Calendar.TUESDAY, 9, 0));

        assertTrue(result.today.isEmpty());
        assertEquals(0, result.remainingToday);
        assertEquals("大学物理", result.next.name);
        assertEquals("周五", result.next.dayLabel);
    }

    @Test
    public void emptyWeekProducesNothing() {
        WidgetScheduleDeriver.Result result =
            WidgetScheduleDeriver.derive(payload(), at(Calendar.FRIDAY, 9, 0));

        assertTrue(result.today.isEmpty());
        assertNull(result.next);
        assertEquals(0, result.remainingToday);
    }

    // ---- 跨周陈旧检测：week[] 只代表同步时的那一周 ----

    // 固定日期，避免依赖「今天是星期几」
    private static Calendar date(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, 0);
        return calendar;
    }

    private static WidgetPayload anchored(String anchor) {
        WidgetPayload payload = payload(item(Calendar.MONDAY, 1, 2, "08:00", "09:40", "高等数学"));
        payload.weekAnchor = anchor;
        return payload;
    }

    @Test
    public void mondayOfReturnsWeekStart() {
        // 2026-10-29 是周四，本周一 2026-10-26
        assertEquals("2026-10-26", WidgetScheduleDeriver.mondayOf(date(2026, 10, 29, 9, 0)));
        // 周日仍是同一个周一
        assertEquals("2026-10-26", WidgetScheduleDeriver.mondayOf(date(2026, 11, 1, 23, 59)));
        // 周一 00:05 已经翻到新的一周
        assertEquals("2026-11-02", WidgetScheduleDeriver.mondayOf(date(2026, 11, 2, 0, 5)));
    }

    @Test
    public void sameWeekIsNotStale() {
        WidgetPayload payload = anchored("2026-10-26");
        assertFalse(WidgetScheduleDeriver.isStale(payload, date(2026, 10, 29, 0, 5)));
        assertFalse(WidgetScheduleDeriver.isStale(payload, date(2026, 11, 1, 23, 59)));
    }

    @Test
    public void nextWeekIsStale() {
        // 周日 23:59 同步的快照，周一 00:05 重算时已经是第 10 周
        WidgetPayload payload = anchored("2026-10-26");
        assertTrue(WidgetScheduleDeriver.isStale(payload, date(2026, 11, 2, 0, 5)));
    }

    @Test
    public void missingAnchorKeepsLegacyBehavior() {
        WidgetPayload payload = anchored("");
        assertFalse(WidgetScheduleDeriver.isStale(payload, date(2026, 11, 2, 0, 5)));
        assertFalse(WidgetScheduleDeriver.isStale(null, date(2026, 11, 2, 0, 5)));
    }

    // ---- 2x2 时间行的星期前缀 ----

    private static WidgetPayload.ClassItem cls(String dayLabel) {
        WidgetPayload.ClassItem item = new WidgetPayload.ClassItem();
        item.dayLabel = dayLabel;
        item.startTime = "14:30";
        item.endTime = "15:55";
        return item;
    }

    @Test
    public void dayPrefixEmptyForToday() {
        assertEquals("", WidgetScheduleDeriver.dayPrefix(cls("今天"), date(2026, 10, 30, 9, 0)));
        assertEquals("", WidgetScheduleDeriver.dayPrefix(cls(""), date(2026, 10, 30, 9, 0)));
        assertEquals("", WidgetScheduleDeriver.dayPrefix(null, date(2026, 10, 30, 9, 0)));
    }

    @Test
    public void dayPrefixPassesWeekdayThrough() {
        assertEquals("周日", WidgetScheduleDeriver.dayPrefix(cls("周日"), date(2026, 10, 30, 9, 0)));
        assertEquals("周一", WidgetScheduleDeriver.dayPrefix(cls("周一"), date(2026, 10, 30, 9, 0)));
    }

    @Test
    public void dayPrefixTurnsTomorrowIntoWeekday() {
        // 2026-10-30 是周五 → 明天周六
        assertEquals("周六", WidgetScheduleDeriver.dayPrefix(cls("明天"), date(2026, 10, 30, 9, 0)));
        // 2026-11-01 是周日 → 明天周一（跨周）
        assertEquals("周一", WidgetScheduleDeriver.dayPrefix(cls("明天"), date(2026, 11, 1, 20, 0)));
    }

    @Test
    public void dayPrefixPrefersExplicitWeekday() {
        // payload 带了 weekday 就以它为准：即便 dayLabel 是「明天」，渲染时刻跨日也不会错位
        WidgetPayload.ClassItem item = cls("明天");
        item.weekday = 3;
        assertEquals("周三", WidgetScheduleDeriver.dayPrefix(item, date(2026, 11, 1, 20, 0)));
    }

    @Test
    public void missingStartTimeIsStillNext() {
        // startTime 缺失按「还没开始」处理（与 payload.js 的 startAt === null 一致），
        // 不能被 > now 判掉
        WidgetPayload payload = payload(item(Calendar.MONDAY, 1, 2, "", "", "时间待定"));

        WidgetScheduleDeriver.Result result =
            WidgetScheduleDeriver.derive(payload, at(Calendar.MONDAY, 15, 0));

        assertEquals(1, result.today.size());
        assertEquals("时间待定", result.next.name);
        assertEquals("今天", result.next.dayLabel);
    }
}