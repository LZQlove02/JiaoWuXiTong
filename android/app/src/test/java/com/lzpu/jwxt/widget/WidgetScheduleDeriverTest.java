package com.lzpu.jwxt.widget;

import static org.junit.Assert.assertEquals;
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
    public void ongoingSecondClassKeepsOneRemaining() {
        WidgetPayload payload = payload(
            item(Calendar.MONDAY, 1, 2, "08:00", "09:40", "高等数学"),
            item(Calendar.MONDAY, 3, 4, "10:05", "11:45", "毛泽东思想"));

        WidgetScheduleDeriver.Result result =
            WidgetScheduleDeriver.derive(payload, at(Calendar.MONDAY, 10, 20));

        assertEquals(2, result.today.size());
        assertEquals(1, result.remainingToday);
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
}