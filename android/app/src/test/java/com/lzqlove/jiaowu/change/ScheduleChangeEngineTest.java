package com.lzqlove.jiaowu.change;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 判定矩阵：停课 / 调课 / 新增 / 换教室 / 本周过滤 / 已过时段 / 首次基线。 */
public class ScheduleChangeEngineTest {

    private static final TermInfo TERM = new TermInfo("2026", "3", "2026-08-31");
    // 2026-10-30 是第 9 周周五 12:00
    private static final java.util.Calendar NOW = at(2026, 10, 30, 12, 0);

    private static java.util.Calendar at(int year, int month, int day, int hour, int minute) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(year, month - 1, day, hour, minute, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar;
    }

    private static ScheduleSession session(String id, String name, int weekday,
                                           int start, int end, String weeks, String room) {
        return new ScheduleSession(id, name, weekday, start, end,
                ScheduleParser.parseWeeks(weeks), room);
    }

    private static Map<Integer, String> times() {
        Map<Integer, String> map = new LinkedHashMap<>();
        map.put(1, "08:20-09:00");
        map.put(2, "09:05-09:45");
        map.put(3, "10:05-10:45");
        map.put(4, "10:50-11:30");
        map.put(5, "11:35-12:15");
        map.put(6, "14:30-15:10");
        map.put(7, "15:15-15:55");
        map.put(8, "16:15-16:55");
        map.put(9, "17:00-17:40");
        map.put(10, "19:30-20:10");
        map.put(11, "20:15-20:55");
        map.put(12, "21:00-21:40");
        return map;
    }

    private static List<ChangeEntry> diff(List<ScheduleSession> oldSessions,
                                          List<ScheduleSession> newSessions) {
        return ScheduleChangeEngine.diff(oldSessions, newSessions, TERM, times(), NOW, "foreground");
    }

    @Test
    public void detectsFutureCancel() {
        List<ScheduleSession> before = new ArrayList<>();
        before.add(session("C1", "高等数学", 5, 10, 11, "1-16周", "A101"));
        before.add(session("C2", "大学英语", 5, 12, 12, "1-16周", "B203"));
        List<ScheduleSession> after = new ArrayList<>();
        after.add(session("C2", "大学英语", 5, 12, 12, "1-16周", "B203"));

        List<ChangeEntry> entries = diff(before, after);
        assertEquals(1, entries.size());
        ChangeEntry entry = entries.get(0);
        assertEquals(ChangeEntry.TYPE_CANCEL, entry.type);
        assertEquals("C1", entry.courseId);
        assertEquals(5, entry.from.weekday);
        assertEquals("1-16周", entry.from.weeksLabel);
        assertFalse(entry.quiet);
        assertTrue(entry.id.startsWith("c_"));
    }

