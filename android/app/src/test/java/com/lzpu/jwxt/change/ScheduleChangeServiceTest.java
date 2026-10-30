package com.lzpu.jwxt.change;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 基线决策的纯逻辑测试：Service 里最容易出错的分支在这里覆盖，
 * 不需要 Android Context。
 */
public class ScheduleChangeServiceTest {

    private static ScheduleChangeStore.Baseline baseline(String term, String owner, int sessions) {
        ScheduleChangeStore.Baseline value = new ScheduleChangeStore.Baseline();
        value.term = new TermInfo("2026", term, "2026-08-31");
        value.studentId = owner;
        for (int i = 0; i < sessions; i += 1) {
            value.sessions.add(new ScheduleSession(
                    "C" + i, "课程" + i, 1, 1, 2, new int[]{1, 2, 3}, "A101"));
        }
        value.sectionTimes = ScheduleChangeStore.defaultSectionTimes();
        return value;
    }

    @Test
    public void noBaselineResets() {
        assertEquals(ScheduleChangeService.BaselineAction.RESET,
                ScheduleChangeService.decide(null, new TermInfo("2026", "3", "2026-08-31"),
                        "20256030001", 10));
    }

    @Test
    public void termChangeResets() {
        assertEquals(ScheduleChangeService.BaselineAction.RESET,
                ScheduleChangeService.decide(baseline("3", "20256030001", 10),
                        new TermInfo("2026", "12", "2027-02-23"), "20256030001", 10));
    }

    @Test
    public void ownerChangeResets() {
        assertEquals(ScheduleChangeService.BaselineAction.RESET,
                ScheduleChangeService.decide(baseline("3", "20256030001", 10),
                        new TermInfo("2026", "3", "2026-08-31"), "20256039999", 10));
    }

    @Test
    public void emptyNewScheduleKeepsBaseline() {
        assertEquals(ScheduleChangeService.BaselineAction.SKIP_EMPTY,
                ScheduleChangeService.decide(baseline("3", "20256030001", 10),
                        new TermInfo("2026", "3", "2026-08-31"), "20256030001", 0));
    }

    @Test
    public void firstEmptyBaselineStillWrites() {
        assertEquals(ScheduleChangeService.BaselineAction.DIFF,
                ScheduleChangeService.decide(baseline("3", "20256030001", 0),
                        new TermInfo("2026", "3", "2026-08-31"), "20256030001", 0));
    }

    @Test
    public void normalTermDiffs() {
        assertEquals(ScheduleChangeService.BaselineAction.DIFF,
                ScheduleChangeService.decide(baseline("3", "20256030001", 10),
                        new TermInfo("2026", "3", "2026-08-31"), "20256030001", 10));
    }

    @Test
    public void defaultSectionTimesCoverTwelveSections() {
        assertEquals(12, ScheduleChangeStore.defaultSectionTimes().size());
        assertEquals("08:20-09:00", ScheduleChangeStore.defaultSectionTimes().get(1));
        assertEquals("21:00-21:40", ScheduleChangeStore.defaultSectionTimes().get(12));
    }
}