package com.lzqlove.jiaowu.change;

import android.content.Context;

import com.lzqlove.jiaowu.AuthCookieStore;
import com.lzqlove.jiaowu.HttpPolicy;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 后台成绩抓取：复用登录后的加密 Cookie，只 POST zfjw HTTPS。
 * 与课表后台检查共用同一套主机策略与登录页判定；失败静默，等下一个时段。
 */
final class GradeFetcher {

    private static final String GRADES_ENDPOINT =
            "https://zfjw.lzzy.net/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int MAX_REDIRECTS = 5;
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 3;
    private static final MediaType FORM =
            MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    private GradeFetcher() {
    }

    static final class Result {
        final boolean ok;
        final boolean sessionExpired;
        final List<GradeRecord> records;

        private Result(boolean ok, boolean sessionExpired, List<GradeRecord> records) {
            this.ok = ok;
            this.sessionExpired = sessionExpired;
            this.records = records == null ? new ArrayList<>() : records;
        }

        static Result success(List<GradeRecord> records) {
            return new Result(true, false, records);
        }

        static Result expired() {
            return new Result(false, true, null);
        }

        static Result failed() {
            return new Result(false, false, null);
        }
    }

    static Result fetch(Context context, TermInfo term) {
        String cookie = AuthCookieStore.load(context);
        if (cookie == null || cookie.isEmpty()) return Result.expired();
        if (term == null || !term.isValid()) return Result.failed();

        List<GradeRecord> records = new ArrayList<>();
        int page = 1;
        int totalPage = 1;
        while (page <= Math.min(totalPage, MAX_PAGES)) {
            String body;
            try {
                body = requestPage(term, page, cookie);
            } catch (IOException error) {
                ChangeDebug.warn(context, "成绩后台检查网络失败: " + error.getClass().getSimpleName());
                return Result.failed();
            }
            if (body == null) {
                ChangeDebug.warn(context, "成绩后台检查返回非成绩页");
                return Result.failed();
            }
            try {
                records.addAll(GradeParser.parse(body));
                if (page == 1) totalPage = Math.max(1, GradeParser.totalPage(body));
            } catch (JSONException error) {
                ChangeDebug.warn(context, "成绩后台检查解析失败: " + error.getClass().getSimpleName());
                return Result.failed();
            }
            page += 1;
        }
        return Result.success(records);
    }

    /** 返回成绩 JSON；会话失效返回 null，由调用方静默跳过。 */
    private static String requestPage(TermInfo term, int page, String cookie) throws IOException {
        HttpUrl base = HttpUrl.parse(GRADES_ENDPOINT);
        if (base == null) return null;
        String form = "xnm=" + term.xnm
                + "&xqm=" + term.xqm
                + "&queryModel.showCount=" + PAGE_SIZE
                + "&queryModel.currentPage=" + page;
        RequestBody requestBody = RequestBody.create(form, FORM);

        Request current = new Request.Builder()
                .url(base)
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
                        // 跳到 http / 其它主机通常意味着被踢回登录页
                        return null;
                    }
                    Request.Builder builder = current.newBuilder().url(next);
                    if (code == 301 || code == 302 || code == 303) {
                        builder.get();
                    }
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
                    if (ScheduleFetcher.looksLikeLoginPage(trimmed)) return null;
                    return null;
                }
                return text;
            }
        }
        return null;
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }
}
