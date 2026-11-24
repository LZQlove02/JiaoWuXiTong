package com.lzqlove.jiaowu.keepalive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 保活请求的纯判断：登录页识别、网络策略与 30 分钟调度间隔。 */
public class SessionKeepAliveTest {

    @Test
    public void detectsLoginPageByKnownMarkers() {
        assertTrue(SessionKeepAliveService.looksLikeLoginPage("<html>login_slogin</html>"));
        assertTrue(SessionKeepAliveService.looksLikeLoginPage("<input name=\"yhm\">"));
        // 正常业务页可能含「统一身份认证」或 authserver 链接，不能据此判定失效
        assertFalse(SessionKeepAliveService.looksLikeLoginPage("<form>统一身份认证</form>"));
        assertFalse(SessionKeepAliveService.looksLikeLoginPage("<script src=\"authserver/login\"></script>"));
        assertFalse(SessionKeepAliveService.looksLikeLoginPage("{\"items\":[]}"));
        assertFalse(SessionKeepAliveService.looksLikeLoginPage("<html>菜单</html>"));
        assertFalse(SessionKeepAliveService.looksLikeLoginPage(""));
        assertFalse(SessionKeepAliveService.looksLikeLoginPage(null));
    }

    @Test
    public void onlyAuthenticatedHttpsHostIsAllowed() {
        assertTrue(SessionKeepAliveService.canRequest(
                "https://zfjw.lzzy.net/xtgl/index_cxDbsy.html?doType=query"));
        assertFalse(SessionKeepAliveService.canRequest(
                "http://zfjw.lzzy.net/xtgl/index_cxDbsy.html?doType=query"));
        assertFalse(SessionKeepAliveService.canRequest(
                "https://zfjw.lzzy.net:8443/xtgl/index_cxDbsy.html?doType=query"));
        assertFalse(SessionKeepAliveService.canRequest(
                "https://example.com/xtgl/index_cxDbsy.html?doType=query"));
        assertFalse(SessionKeepAliveService.canRequest(
                "https://user:pass@zfjw.lzzy.net/xtgl/index_cxDbsy.html?doType=query"));
        assertFalse(SessionKeepAliveService.canRequest("not a url"));
    }

    @Test
    public void schedulesNextTriggerThirtyMinutesLater() {
        long now = 1762056000000L;
        assertEquals(now + 30L * 60 * 1000, SessionKeepAliveScheduler.nextTrigger(now));
    }

    @Test
    public void nextTriggerUsesProvidedNowAndStaysInTheFuture() {
        // 单次闹钟链按传入时刻推导，不依赖 System.currentTimeMillis，便于单测
        long now = 1000L;
        long next = SessionKeepAliveScheduler.nextTrigger(now);
        assertTrue(next > now);
        assertEquals(now + SessionKeepAliveScheduler.INTERVAL_MS, next);
    }

    @Test
    public void schedulesOnlyWhenSwitchIsOnAndSessionIsStored() {
        // 本地没有会话时排出来的闹钟只会立刻判定失效并把自己取消，
        // 设置页却会显示一个并不存在的「下次保活」，所以排程条件是两者同时成立
        assertTrue(SessionKeepAliveScheduler.shouldSchedule(true, true));
        assertFalse(SessionKeepAliveScheduler.shouldSchedule(true, false));
        assertFalse(SessionKeepAliveScheduler.shouldSchedule(false, true));
        assertFalse(SessionKeepAliveScheduler.shouldSchedule(false, false));
    }
}
