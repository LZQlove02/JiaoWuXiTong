package com.lzqlove.jiaowu.widget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * 「更新于」相对时间口径测试：与 docs/plan/小组件执行方案.md §7.3 的规则表对齐。
 */
public class WidgetUpdatedLabelTest {

    private static Calendar at(int year, int month, int day, int hour, int minute) {
        return new GregorianCalendar(year, month - 1, day, hour, minute, 0);
    }

    @Test
    public void missingDateFallsBackToClock() {
        WidgetUpdatedLabel.Label label = WidgetUpdatedLabel.parse(null, "19:58", at(2026, 11, 22, 20, 9));
        assertEquals(WidgetUpdatedLabel.KIND_UNKNOWN, label.kind);
        assertEquals("19:58", label.clock);
    }

    @Test
    public void emptyClockProducesEmptyLabel() {
        assertEquals(WidgetUpdatedLabel.KIND_UNKNOWN,
            WidgetUpdatedLabel.parse("2026-11-22", "", at(2026, 11, 22, 20, 9)).kind);
        assertEquals("", WidgetUpdatedLabel.text(null, (WidgetUpdatedLabel.Label) null));
        assertEquals("", WidgetUpdatedLabel.parse(null, null, null).clock);
    }

    @Test
    public void invalidClockFallsBackToRawValue() {
        WidgetUpdatedLabel.Label label = WidgetUpdatedLabel.parse("2026-11-22", "8:05", at(2026, 11, 22, 20, 9));
        assertEquals(WidgetUpdatedLabel.KIND_UNKNOWN, label.kind);
        assertEquals("8:05", label.clock);
    }

    @Test
    public void underOneMinuteIsJustNow() {
        assertEquals(WidgetUpdatedLabel.KIND_JUST_NOW,
            WidgetUpdatedLabel.parse("2026-11-22", "20:08", at(2026, 11, 22, 20, 9)).kind);
    }

    @Test
    public void futureTimestampIsJustNowNotNegative() {
        WidgetUpdatedLabel.Label label = WidgetUpdatedLabel.parse("2026-11-22", "21:30", at(2026, 11, 22, 20, 9));
        assertEquals(WidgetUpdatedLabel.KIND_JUST_NOW, label.kind);
    }

    @Test
    public void underOneHourCountsMinutes() {
        WidgetUpdatedLabel.Label label = WidgetUpdatedLabel.parse("2026-11-22", "19:58", at(2026, 11, 22, 20, 9));
        assertEquals(WidgetUpdatedLabel.KIND_MINUTES, label.kind);
        assertEquals(11, label.minutes);
    }

    @Test
    public void sameDayAfterOneHourShowsClock() {
        WidgetUpdatedLabel.Label label = WidgetUpdatedLabel.parse("2026-11-22", "08:20", at(2026, 11, 22, 20, 9));
        assertEquals(WidgetUpdatedLabel.KIND_TODAY, label.kind);
        assertEquals("08:20", label.clock);
    }

    @Test
    public void yesterdayShowsYesterday() {
        assertEquals(WidgetUpdatedLabel.KIND_YESTERDAY,
            WidgetUpdatedLabel.parse("2026-11-21", "19:58", at(2026, 11, 22, 20, 9)).kind);
    }

    @Test
    public void earlierThisYearShowsMonthAndDay() {
        WidgetUpdatedLabel.Label label = WidgetUpdatedLabel.parse("2026-03-02", "09:20", at(2026, 11, 22, 20, 9));
        assertEquals(WidgetUpdatedLabel.KIND_DATE, label.kind);
        assertEquals(3, label.month);
        assertEquals(2, label.day);
        assertEquals("09:20", label.clock);
    }

    @Test
    public void lastYearCarriesTheYear() {
        WidgetUpdatedLabel.Label label = WidgetUpdatedLabel.parse("2025-12-31", "23:50", at(2026, 1, 1, 9, 0));
        assertEquals(WidgetUpdatedLabel.KIND_DATE_YEAR, label.kind);
        assertEquals(2025, label.year);
        assertEquals(12, label.month);
        assertEquals(31, label.day);
    }

    @Test
    public void minutesAreNotRoundedUp() {
        WidgetUpdatedLabel.Label label = WidgetUpdatedLabel.parse("2026-11-22", "19:00", at(2026, 11, 22, 19, 59));
        assertEquals(WidgetUpdatedLabel.KIND_MINUTES, label.kind);
        assertEquals(59, label.minutes);
    }

    @Test
    public void exactlyOneHourIsNotMinutes() {
        assertEquals(WidgetUpdatedLabel.KIND_TODAY,
            WidgetUpdatedLabel.parse("2026-11-22", "19:00", at(2026, 11, 22, 20, 0)).kind);
    }

    @Test
    public void invalidDateFallsBackToUnknown() {
        WidgetUpdatedLabel.Label label = WidgetUpdatedLabel.parse("2026-13-45", "19:58", at(2026, 11, 22, 20, 9));
        assertEquals(WidgetUpdatedLabel.KIND_UNKNOWN, label.kind);
        assertEquals("19:58", label.clock);
    }

    @Test
    public void midnightYesterdayCountsAsMinutes() {
        // 23:58 抓的，00:03 刷新：差值 5 分钟比「昨天 23:58」更有用
        WidgetUpdatedLabel.Label label = WidgetUpdatedLabel.parse("2026-11-21", "23:58", at(2026, 11, 22, 0, 3));
        assertEquals(WidgetUpdatedLabel.KIND_MINUTES, label.kind);
        assertEquals(5, label.minutes);
    }
}
