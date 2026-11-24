package com.lzqlove.jiaowu;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

/** 后台换票的纯解析函数：票据名值、Set-Cookie 首段、日志路径脱敏与 Cookie 头拼接。 */
public class SsoSilentRenewerTest {

    @Test
    public void ticketNameFallsBackToCastgc() {
        assertEquals("CASTGC", SsoSilentRenewer.ticketName(null));
        assertEquals("CASTGC", SsoSilentRenewer.ticketName(""));
        assertEquals("CASTGC", SsoSilentRenewer.ticketName("=only-value"));
        assertEquals("CASTGC", SsoSilentRenewer.ticketName("CASTGC"));
        assertEquals("CASTGC", SsoSilentRenewer.ticketName("CASTGC=ticket-value"));
        // 学校有些部署把票据叫 TGC，回放时必须原样带名字
        assertEquals("TGC", SsoSilentRenewer.ticketName("TGC=ticket-value"));
        assertEquals("CASTGC", SsoSilentRenewer.ticketName(" CASTGC = ticket-value "));
    }

    @Test
    public void ticketValueKeepsValueOnly() {
        assertEquals("", SsoSilentRenewer.ticketValue(null));
        assertEquals("ticket-value", SsoSilentRenewer.ticketValue("CASTGC=ticket-value"));
        assertEquals("ticket-value", SsoSilentRenewer.ticketValue("TGC = ticket-value "));
        // 兼容历史版本只存了值、没有 name= 前缀的情况
        assertEquals("plain-ticket", SsoSilentRenewer.ticketValue("plain-ticket"));
    }

    @Test
    public void setCookiePairDropsAttributes() {
        assertEquals("JSESSIONID=abc123",
                SsoSilentRenewer.setCookiePair("JSESSIONID=abc123; Path=/xtgl; HttpOnly"));
        assertEquals("CASTGC=xyz",
                SsoSilentRenewer.setCookiePair("CASTGC=xyz;Path=/authserver;Secure"));
        assertEquals("", SsoSilentRenewer.setCookiePair(null));
        assertEquals("", SsoSilentRenewer.setCookiePair(""));
        assertEquals("", SsoSilentRenewer.setCookiePair("=value-without-name"));
        assertEquals("", SsoSilentRenewer.setCookiePair("name-without-value="));
    }

    @Test
    public void sanitizePathRemovesQueryAndMatrixParameters() {
        assertEquals("/xtgl/index_initMenu.html",
                SsoSilentRenewer.sanitizePath("/xtgl/index_initMenu.html?jsdm=xs&_t=1"));
        // CAS 会往重定向里塞 ;jsessionid=…，日志里不能带出来
        assertEquals("/sso/jziotlogin",
                SsoSilentRenewer.sanitizePath("/sso/jziotlogin;jsessionid=E2B7CEED7DFE7C71172FC4817B9F5FFC"));
        assertEquals("/", SsoSilentRenewer.sanitizePath("/;jsessionid=E2B7"));
        assertEquals("/xtgl/login_slogin.html",
                SsoSilentRenewer.sanitizePath("/xtgl/login_slogin.html?x=1;jsessionid=E2B7"));
        assertEquals("/", SsoSilentRenewer.sanitizePath(null));
        assertEquals("/", SsoSilentRenewer.sanitizePath(""));
    }

    @Test
    public void cookieHeaderJoinsPairsInIterationOrder() {
        Map<String, String> jar = new LinkedHashMap<>();
        jar.put("CASTGC", "ticket-value");
        jar.put("_shmat", "device");
        assertEquals("CASTGC=ticket-value; _shmat=device", SsoSilentRenewer.cookieHeader(jar));
        assertEquals("", SsoSilentRenewer.cookieHeader(new LinkedHashMap<>()));
    }
}
