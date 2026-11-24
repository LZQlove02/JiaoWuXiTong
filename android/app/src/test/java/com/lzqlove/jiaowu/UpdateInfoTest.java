package com.lzqlove.jiaowu;

import static org.junit.Assert.assertEquals;

import org.json.JSONException;
import org.junit.Test;

public class UpdateInfoTest {

    @Test
    public void parsesAllFields() throws Exception {
        String json = "{"
                + "\"tag_name\":\"v1.3.0\","
                + "\"name\":\"教务助手 v1.3.0\","
                + "\"body\":\"- 首启免责声明\\n- 修复会话失效\","
                + "\"html_url\":\"https://github.com/LZQlove02/JiaoWuXiTong/releases/tag/v1.3.0\","
                + "\"published_at\":\"2026-10-30T12:00:00Z\""
                + "}";
        UpdateInfo info = UpdateInfo.parse(json);
        assertEquals("v1.3.0", info.tag);
        assertEquals("教务助手 v1.3.0", info.name);
        assertEquals("- 首启免责声明\n- 修复会话失效", info.notes);
        assertEquals("https://github.com/LZQlove02/JiaoWuXiTong/releases/tag/v1.3.0", info.url);
        assertEquals("2026-10-30T12:00:00Z", info.publishedAt);
    }

    @Test
    public void fallsBackForOptionalFields() throws Exception {
        UpdateInfo info = UpdateInfo.parse("{\"tag_name\":\"v1.3.0\"}");
        assertEquals("v1.3.0", info.name);
        assertEquals("", info.notes);
        assertEquals("", info.publishedAt);
        assertEquals("", info.url);
    }

    @Test
    public void dropsNonGithubUrl() throws Exception {
        UpdateInfo info = UpdateInfo.parse(
                "{\"tag_name\":\"v1.3.0\",\"html_url\":\"http://evil.example.com/release\"}");
        assertEquals("", info.url);
    }

    @Test(expected = JSONException.class)
    public void rejectsMissingTag() throws Exception {
        UpdateInfo.parse("{\"name\":\"v1.3.0\"}");
    }

    @Test(expected = JSONException.class)
    public void rejectsBlankTag() throws Exception {
        UpdateInfo.parse("{\"tag_name\":\"   \"}");
    }

    @Test(expected = JSONException.class)
    public void rejectsEmptyResponse() throws Exception {
        UpdateInfo.parse("");
    }

    @Test(expected = JSONException.class)
    public void rejectsInvalidJson() throws Exception {
        UpdateInfo.parse("not-json");
    }
}
