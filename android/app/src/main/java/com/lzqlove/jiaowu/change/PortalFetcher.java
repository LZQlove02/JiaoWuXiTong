package com.lzqlove.jiaowu.change;

import android.content.Context;

import com.lzqlove.jiaowu.AuthCookieStore;
import com.lzqlove.jiaowu.HttpPolicy;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 门户通知/消息后台抓取：复用登录 Cookie，只 POST zfjw；失败静默。
 */
final class PortalFetcher {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int MAX_REDIRECTS = 5;
    private static final MediaType FORM =
            MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    private PortalFetcher() {
    }

    static String fetch(Context context, String url, String body) {
        String cookie = AuthCookieStore.load(context);
        if (cookie == null || cookie.isEmpty()) return null;

        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) return null;
        RequestBody requestBody = RequestBody.create(body == null ? "" : body, FORM);
        Request current = new Request.Builder()
                .url(parsed)
                .post(requestBody)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Cookie", cookie)
                .build();

        long deadline = System.currentTimeMillis() + 9000;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop += 1) {
            if (System.currentTimeMillis() > deadline) return null;
            try {
                HttpPolicy.AUTH.check(current.url());
            } catch (IOException blocked) {
                return null;
            }
            try (Response response = CLIENT.newCall(current).execute()) {
                int code = response.code();
                if (isRedirect(code)) {
                    String location = response.header("Location");
                    if (location == null) return null;
                    HttpUrl next = current.url().resolve(location);
                    if (next == null) return null;
                    try {
                        HttpPolicy.AUTH.check(next);
                    } catch (IOException blocked) {
                        return null;
                    }
                    Request.Builder builder = current.newBuilder().url(next);
                    if (code == 301 || code == 302 || code == 303) builder.get();
                    builder.header("Cookie", cookie);
                    current = builder.build();
                    continue;
                }
                if (code == 401 || code == 403) return null;
                if (code != 200) return null;
                ResponseBody responseBody = response.body();
                String text = responseBody == null ? "" : responseBody.string();
                if (text.startsWith("\uFEFF")) text = text.substring(1);
                String trimmed = text.trim();
                if (trimmed.startsWith("<")) {
                    if (ScheduleFetcher.looksLikeLoginPage(trimmed)) {
                        ChangeDebug.warn(context, "门户后台检查会话失效");
                    }
                    return null;
                }
                return text;
            } catch (IOException error) {
                ChangeDebug.warn(context, "门户后台检查网络失败: "
                        + error.getClass().getSimpleName());
                return null;
            }
        }
        return null;
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }
}
