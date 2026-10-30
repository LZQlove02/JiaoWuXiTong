package com.lzpu.jwxt.change;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 登录页判定与网络策略：不触碰网络，只测纯判断。 */
public class ScheduleFetcherTest {

    @Test
    public void detectsLoginPageByKnownMarkers() {
        assertTrue(ScheduleFetcher.looksLikeLoginPage("<html>login_slogin</html>"));
        assertTrue(ScheduleFetcher.looksLikeLoginPage("<form>统一身份认证</form>"));
        assertTrue(ScheduleFetcher.looksLikeLoginPage("<script src=\"authserver/login\"></script>"));
        assertFalse(ScheduleFetcher.looksLikeLoginPage("<html>课表页面</html>"));
        assertFalse(ScheduleFetcher.looksLikeLoginPage(""));
        assertFalse(ScheduleFetcher.looksLikeLoginPage(null));
    }

    @Test
    public void onlyAuthenticatedHttpsHostIsAllowed() {
        assertTrue(ScheduleFetcher.canRequest(
                "https://zfjw.lzzy.net/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151"));
        assertFalse(ScheduleFetcher.canRequest("http://zfjw.lzzy.net/kbcx/xskbcx_cxXsKb.html"));
        assertFalse(ScheduleFetcher.canRequest("https://zfjw.lzzy.net:8443/kbcx/xskbcx_cxXsKb.html"));
        assertFalse(ScheduleFetcher.canRequest("https://example.com/kbcx/xskbcx_cxXsKb.html"));
        assertFalse(ScheduleFetcher.canRequest("https://user:pass@zfjw.lzzy.net/kbcx/xskbcx_cxXsKb.html"));
        assertFalse(ScheduleFetcher.canRequest("not a url"));
    }
}