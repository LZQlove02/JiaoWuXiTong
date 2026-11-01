package com.lzqlove.jiaowu;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * GitHub Release 的最小解析结果。只做字段读取，解析失败一律抛错，不做猜测。
 * 保持纯 Java，便于在 JVM 单测中直接覆盖。
 */
final class UpdateInfo {

    final String tag;
    final String name;
    final String notes;
    final String url;
    final String publishedAt;

    private UpdateInfo(String tag, String name, String notes, String url, String publishedAt) {
        this.tag = tag;
        this.name = name;
        this.notes = notes;
        this.url = url;
        this.publishedAt = publishedAt;
    }

    static UpdateInfo parse(String json) throws JSONException {
        if (json == null || json.trim().isEmpty()) {
            throw new JSONException("响应为空");
        }
        JSONObject object = new JSONObject(json);
        String tag = object.optString("tag_name", "").trim();
        if (tag.isEmpty()) {
            throw new JSONException("缺少 tag_name");
        }
        String url = object.optString("html_url", "").trim();
        if (!url.startsWith("https://github.com/")) {
            url = "";
        }
        return new UpdateInfo(
                tag,
                object.optString("name", tag).trim(),
                object.optString("body", ""),
                url,
                object.optString("published_at", "").trim());
    }
}
