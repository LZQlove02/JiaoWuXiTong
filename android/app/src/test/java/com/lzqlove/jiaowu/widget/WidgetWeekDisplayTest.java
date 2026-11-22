package com.lzqlove.jiaowu.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 4x4 两周切换的取数与文案口径：offset 只认 0/1，其余一律当本周。
 */
public class WidgetWeekDisplayTest {

    private static WidgetPayload.WeekItem item(String name, int weekday) {
        WidgetPayload.WeekItem week = new WidgetPayload.WeekItem();
        week.weekday = weekday;
        week.start = 1;
        week.end = 2;
        week.startTime = "08:20";
        week.endTime = "09:45";
        week.name = name;
        week.room = "T2-A507";
        return week;
    }

    private static WidgetPayload payload() {
        WidgetPayload payload = new WidgetPayload();
        payload.loggedIn = true;
        payload.scheduleLoaded = true;
        payload.termWeek = 12;
        payload.nextWeekNo = 13;
        payload.week.add(item("鸿蒙开发技术", 1));
        payload.nextWeek.add(item("机器学习与深度学习", 4));
        return payload;
    }

    @Test
    public void normalizeKeepsOnlyZeroAndOne() {
        assertEquals(WidgetWeekDisplay.THIS_WEEK, WidgetWeekDisplay.normalize(-1));
        assertEquals(WidgetWeekDisplay.THIS_WEEK, WidgetWeekDisplay.normalize(2));
        assertEquals(WidgetWeekDisplay.NEXT_WEEK, WidgetWeekDisplay.normalize(1));
    }

    @Test
    public void itemsSwitchByOffset() {
        WidgetPayload payload = payload();
        assertEquals(1, WidgetWeekDisplay.items(payload, WidgetWeekDisplay.THIS_WEEK).size());
        assertEquals("鸿蒙开发技术",
            WidgetWeekDisplay.items(payload, WidgetWeekDisplay.THIS_WEEK).get(0).name);
        assertEquals("机器学习与深度学习",
            WidgetWeekDisplay.items(payload, WidgetWeekDisplay.NEXT_WEEK).get(0).name);
        assertEquals(0, WidgetWeekDisplay.items(null, WidgetWeekDisplay.NEXT_WEEK).size());
        assertEquals("鸿蒙开发技术", WidgetWeekDisplay.items(payload, 7).get(0).name);
    }

    @Test
    public void weekNoFollowsOffsetAndFallsBackToZero() {
        WidgetPayload payload = payload();
        assertEquals(12, WidgetWeekDisplay.weekNo(payload, WidgetWeekDisplay.THIS_WEEK));
        assertEquals(13, WidgetWeekDisplay.weekNo(payload, WidgetWeekDisplay.NEXT_WEEK));
        payload.nextWeekNo = 0;
        assertEquals(0, WidgetWeekDisplay.weekNo(payload, WidgetWeekDisplay.NEXT_WEEK));
        assertEquals(0, WidgetWeekDisplay.weekNo(null, WidgetWeekDisplay.THIS_WEEK));
    }

    @Test
    public void nextWeekHasNoTodayHighlight() {
        assertEquals(3, WidgetWeekDisplay.todayWeekday(WidgetWeekDisplay.THIS_WEEK, 3));
        assertEquals(0, WidgetWeekDisplay.todayWeekday(WidgetWeekDisplay.NEXT_WEEK, 3));
        assertEquals(0, WidgetWeekDisplay.todayWeekday(9, 3));
    }

    @Test
    public void onlyLoadedLoggedInPayloadCanSwitch() {
        WidgetPayload payload = payload();
        assertTrue(WidgetWeekDisplay.canSwitch(payload));

        payload.stale = true;
        assertFalse(WidgetWeekDisplay.canSwitch(payload));

        WidgetPayload loggedOut = new WidgetPayload();
        loggedOut.scheduleLoaded = true;
        assertFalse(WidgetWeekDisplay.canSwitch(loggedOut));

        WidgetPayload notLoaded = new WidgetPayload();
        notLoaded.loggedIn = true;
        assertFalse(WidgetWeekDisplay.canSwitch(notLoaded));

        assertFalse(WidgetWeekDisplay.canSwitch(null));
    }

    @Test
    public void titleAndEmptyCopyFollowOffset() {
        assertEquals(com.lzqlove.jiaowu.R.string.widget_title_week_no,
            WidgetWeekDisplay.titleRes(WidgetWeekDisplay.THIS_WEEK));
        assertEquals(com.lzqlove.jiaowu.R.string.widget_title_next_week_no,
            WidgetWeekDisplay.titleRes(WidgetWeekDisplay.NEXT_WEEK));
        assertEquals(com.lzqlove.jiaowu.R.string.widget_title_week,
            WidgetWeekDisplay.plainTitleRes(WidgetWeekDisplay.THIS_WEEK));
        assertEquals(com.lzqlove.jiaowu.R.string.widget_empty_week,
            WidgetWeekDisplay.emptyRes(WidgetWeekDisplay.THIS_WEEK));
        assertEquals(com.lzqlove.jiaowu.R.string.widget_empty_next_week,
            WidgetWeekDisplay.emptyRes(WidgetWeekDisplay.NEXT_WEEK));
        assertEquals(com.lzqlove.jiaowu.R.string.widget_switch_next_week,
            WidgetWeekDisplay.toggleRes(WidgetWeekDisplay.THIS_WEEK));
        assertEquals(com.lzqlove.jiaowu.R.string.widget_switch_this_week,
            WidgetWeekDisplay.toggleRes(WidgetWeekDisplay.NEXT_WEEK));
    }
}
