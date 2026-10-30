package com.lzpu.jwxt.change;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

/** 解析器必须与 src/api/zf.js 的 mapSchedule / parseWeeks / parseSections / cleanRoom 对齐。 */
public class ScheduleParserTest {

    private static final String KB_LIST = "{\"kbList\":["
            + "{\"kch\":\"B001\",\"kcmc\":\"高等数学\",\"xqj\":\"1\",\"jcs\":\"3-4节\",\"zcd\":\"1-16周\",\"cdmc\":\"官塘校区A101(80人)\"},"
            + "{\"kch\":\"B002\",\"kcmc\":\"大学英语\",\"xqj\":\"3\",\"jcs\":\"5\",\"zcd\":\"1-16周(单)\",\"cdmc\":\"B203\"},"
            + "{\"kch\":\"B003\",\"kcmc\":\"体育\",\"xqj\":\"5\",\"jcs\":\"7-8\",\"zcd\":\"1-8周\",\"cdmc\":\"操场\"},"
            + "{\"kch\":\"B003\",\"kcmc\":\"体育\",\"xqj\":\"5\",\"jcs\":\"7-8\",\"zcd\":\"1-8周\",\"cdmc\":\"操场\"},"
            + "{\"kch\":\"B004\",\"kcmc\":\"\",\"xqj\":\"2\",\"jcs\":\"1-2\",\"zcd\":\"1-4周\",\"cdmc\":\"C1\"},"
            + "{\"kch\":\"B005\",\"kcmc\":\"物理\",\"jcs\":\"1-2\",\"zcd\":\"\",\"cdmc\":\"C2\"}"
            + "]}";

    @Test
    public void parsesAndDedupesKbList() throws Exception {
        List<ScheduleSession> sessions = ScheduleParser.parse(new JSONObject(KB_LIST));
        assertEquals(4, sessions.size());

        ScheduleSession math = sessions.get(0);
        assertEquals("B001", math.courseId);
        assertEquals("高等数学", math.courseName);
        assertEquals(1, math.weekday);
        assertEquals(3, math.startSection);
        assertEquals(4, math.endSection);
        assertEquals("官塘校区A101", math.room);
        assertEquals("1-16周", math.weeksLabel());
    }

    @Test
    public void oddWeeksAndSingleSection() throws Exception {
        List<ScheduleSession> sessions = ScheduleParser.parse(new JSONObject(KB_LIST));
        ScheduleSession english = sessions.get(1);
        assertEquals(3, english.weekday);
        assertEquals(5, english.startSection);
        assertEquals(5, english.endSection);
        assertEquals(8, english.weeks.length);
        assertEquals("1-15周(单)", english.weeksLabel());
    }

    @Test
    public void missingWeekdayFallsBackToOneAndEmptyWeeksToSixteen() throws Exception {
        List<ScheduleSession> sessions = ScheduleParser.parse(new JSONObject(KB_LIST));
        ScheduleSession physics = sessions.get(3);
        assertEquals("B005", physics.courseId);
        assertEquals(1, physics.weekday);
        assertEquals(16, physics.weeks.length);
        assertEquals(1, physics.weeks[0]);
        assertEquals(16, physics.weeks[15]);
    }

    @Test
    public void roomCountSuffixIsRemoved() {
        assertEquals("A101", ScheduleParser.cleanRoom("A101(80人)"));
        // 与 JS 正则 `\(\d+\s*人\)` 一致：括号后先有空格时不清洗
        assertEquals("A101( 120 人)", ScheduleParser.cleanRoom(" A101( 120 人) "));
        // 全角括号沿用 JS 现状：不清洗
        assertEquals("A101（80人）", ScheduleParser.cleanRoom("A101（80人）"));
    }

    @Test
    public void sectionAndWeekParsingMatchKnownInputs() {
        assertEquals(3, ScheduleParser.parseSections("3-4节")[0]);
        assertEquals(4, ScheduleParser.parseSections("3-4节")[1]);
        assertEquals(7, ScheduleParser.parseSections("7")[0]);
        assertEquals(1, ScheduleParser.parseSections("")[0]);

        assertEquals(16, ScheduleParser.parseWeeks("1-16周").length);
        assertEquals(2, ScheduleParser.parseWeeks("2-4周(双)").length);
        assertEquals(8, ScheduleParser.parseWeeks("1-16周(单)").length);
        assertEquals(16, ScheduleParser.parseWeeks("").length);
        // 与 JS 现有行为一致：逗号枚举只识别到最后一个「N周」
        assertEquals(1, ScheduleParser.parseWeeks("1,3,5周").length);
        assertEquals(5, ScheduleParser.parseWeeks("1,3,5周")[0]);
    }

    @Test
    public void missingKbListReturnsEmpty() throws Exception {
        assertEquals(0, ScheduleParser.parse(new JSONObject("{}")).size());
        assertEquals(0, ScheduleParser.parse(new JSONObject("{\"kbList\":[]}")).size());
    }
}