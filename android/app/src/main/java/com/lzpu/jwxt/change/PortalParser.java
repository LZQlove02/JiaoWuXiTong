package com.lzpu.jwxt.change;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 门户通知/消息 ID 解析。只取 ID 用于「有没有新内容」判断，不保存标题与正文。
 */
public final class PortalParser {

    private PortalParser() {
    }

    public static List<String> parseNoticeIds(String json) throws JSONException {
        return parseIds(json, "xwbh", "row_id");
    }

    public static List<String> parseMessageIds(String json) throws JSONException {
        return parseIds(json, "zjxx", "id", "w_id");
    }

    private static List<String> parseIds(String json, String... fields) throws JSONException {
        String text = json == null ? "" : json.trim();
        JSONObject root = new JSONObject(text);
        JSONArray items = root.optJSONArray("items");
        if (items == null) throw new JSONException("门户数据缺少 items");
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < items.length(); i += 1) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String id = "";
            for (String field : fields) {
                String value = item.optString(field, "").trim();
                if (!value.isEmpty()) {
                    id = value;
                    break;
                }
            }
            if (!id.isEmpty() && !ids.contains(id)) ids.add(id);
        }
        return ids;
    }
}
