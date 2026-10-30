package com.lzpu.jwxt.change;

import android.content.Context;
import android.util.Log;

import com.lzpu.jwxt.AuthCookieStore;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 后台检查用的课表请求：复用登录后的加密 Cookie，只 GET zfjw HTTPS。
 * 与 JwxtHttpPlugin.authGet 同一套请求头；不更新 Cookie、不写任何业务数据。
 */
final class ScheduleFetcher {

    private static final String TAG = "LzpuChange";
    private static final String HOST = "zfjw.lzzy.net";
    private static final String SCHEDULE_ENDPOINT =
            "https://zfjw.lzzy.net/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int MAX_REDIRECTS = 3;

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
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

        IOException lastError = null;
        for (int attempt = 0; attempt < 2; attempt += 1) {
            try {
                return request(url, cookie);
            } catch (IOException error) {
                lastError = error;
                if (attempt == 0) {
                    try {
                        Thread.sleep(400);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        Log.w(TAG, "课表后台检查网络失败: " + (lastError == null ? "unknown" : lastError.getMessage()));
        return Result.network();
    }

    private static Result request(HttpUrl start, String cookie) throws IOException {
        HttpUrl current = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop += 1) {
            if (!isAllowed(current)) return Result.expired();
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
                    // 被踢回 http 登录页 = 会话失效；其余跳转按失败处理，不追到别的站
                    if (!"https".equals(next.scheme()) || !HOST.equals(next.host())) {
                        return Result.expired();
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
                    boolean expired = trimmed.contains("login_slogin")
                            || trimmed.contains("统一身份认证")
                            || trimmed.contains("authserver");
                    return expired ? Result.expired() : Result.failed();
                }
                return Result.success(text);
            }
        }
        return Result.failed();
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private static boolean isAllowed(HttpUrl url) {
        return "https".equals(url.scheme())
                && url.port() == 443
                && HOST.equals(url.host());
    }
}