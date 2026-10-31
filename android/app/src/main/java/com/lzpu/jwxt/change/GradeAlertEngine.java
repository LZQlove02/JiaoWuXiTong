package com.lzpu.jwxt.change;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 成绩提醒的纯判定逻辑，便于单测：把课程状态压平成 map，并找出「新变为不合格」的课程。
 */
public final class GradeAlertEngine {

    private GradeAlertEngine() {
    }

    public static Map<String, String> statusMap(List<GradeRecord> records) {
        Map<String, String> map = new HashMap<>();
        if (records == null) return map;
        for (GradeRecord record : records) {
            if (record == null || record.key.isEmpty()) continue;
            map.put(record.key, record.status);
        }
        return map;
    }

    /**
     * 只返回基线中不是 fail、当前为 fail 的课程。
     * 首次基线由调用方静默写入，不会走这里，因此不会补发历史不及格。
     */
    public static List<GradeRecord> newFailures(Map<String, String> previous,
                                                List<GradeRecord> current) {
        List<GradeRecord> result = new ArrayList<>();
        if (current == null) return result;
        Map<String, String> baseline = previous == null ? new HashMap<>() : previous;
        for (GradeRecord record : current) {
            if (record == null || !record.isFailed()) continue;
            String old = baseline.get(record.key);
            if (GradeRecord.STATUS_FAIL.equals(old)) continue;
            result.add(record);
        }
        return result;
    }
}
