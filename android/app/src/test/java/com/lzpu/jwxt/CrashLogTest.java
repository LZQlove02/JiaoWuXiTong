package com.lzpu.jwxt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CrashLogTest {

    @Test
    public void summarizeKeepsHeaderAndLimitedFrames() {
        StringBuilder text = new StringBuilder(
                "时间：2026-10-31 23:00\n应用：v1.3.5\n异常：java.lang.RuntimeException: boom\n堆栈：\n");
        for (int i = 1; i <= 20; i++) {
            text.append("\tat com.lzpu.jwxt.A.b(A.java:").append(i).append(")\n");
        }

        String summary = CrashLog.summarize(text.toString());
        assertTrue(summary.contains("时间：2026-10-31 23:00"));
        assertTrue(summary.contains("异常：java.lang.RuntimeException: boom"));
        assertTrue(summary.contains("A.java:12"));
        assertFalse(summary.contains("A.java:13"));
    }

    @Test
    public void sanitizeHidesLinksAndSecrets() {
        String raw = "异常：GET https://zfjw.lzzy.net/x Cookie=SESSID123 token=abc\n"
                + "Authorization: Bearer xyz\n"
                + "id=3BD1C87FAB6AF093E065FCFCFED77A02";
        String clean = CrashLog.sanitize(raw);

        assertTrue(clean.contains("[已隐藏链接]"));
        assertTrue(clean.contains("Cookie=[已隐藏]"));
        assertTrue(clean.contains("Authorization=[已隐藏]"));
        assertFalse(clean.contains("zfjw"));
        assertFalse(clean.contains("SESSID123"));
        assertFalse(clean.contains("abc"));
        assertFalse(clean.contains("xyz"));
        assertFalse(clean.contains("3BD1C87FAB6AF093E065FCFCFED77A02"));
    }
}
