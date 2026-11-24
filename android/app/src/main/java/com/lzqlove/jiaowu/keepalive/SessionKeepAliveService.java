package com.lzqlove.jiaowu.keepalive;

import android.content.Context;

import com.lzqlove.jiaowu.AuthCookieStore;
import com.lzqlove.jiaowu.DebugLog;
import com.lzqlove.jiaowu.HttpPolicy;
import com.lzqlove.jiaowu.SsoSilentRenewer;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 后台会话保活：用本机加密保存的会话 Cookie 查询一次教务系统的轻量 JSON 接口，
 * 让服务端会话保持活跃，降低空闲约 3 小时后的重新登录与短信验证概率。
 *
 * 只访问 zfjw HTTPS，不解析、不保存响应内容；明确判定会话失效时才停止闹钟。
 */
public final class SessionKeepAliveService {

    static final String ENDPOINT =
            "https://zfjw.lzzy.net/xtgl/index_cxDbsy.html?doType=query";
    private static final String QUERY_BODY =
            "flag=0&sfyy=1&queryModel.showCount=1&queryModel.currentPage=1";
    private static final RequestBody QUERY = RequestBody.create(
            QUERY_BODY,
            MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8"));
    private static final String AUTH_HOST = "authserver.lzzy.net";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int MAX_REDIRECTS = 4;
    private static final long DEADLINE_MS = 9000;

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    /** 回前台补保活的最小间隔：比 30 分钟闹钟略小，留出一次检查本身的耗时余量。 */
    public static final long TOP_UP_MIN_INTERVAL_MS = 25L * 60 * 1000;

    public enum Result {
        ALIVE,
        EXPIRED,
        FAILED
    }

    private SessionKeepAliveService() {
    }

    static Result checkNow(Context context) {
        // 先记开始时间：一次请求最长约 9 秒，记录「发起检查」的时刻更符合展示语义
        long checkedAt = System.currentTimeMillis();
        Result result = runCheck(context);
        if (result == Result.EXPIRED && AuthCookieStore.hasStoredCookie(context)) {
            // 失效不等于没救：统一认证票据还在的话，先用它换一份新会话。
            // 换票成功时 renew 已经拿到教务首页 200 与新的会话 Cookie，这里不再二次查询：
            // goAsync 建议窗口约 10 秒，检查最长 9 秒、换票最长 6 秒，最坏约 15 秒；
            // 并发由 SsoSilentRenewer 内部串行化，真的超时被杀也只是本次未记结果，下次重试。
            SsoSilentRenewer.Result renewed = SsoSilentRenewer.renew(context);
            DebugLog.i("JiaowuKeepAlive", "静默续期 " + renewed);
            if (renewed == SsoSilentRenewer.Result.OK) {
                result = Result.ALIVE;
            }
        }
        // 统一在出口记账：Cookie 为空、网络异常、正常判定等所有返回分支都不会漏记，
        // 以后新增分支也不需要再改这里
        SessionKeepAliveStore.recordCheckResult(context, checkedAt, result.name());
        return result;
    }

    /**
     * 只有距上次检查超过阈值才值得在回前台时补一次；lastCheckAt 为 0 表示从未检查过。
     * 处于失效退避（上次判定 EXPIRED）时不补：否则每次回前台都会绕过 3 小时退避重新探活，
     * 既浪费请求，也会把下一次闹钟不断往后推，退避形同虚设。
     */
    public static boolean shouldTopUp(long now, long lastCheckAt, boolean backoff, long thresholdMs) {
        if (backoff) return false;
        if (lastCheckAt <= 0) return true;
        return now - lastCheckAt >= thresholdMs;
    }

    /**
     * 前台补保活：用户回到 App 且距上次检查已超过阈值时立刻补一次，
     * 会话在后台失效的话在这里先换票，随后的数据请求就能直接用新会话。
     * 顺序与闹钟一致：先续上闹钟链防止中途被杀断链，检查完再按最终退避状态重排
     * （换票成功会把退避清掉，必须重排回 30 分钟，不能沿用检查前的 3 小时）。
     */
    public static Result checkNowAndReschedule(Context context) {
        Context app = context.getApplicationContext();
        SessionKeepAliveScheduler.scheduleNext(app);
        Result result = checkNow(app);
        SessionKeepAliveScheduler.scheduleNext(app);
        return result;
    }

    private static Result runCheck(Context context) {
        String cookie = AuthCookieStore.load(context);
        if (cookie == null || cookie.isEmpty()) {
            // 区分两种 null：本机确实没有会话 → 取消闹钟；
            // Keystore 临时不可用导致解密失败 → 保留闹钟链，下一个周期重试。
            // 小米等 ROM 在深睡时偶发 Keystore 不可用，误判会永久砍断保活。
            if (!AuthCookieStore.hasStoredCookie(context)) {
                SessionKeepAliveScheduler.cancel(context);
                return Result.EXPIRED;
            }
            return Result.FAILED;
        }
        HttpUrl start = HttpUrl.parse(ENDPOINT);
        if (start == null) return Result.FAILED;
        try {
            // 会话失效也保留闹钟链：等用户下次打开 App 静默换票成功后，
            // setAuthCookies 会重新排程；避免服务器一次临时跳登录页就永久停掉保活。
            // 明确退出登录 / 清空数据时由 clearAuthCookies 取消闹钟。
            return request(context, start, cookie);
        } catch (IOException error) {
            // 网络不可达时保留闹钟链，下一个周期再试
            return Result.FAILED;
        }
    }

    private static Result request(Context context, HttpUrl start, String cookie) throws IOException {
        HttpUrl current = start;
        boolean usePost = true;
        long deadline = System.currentTimeMillis() + DEADLINE_MS;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop += 1) {
            if (System.currentTimeMillis() > deadline) return Result.FAILED;
            try {
                HttpPolicy.AUTH.check(current);
            } catch (IOException blocked) {
                return Result.FAILED;
            }
            Request.Builder builder = new Request.Builder()
                    .url(current)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
                    .header("Cookie", cookie);
            if (usePost) {
                builder.post(QUERY).header("X-Requested-With", "XMLHttpRequest");
            } else {
                builder.get();
            }
            try (Response response = CLIENT.newCall(builder.build()).execute()) {
                int code = response.code();
                if (isRedirect(code)) {
                    String location = response.header("Location");
                    if (location == null) return Result.FAILED;
                    HttpUrl next = current.resolve(location);
                    if (next == null) return Result.FAILED;
                    // 会话失效时教务系统会跳到统一认证或明文登录页
                    if ("http".equals(next.scheme()) || AUTH_HOST.equals(next.host())) {
                        return Result.EXPIRED;
                    }
                    try {
                        HttpPolicy.AUTH.check(next);
                    } catch (IOException blocked) {
                        return Result.FAILED;
                    }
                    // 301/302/303 转为 GET；307/308 保留原请求方法
                    if (code != 307 && code != 308) usePost = false;
                    current = next;
                    continue;
                }
                if (code == 401 || code == 403) return Result.EXPIRED;
                if (code != 200) return Result.FAILED;
                String sample = response.body() == null
                        ? "" : response.peekBody(4096).string();
                String trimmed = sample.trim();
                if (trimmed.startsWith("\uFEFF")) trimmed = trimmed.substring(1).trim();
                if (trimmed.startsWith("<")) {
                    if (looksLikeLoginPage(trimmed)) return Result.EXPIRED;
                    // 非 JSON、也非明确登录页，按接口异常处理，保留闹钟
                    return Result.FAILED;
                }
                AuthCookieStore.mergeSetCookies(context, response.headers("Set-Cookie"));
                return Result.ALIVE;
            }
        }
        return Result.FAILED;
    }

    /**
     * 只认明确的本地/统一认证登录页特征，避免正常业务页含「统一身份认证」
     * 或 authserver 链接时被误判为会话失效。
     */
    static boolean looksLikeLoginPage(String body) {
        if (body == null) return false;
        return body.contains("login_slogin")
                || body.contains("name=\"yhm\"");
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    /** 单测用：确认保活请求与 HttpPolicy 使用同一套主机 / 协议 / 凭据校验。 */
    static boolean canRequest(String url) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) return false;
        try {
            HttpPolicy.AUTH.check(parsed);
            return true;
        } catch (IOException error) {
            return false;
        }
    }
}
