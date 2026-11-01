package com.lzqlove.jiaowu.change;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

/** 提醒时间解析、间隔校验与下一次触发计算。 */
public class CheckTimesTest {

    @Test
    public void parsesDefaults() {
        CheckTimes times = CheckTimes.parse("07:10", "12:50");
        assertNotNull(times);
        assertTrue(times.isValid());
        assertEquals(7 * 60 + 10, times.morningMinutes);
        assertEquals(12 * 60 + 50, times.noonMinutes);
    }

    @Test
    public void acceptsSingleDigitHourAndNormalizes() {
        CheckTimes times = CheckTimes.parse("7:05", "12:50");
        assertNotNull(times);
        assertEquals("07:05", times.morningText());
        assertEquals("12:50", times.noonText());
    }

    @Test
    public void rejectsInvalidFormatAndRange() {
        assertNull(CheckTimes.parse("7", "12:50"));
        assertNull(CheckTimes.parse("24:00", "12:50"));
        assertNull(CheckTimes.parse("07:60", "12:50"));
        assertFalse(CheckTimes.isValid("07:1"));
    }

    @Test
    public void requiresAtLeastOneHourGap() {
        assertTrue(CheckTimes.parse("07:10", "08:10").isValid());
        assertFalse(CheckTimes.parse("07:10", "08:09").isValid());
        assertFalse(CheckTimes.parse("12:50", "07:10").isValid());
    }

    @Test
    public void nextTriggerUsesConfiguredTimeAndRollsToTomorrow() {
        CheckTimes times = CheckTimes.parse("07:10", "12:50");
        long before = at(2026, 10, 31, 6, 0).getTimeInMillis();
        long after = at(2026, 10, 31, 13, 0).getTimeInMillis();

        assertEquals(at(2026, 10, 31, 7, 10).getTimeInMillis(),
                ScheduleCheckScheduler.nextTrigger(1, before, times));
        assertEquals(at(2026, 11, 1, 7, 10).getTimeInMillis(),
                ScheduleCheckScheduler.nextTrigger(1, after, times));
        assertEquals(at(2026, 10, 31, 12, 50).getTimeInMillis(),
                ScheduleCheckScheduler.nextTrigger(2, before, times));
    }

    private static Calendar at(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month - 1, day, hour, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }
}
