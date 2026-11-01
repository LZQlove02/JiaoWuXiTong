package com.lzqlove.jiaowu.change;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 成绩提醒只对「新变为不合格」的课程触发。 */
public class GradeAlertEngineTest {

    private static GradeRecord record(String key, String name, String status) {
        return new GradeRecord(key, name, status, "");
    }

    @Test
    public void ignoresExistingFailures() {
        Map<String, String> previous = new HashMap<>();
        previous.put("A|高数", GradeRecord.STATUS_FAIL);
        List<GradeRecord> current = new ArrayList<>();
        current.add(record("A|高数", "高数", GradeRecord.STATUS_FAIL));
        assertEquals(0, GradeAlertEngine.newFailures(previous, current).size());
    }

    @Test
    public void detectsNewFailureFromPendingAndPass() {
        Map<String, String> previous = new HashMap<>();
        previous.put("A|高数", GradeRecord.STATUS_PENDING);
        previous.put("B|英语", GradeRecord.STATUS_PASS);
        List<GradeRecord> current = new ArrayList<>();
        current.add(record("A|高数", "高数", GradeRecord.STATUS_FAIL));
        current.add(record("B|英语", "英语", GradeRecord.STATUS_FAIL));
        current.add(record("C|体育", "体育", GradeRecord.STATUS_PASS));
        assertEquals(2, GradeAlertEngine.newFailures(previous, current).size());
    }

    @Test
    public void treatsMissingBaselineAsNoPreviousFailure() {
        List<GradeRecord> current = new ArrayList<>();
        current.add(record("A|高数", "高数", GradeRecord.STATUS_FAIL));
        current.add(record("B|英语", "英语", GradeRecord.STATUS_PASS));
        // 调用方在首次检查会静默建基线，不会直接走这里；纯函数语义仍需正确
        assertEquals(1, GradeAlertEngine.newFailures(new HashMap<>(), current).size());
    }

    @Test
    public void statusMapKeepsLatestStatus() {
        List<GradeRecord> current = new ArrayList<>();
        current.add(record("A|高数", "高数", GradeRecord.STATUS_FAIL));
        current.add(record("A|高数", "高数", GradeRecord.STATUS_PASS));
        Map<String, String> map = GradeAlertEngine.statusMap(current);
        assertTrue(map.containsKey("A|高数"));
        assertEquals(GradeRecord.STATUS_PASS, map.get("A|高数"));
    }
}
