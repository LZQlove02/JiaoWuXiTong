package com.lzqlove.jiaowu.change;

import android.content.Context;

import com.lzqlove.jiaowu.AuthCookieStore;
import com.lzqlove.jiaowu.HttpPolicy;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 后台检查用的课表请求：复用登录后的加密 Cookie，只 GET zfjw HTTPS。
 * 与 JwxtHttpPlugin.authGet 同一套请求头与 HttpPolicy；不更新 Cookie、不写业务数据。
 *
 * 超时按广播 goAsync 的 10 秒窗口收紧，且只尝试一次：失败静默，等下一个时段。
 */
final class ScheduleFetcher {

    private static final String SCHEDULE_ENDPOINT =
            "https://zfjw.lzzy.net/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int MAX_REDIRECTS = 5;

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            // 整次调用（含重定向/DNS）硬上限，配合 request() 的总时限保证不超过 goAsync 窗口
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    private ScheduleFetcher() {
    }

    static final class Result {
        final boolean ok;
        final String body;
        final boolean sessionExpired;
        final boolean network;

        private Result(boolean ok, String body, boolean sessionExpired, boolean network) {
            this.ok = ok;
            this.body = body == null ? "" : body;
            this.sessionExpired = sessionExpired;
            this.network = network;
        }

        static Result success(String body) {
            return new Result(true, body, false, false);
        }

        static Result expired() {
            return new Result(false, "", true, false);
        }

        static Result network() {
            return new Result(false, "", false, true);
        }

        static Result failed() {
            return new Result(false, "", false, false);
        }
    }

    static Result fetch(Context context, TermInfo term) {
        String cookie = AuthCookieStore.load(context);
        if (cookie == null || cookie.isEmpty()) return Result.expired();

        HttpUrl base = HttpUrl.parse(SCHEDULE_ENDPOINT);
        if (base == null) return Result.failed();
        HttpUrl url = base.newBuilder()
                .addQueryParameter("xnm", term.xnm)
                .addQueryParameter("xqm", term.xqm)
                .build();

        try {
            return request(url, cookie);
        } catch (IOException error) {
            ChangeDebug.warn(context, "课表后台检查网络失败: " + error.getClass().getSimpleName());
            return Result.network();
        }
    }

    private static Result request(HttpUrl start, String cookie) throws IOException {
        HttpUrl current = start;
        // 重定向最多 3 跳，但总耗时不能随跳数线性放大
        long deadline = System.currentTimeMillis() + 9000;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop += 1) {
            if (System.currentTimeMillis() > deadline) return Result.failed();
            try {
                HttpPolicy.AUTH.check(current);
            } catch (IOException blocked) {
                // 初始 URL 是写死的 zfjw，策略失败只能是配置或代码问题，按失败处理
                return Result.failed();
            }
            Request request = new Request.Builder()
                    .url(current)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
                    .header("Cookie", cookie)
                    .get()
                    .build();
            try (Response response = CLIENT.newCall(request).execute()) {
                int code = response.code();
                if (isRedirect(code)) {
                    String location = response.header("Location");
                    if (location == null) return Result.failed();
                    HttpUrl next = current.resolve(location);
                    if (next == null) return Result.failed();
                    try {
                        HttpPolicy.AUTH.check(next);
                    } catch (IOException blocked) {
                        // 跳到 http（登录页）= 会话失效；其余策略失败（跨主机 / 带凭据 / 非 443）= 接口异常
                        if ("http".equals(next.scheme())) return Result.expired();
                        return Result.failed();
                    }
                    current = next;
                    continue;
                }
                if (code == 401 || code == 403) return Result.expired();
                if (code != 200) return Result.failed();
                ResponseBody body = response.body();
                String text = body == null ? "" : body.string();
                if (text.startsWith("\uFEFF")) text = text.substring(1);
                String trimmed = text.trim();
                if (trimmed.startsWith("<")) {
                    return looksLikeLoginPage(trimmed) ? Result.expired() : Result.failed();
                }
                return Result.success(text);
            }
        }
        return Result.failed();
    }

    /** 会话失效时教务系统会返回 200 + 登录页 HTML，不能只按状态码判定成功。 */
    static boolean looksLikeLoginPage(String body) {
        if (body == null) return false;
        return body.contains("login_slogin")
                || body.contains("统一身份认证")
                || body.contains("authserver");
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    /** 单测用：确认与 HttpPolicy 使用同一套主机 / 协议 / 凭据校验。 */
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