    @Test
    public void passedClassIsRecordedAsQuiet() {
        List<ScheduleSession> before = new ArrayList<>();
        before.add(session("C1", "高等数学", 1, 3, 4, "1-16周", "A101"));
        before.add(session("C2", "大学英语", 5, 12, 12, "1-16周", "B203"));
        List<ScheduleSession> after = new ArrayList<>();
        after.add(session("C2", "大学英语", 5, 12, 12, "1-16周", "B203"));

        List<ChangeEntry> entries = diff(before, after);
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).quiet);
    }

    @Test
    public void detectsAdd() {
        List<ScheduleSession> before = new ArrayList<>();
        before.add(session("C1", "高等数学", 1, 3, 4, "1-16周", "A101"));
        List<ScheduleSession> after = new ArrayList<>(before);
        after.add(session("C2", "大学英语", 5, 10, 11, "1-16周", "B203"));

        List<ChangeEntry> entries = diff(before, after);
        assertEquals(1, entries.size());
        assertEquals(ChangeEntry.TYPE_ADD, entries.get(0).type);
        assertEquals("C2", entries.get(0).courseId);
        assertEquals(5, entries.get(0).to.weekday);
        assertFalse(entries.get(0).quiet);
    }

    @Test
    public void detectsMoveToAnotherDay() {
        List<ScheduleSession> before = Collections.singletonList(
                session("C1", "高等数学", 1, 3, 4, "1-16周", "A101"));
        List<ScheduleSession> after = Collections.singletonList(
                session("C1", "高等数学", 5, 10, 11, "1-16周", "A101"));

        List<ChangeEntry> entries = diff(before, after);
        assertEquals(1, entries.size());
        ChangeEntry entry = entries.get(0);
        assertEquals(ChangeEntry.TYPE_MOVE, entry.type);
        assertEquals(1, entry.from.weekday);
        assertEquals(5, entry.to.weekday);
        assertFalse(entry.quiet);
    }

    @Test
    public void weekOnlyChangeIsMoveNotCancelAndAdd() {
        List<ScheduleSession> before = Collections.singletonList(
                session("C1", "高等数学", 2, 3, 4, "1-16周", "A101"));
        List<ScheduleSession> after = Collections.singletonList(
                session("C1", "高等数学", 2, 3, 4, "9-16周", "A101"));

        List<ChangeEntry> entries = diff(before, after);
        assertEquals(1, entries.size());
        ChangeEntry entry = entries.get(0);
        assertEquals(ChangeEntry.TYPE_MOVE, entry.type);
        assertEquals("1-16周", entry.from.weeksLabel);
        assertEquals("9-16周", entry.to.weeksLabel);
    }

    @Test
    public void roomOnlyChangeIsRecordedWithoutNotification() {
        List<ScheduleSession> before = Collections.singletonList(
                session("C1", "高等数学", 5, 10, 11, "1-16周", "A101"));
        List<ScheduleSession> after = Collections.singletonList(
                session("C1", "高等数学", 5, 10, 11, "1-16周", "B203"));

        List<ChangeEntry> entries = diff(before, after);
        assertEquals(1, entries.size());
        ChangeEntry entry = entries.get(0);
        assertEquals(ChangeEntry.TYPE_ROOM, entry.type);
        assertEquals("A101", entry.from.room);
        assertEquals("B203", entry.to.room);
        assertFalse(entry.isRemindable());
    }

    @Test
    public void nextWeekOnlyChangeIsIgnored() {
        List<ScheduleSession> before = new ArrayList<>();
        before.add(session("C1", "高等数学", 5, 10, 11, "10周", "A101"));
        before.add(session("C2", "大学英语", 1, 1, 2, "1-16周", "B203"));
        List<ScheduleSession> after = new ArrayList<>();
        after.add(session("C2", "大学英语", 1, 1, 2, "1-16周", "B203"));

        assertEquals(0, diff(before, after).size());
    }

    @Test
    public void timeAndWeeksChangedTogetherIsMove() {
        List<ScheduleSession> before = Collections.singletonList(
                session("C1", "高等数学", 1, 3, 4, "1-8周", "A101"));
        List<ScheduleSession> after = Collections.singletonList(
                session("C1", "高等数学", 5, 10, 11, "9-16周", "A101"));

        List<ChangeEntry> entries = diff(before, after);
        assertEquals(1, entries.size());
        assertEquals(ChangeEntry.TYPE_MOVE, entries.get(0).type);
        assertFalse(entries.get(0).quiet);
    }

    @Test
    public void multipleSessionsOfSameCoursePairByDistance() {
        List<ScheduleSession> before = new ArrayList<>();
        before.add(session("C1", "高等数学", 1, 3, 4, "1-16周", "A101"));
        before.add(session("C1", "高等数学", 3, 5, 6, "1-16周", "A101"));
        List<ScheduleSession> after = new ArrayList<>();
        after.add(session("C1", "高等数学", 1, 3, 4, "1-16周", "A101"));
        after.add(session("C1", "高等数学", 4, 7, 8, "1-16周", "A101"));

        List<ChangeEntry> entries = diff(before, after);
        assertEquals(1, entries.size());
        assertEquals(ChangeEntry.TYPE_MOVE, entries.get(0).type);
        assertEquals(3, entries.get(0).from.weekday);
        assertEquals(4, entries.get(0).to.weekday);
    }

    @Test
    public void emptyBaselineProducesNothing() {
        List<ScheduleSession> after = Collections.singletonList(
                session("C1", "高等数学", 1, 3, 4, "1-16周", "A101"));
        assertEquals(0, diff(new ArrayList<>(), after).size());
    }

    @Test
    public void emptyNewListProducesNothingToAvoidMassCancel() {
        List<ScheduleSession> before = Collections.singletonList(
                session("C1", "高等数学", 1, 3, 4, "1-16周", "A101"));
        assertEquals(0, diff(before, new ArrayList<>()).size());
    }

    @Test
    public void idsAreStableAcrossRuns() {
        List<ScheduleSession> before = new ArrayList<>();
        before.add(session("C1", "高等数学", 1, 3, 4, "1-16周", "A101"));
        before.add(session("C2", "大学英语", 5, 12, 12, "1-16周", "B203"));
        List<ScheduleSession> after = new ArrayList<>();
        after.add(session("C2", "大学英语", 5, 12, 12, "1-16周", "B203"));

        List<ChangeEntry> first = diff(before, after);
        List<ChangeEntry> second = diff(before, after);
        assertEquals(first.get(0).id, second.get(0).id);
        assertNotNull(first.get(0).detectedAt);
    }

    @Test
    public void currentWeekUsesTermStartDate() {
        assertEquals(9, DateUtils.currentWeek("2026-08-31", NOW));
        // 开学前按第 1 周
        assertEquals(1, DateUtils.currentWeek("2026-09-07", at(2026, 8, 31, 9, 0)));
        // 超出 20 周钳制
        assertEquals(20, DateUtils.currentWeek("2026-01-05", NOW));
    }

    @Test
    public void nonMondayTermStartStillUsesRealWeekdayForQuiet() {
        // 2025-08-31 是周日：不能把开学日当成周一，否则周一的变更当天就被判已上完
        TermInfo term = new TermInfo("2025", "3", "2025-08-31");
        java.util.Calendar mondayMorning = at(2025, 9, 1, 8, 0);
        List<ScheduleSession> before = new ArrayList<>();
        before.add(session("C1", "高等数学", 1, 3, 4, "1-16周", "A101"));
        before.add(session("C2", "大学英语", 5, 12, 12, "1-16周", "B203"));
        List<ScheduleSession> after = new ArrayList<>();
        after.add(session("C2", "大学英语", 5, 12, 12, "1-16周", "B203"));

        List<ChangeEntry> entries = ScheduleChangeEngine.diff(
                before, after, term, times(), mondayMorning, "foreground");
        assertEquals(1, entries.size());
        assertFalse(entries.get(0).quiet);
    }

    @Test
    public void tuesdayTermStartKeepsMondayClassInAppWeekBlock() {
        // 2027-02-23 是周二：第 3 周从 03-09 起算，周一的课应在 03-15，不能在 03-09 被判已上完
        TermInfo term = new TermInfo("2026", "12", "2027-02-23");
        java.util.Calendar tuesdayMorning = at(2027, 3, 9, 8, 0);
        List<ScheduleSession> before = new ArrayList<>();
        before.add(session("C1", "高等数学", 1, 3, 4, "1-16周", "A101"));
        before.add(session("C2", "大学英语", 2, 1, 2, "1-16周", "B203"));
        List<ScheduleSession> after = new ArrayList<>();
        after.add(session("C2", "大学英语", 2, 1, 2, "1-16周", "B203"));

        List<ChangeEntry> entries = ScheduleChangeEngine.diff(
                before, after, term, times(), tuesdayMorning, "foreground");
        assertEquals(1, entries.size());
        assertFalse(entries.get(0).quiet);
    }
}