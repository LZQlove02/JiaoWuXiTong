package com.lzqlove.jiaowu;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import okhttp3.HttpUrl;

/**
 * 「查看通知原文」必须能打开教务系统链接，同时不能变成任意 Intent 跳板。
 * 这里只测纯函数判定；真正 launch(Intent) 依赖 Android 运行时，不在 JVM 单测范围。
 */
public class SupportBridgeUrlTest {

    private static boolean allowed(String url) {
        return SupportBridgePlugin.isAllowedExternalUrl(HttpUrl.get(url));
    }

    @Test
    public void allowsSchoolWebsite() {
        assertTrue(allowed("https://www.lzpu.edu.cn/jwysxglc/tzgg"));
    }

    @Test
    public void allowsAcademicAffairsNoticePage() {
        assertTrue(allowed("https://zfjw.lzzy.net/xtgl/xwck_ckXw.html?xwbh=20260101"));
    }

    @Test
    public void rejectsPlainHttp() {
        assertFalse(allowed("http://zfjw.lzzy.net/xtgl/xwck_ckXw.html"));
    }

    @Test
    public void rejectsUnlistedHost() {
        assertFalse(allowed("https://evil.example.com/notice"));
    }

    @Test
    public void rejectsNonStandardPort() {
        assertFalse(allowed("https://zfjw.lzzy.net:8443/notice"));
    }

    @Test
    public void rejectsCredentialsInUrl() {
        assertFalse(allowed("https://user:pass@zfjw.lzzy.net/notice"));
    }

    @Test
    public void rejectsNullUrl() {
        assertFalse(SupportBridgePlugin.isAllowedExternalUrl(null));
    }
}
