package com.lzqlove.jiaowu;

import android.webkit.CookieManager;

/** 统一认证/教务 Cookie 的清理边界：保留 CASTGC 与设备信任，避免重复设备校验。 */
final class SsoCookieCleaner {

    private static final String AUTH_SERVER_URL = "https://authserver.lzzy.net";
    /**
     * CookieManager.getCookie 只返回路径匹配的 Cookie；
     * CAS 的 CASTGC 常带 Path=/authserver，用站点根地址读取会漏掉。
     */
    private static final String AUTH_SERVER_LOGIN_URL =
            "https://authserver.lzzy.net/authserver/login";
    private static final String ZFJW_URL = "https://zfjw.lzzy.net";
    private static final String[] AUTH_PATHS = {"/", "/authserver"};
    private static final String[] ZFJW_PATHS = {"/", "/xtgl", "/sso"};
    private static final String[] TRANSIENT = {
            "JSESSIONID", "SESSION", "XSRF-TOKEN",
            "challenge_cookie", "_shmas", "_upstream_id"
    };
    private static final String[] SESSION_TICKETS = {"CASTGC", "TGC"};

    private SsoCookieCleaner() {
    }

    /** 登录结束/取消：清一次性挑战与 HTTP 会话，保留 CASTGC 与 `_shmat` 设备信任。 */
    static void expireTransient(CookieManager manager) {
        expireNames(manager, AUTH_SERVER_URL, TRANSIENT, AUTH_PATHS);
        // CASTGC 有效期由学校控制，应用只保留、不重写；只清较老的 TGC 名称
        expireNames(manager, AUTH_SERVER_URL, new String[]{"TGC"}, AUTH_PATHS);
    }

    /**
     * 只读探测是否仍持有统一认证票据（CASTGC/TGC），不返回票据值。
     * 用于静默重登前的预判：没有票据时不拉起 WebView，避免无意义闪屏。
     */
    static boolean hasCastgc(CookieManager manager) {
        String raw = manager.getCookie(AUTH_SERVER_LOGIN_URL);
        if (raw == null || raw.isEmpty()) raw = manager.getCookie(AUTH_SERVER_URL);
        if (raw == null) return false;
        for (String part : raw.split(";")) {
            String pair = part.trim();
            int index = pair.indexOf('=');
            if (index <= 0) continue;
            String name = pair.substring(0, index).trim();
            if (!"CASTGC".equals(name) && !"TGC".equals(name)) continue;
            if (!pair.substring(index + 1).trim().isEmpty()) return true;
        }
        return false;
    }

    /** 明确退出登录：清掉 CASTGC/TGC，保证可切换账号。 */
    static void expireSsoSession(CookieManager manager) {
        expireNames(manager, AUTH_SERVER_URL, TRANSIENT, AUTH_PATHS);
        expireNames(manager, AUTH_SERVER_URL, SESSION_TICKETS, AUTH_PATHS);
    }

    /** 教务系统会话整体作废。 */
    static void expireZfjw(CookieManager manager) {
        String raw = manager.getCookie(ZFJW_URL);
        if (raw == null) return;
        for (String part : raw.split(";")) {
            int index = part.indexOf('=');
            if (index <= 0) continue;
            String name = part.substring(0, index).trim();
            if (name.isEmpty()) continue;
            expireNames(manager, ZFJW_URL, new String[]{name}, ZFJW_PATHS);
        }
    }

    private static void expireNames(CookieManager manager, String url,
                                    String[] names, String[] paths) {
        for (String name : names) {
            for (String path : paths) {
                manager.setCookie(url, name + "=; Max-Age=0; Path=" + path + "; Secure");
            }
        }
    }
}
