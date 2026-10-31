package com.lzpu.jwxt.change;

import static org.junit.Assert.assertEquals;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;

/** 门户通知/消息只解析 ID，不保存标题与正文。 */
public class PortalParserTest {

    @Test
    public void parsesNoticeIds() throws Exception {
        String json = "{\"items\":["
                + "{\"xwbh\":\"N1\",\"xwbtqc\":\"通知一\"},"
                + "{\"row_id\":\"N2\",\"xwbtqc\":\"通知二\"},"
                + "{\"xwbtqc\":\"无 ID\"},"
                + "{\"xwbh\":\"N1\",\"xwbtqc\":\"重复\"}"
                + "]}";
        List<String> ids = PortalParser.parseNoticeIds(json);
        assertEquals(2, ids.size());
        assertEquals("N1", ids.get(0));
        assertEquals("N2", ids.get(1));
    }

    @Test
    public void parsesMessageIds() throws Exception {
        String json = "{\"items\":["
                + "{\"zjxx\":\"M1\",\"xxbt\":\"消息一\"},"
                + "{\"id\":\"M2\",\"xxbt\":\"消息二\"}"
                + "]}";
        List<String> ids = PortalParser.parseMessageIds(json);
        assertEquals(2, ids.size());
        assertEquals("M1", ids.get(0));
        assertEquals("M2", ids.get(1));
    }

    @Test(expected = JSONException.class)
    public void rejectsPayloadWithoutItems() throws Exception {
        PortalParser.parseNoticeIds("{\"totalResult\":2}");
    }
}
