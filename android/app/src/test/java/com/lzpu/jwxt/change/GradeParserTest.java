package com.lzpu.jwxt.change;

import static org.junit.Assert.assertEquals;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;

/** 成绩状态判定：数值、等级文本、待公布与分页字段。 */
public class GradeParserTest {

    @Test
    public void classifiesScoresAndLabels() throws Exception {
        String json = "{\"items\":["
                + "{\"kch\":\"A1\",\"kcmc\":\"高等数学\",\"cj\":\"59\"},"
                + "{\"kch\":\"A2\",\"kcmc\":\"大学英语\",\"cj\":\"60\"},"
                + "{\"kch\":\"A3\",\"kcmc\":\"体育\",\"cj\":\"优秀\"},"
                + "{\"kch\":\"A4\",\"kcmc\":\"实验\",\"cj\":\"不合格\"},"
                + "{\"kch\":\"A5\",\"kcmc\":\"新课\",\"cj\":\"待公布\"},"
                + "{\"kch\":\"A6\",\"kcmc\":\"\",\"cj\":\"30\"}"
                + "]}";
        List<GradeRecord> records = GradeParser.parse(json);
        assertEquals(5, records.size());
        assertEquals(GradeRecord.STATUS_FAIL, records.get(0).status);
        assertEquals(GradeRecord.STATUS_PASS, records.get(1).status);
        assertEquals(GradeRecord.STATUS_PASS, records.get(2).status);
        assertEquals(GradeRecord.STATUS_FAIL, records.get(3).status);
        assertEquals(GradeRecord.STATUS_PENDING, records.get(4).status);
        assertEquals("A1|高等数学", records.get(0).key);
    }

    @Test
    public void failLabelsWinOverPassLabels() {
        assertEquals(GradeRecord.STATUS_FAIL, GradeParser.classify("不合格"));
        assertEquals(GradeRecord.STATUS_FAIL, GradeParser.classify("不通过"));
        assertEquals(GradeRecord.STATUS_FAIL, GradeParser.classify("未通过"));
        assertEquals(GradeRecord.STATUS_PASS, GradeParser.classify("合格"));
        assertEquals(GradeRecord.STATUS_PASS, GradeParser.classify("通过"));
        assertEquals(GradeRecord.STATUS_PENDING, GradeParser.classify("—"));
        assertEquals(GradeRecord.STATUS_PENDING, GradeParser.classify(""));
    }

    @Test
    public void readsTotalPage() throws Exception {
        assertEquals(2, GradeParser.totalPage("{\"totalPage\":2,\"items\":[]}"));
        assertEquals(1, GradeParser.totalPage("{\"items\":[]}"));
    }

    @Test(expected = JSONException.class)
    public void rejectsPayloadWithoutItems() throws Exception {
        GradeParser.parse("{\"totalPage\":1}");
    }
}
