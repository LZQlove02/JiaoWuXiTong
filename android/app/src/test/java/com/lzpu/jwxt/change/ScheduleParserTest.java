package com.lzpu.jwxt.change;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 解析器必须与 src/api/zf.js 的 mapSchedule / parseWeeks / parseSections / cleanRoom 对齐。
 * fixture 与 JS 端 tests/scheduleParserParity.test.js 共用同一份文件。
 */
public class ScheduleParserTest {

    private static JSONObject fixture() throws Exception {
        InputStream input = ScheduleParserTest.class.getResourceAsStream("/schedule-kbList.json");
        assertNotNull("缺少共享 fixture：tests/fixtures/schedule-kbList.json", input);
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return new JSONObject(builder.toString());
    }

    @Test
    public void matchesSharedFixture() throws Exception {
        JSONObject root = fixture();
        List<ScheduleSession> sessions = ScheduleParser.parse(root);
        JSONArray expected = root.getJSONArray("expectedSessions");
        assertEquals(expected.length(), sessions.size());
        for (int i = 0; i < expected.length(); i += 1) {
            JSONObject item = expected.getJSONObject(i);
            ScheduleSession session = sessions.get(i);
            assertEquals(item.getString("courseId"), session.courseId);
            assertEquals(item.getString("courseName"), session.courseName);
            assertEquals(item.getInt("weekday"), session.weekday);
            assertEquals(item.getInt("startSection"), session.startSection);
            assertEquals(item.getInt("endSection"), session.endSection);
            assertEquals(item.getString("room"), session.room);
            JSONArray weeks = item.getJSONArray("weeks");
            assertEquals(weeks.length(), session.weeks.length);
            for (int w = 0; w < weeks.length(); w += 1) {
                assertEquals(weeks.getInt(w), session.weeks[w]);
            }
        }
    }

    @Test
    public void parsesAndDedupesKbList() throws Exception {
        List<ScheduleSession> sessions = ScheduleParser.parse(fixture());
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
        List<ScheduleSession> sessions = ScheduleParser.parse(fixture());
        ScheduleSession english = sessions.get(1);
        assertEquals(3, english.weekday);
        assertEquals(5, english.startSection);
        assertEquals(5, english.endSection);
        assertEquals(8, english.weeks.length);
        assertEquals("1-15周(单)", english.weeksLabel());
    }

    @Test
    public void missingWeekdayFallsBackToOneAndEmptyWeeksToSixteen() throws Exception {
        List<ScheduleSession> sessions = ScheduleParser.parse(fixture());
        ScheduleSession physics = sessions.get(3);
        assertEquals("B005", physics.courseId);
        assertEquals(1, physics.weekday);
        assertEquals(16, physics.weeks.length);
        assertEquals(1, physics.weeks[0]);
        assertEquals(16, physics.weeks[15]);
    }

    @Test
    public void weeksLabelKeepsMixedSequencesHonest() {
        ScheduleSession mixed = new ScheduleSession(
                "C1", "高等数学", 1, 3, 4,
                new int[]{1, 3, 5, 6, 7, 8}, "A101");
        assertEquals("1,3,5-8周", mixed.weeksLabel());

        ScheduleSession odd = new ScheduleSession(
                "C2", "大学英语", 3, 5, 6,
                new int[]{1, 3, 5, 7, 9, 11, 13, 15}, "B203");
        assertEquals("1-15周(单)", odd.weeksLabel());

        ScheduleSession even = new ScheduleSession(
                "C3", "体育", 5, 7, 8,
                new int[]{2, 4, 6}, "操场");
        assertEquals("2-6周(双)", even.weeksLabel());

        ScheduleSession none = new ScheduleSession("C4", "空周次", 1, 1, 2, new int[0], "");
        assertEquals("", none.weeksLabel());
        assertFalse(none.containsWeek(1));
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
        // 与 JS 一致：自相矛盾的「2周(单)」过滤后为空，不再兜底成 1-16 周
        assertEquals(0, ScheduleParser.parseWeeks("2周(单)").length);
        // 「单周」没有数字：先按 1-16 周兜底，再筛单周
        assertEquals(8, ScheduleParser.parseWeeks("单周").length);
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