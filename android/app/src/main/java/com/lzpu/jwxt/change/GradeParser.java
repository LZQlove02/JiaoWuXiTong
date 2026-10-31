package com.lzpu.jwxt.change;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 正方成绩 JSON 解析。字段只取课程名、课程代码与成绩，不保留教师、学号等信息。
 */
public final class GradeParser {

    private GradeParser() {
    }

    public static List<GradeRecord> parse(String json) throws JSONException {
        String text = json == null ? "" : json.trim();
        JSONObject root = new JSONObject(text);
        JSONArray items = root.optJSONArray("items");
        if (items == null) throw new JSONException("成绩数据缺少 items");
        List<GradeRecord> records = new ArrayList<>();
        for (int i = 0; i < items.length(); i += 1) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String name = item.optString("kcmc", "").trim();
            if (name.isEmpty()) continue;
            String code = item.optString("kch", "").trim();
            String score = item.optString("cj", "").trim();
            records.add(new GradeRecord(keyOf(code, name), name, classify(score), score));
        }
        return records;
    }

    static int totalPage(String json) {
        String text = json == null ? "" : json.trim();
        try {
            JSONObject root = new JSONObject(text);
            return root.optInt("totalPage", 1);
        } catch (JSONException error) {
            return 1;
        }
    }

    static String keyOf(String code, String name) {
        String safeCode = code == null ? "" : code.trim();
        String safeName = name == null ? "" : name.trim();
        return safeCode.isEmpty() ? safeName : safeCode + "|" + safeName;
    }

    /**
     * 数值成绩 < 60 为不合格；文本只认明确的等级词。
     * 明确不合格词优先判断，避免「不合格」被「合格」误判。
     */
    static String classify(String score) {
        String text = score == null ? "" : score.trim();
        if (text.isEmpty()) return GradeRecord.STATUS_PENDING;

        if (text.matches("\\d+(?:\\.\\d+)?")) {
            try {
                double value = Double.parseDouble(text);
                return value < 60 ? GradeRecord.STATUS_FAIL : GradeRecord.STATUS_PASS;
            } catch (NumberFormatException ignored) {
                return GradeRecord.STATUS_PENDING;
            }
        }

        if (text.contains("不及格") || text.contains("不通过") || text.contains("未通过")
                || text.contains("不合格") || text.contains("未合格")
                || text.contains("缺考") || text.contains("旷考")) {
            return GradeRecord.STATUS_FAIL;
        }
        if (text.contains("合格") || text.contains("通过") || text.contains("优秀")
                || text.contains("良好") || text.contains("中等") || text.contains("及格")
                || text.contains("免修")) {
            return GradeRecord.STATUS_PASS;
        }
        return GradeRecord.STATUS_PENDING;
    }
}
