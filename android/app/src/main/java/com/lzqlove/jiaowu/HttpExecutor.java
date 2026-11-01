package com.lzqlove.jiaowu;

import java.io.IOException;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 共用的手动重定向实现：每一跳都校验协议、端口与允许主机，超过上限直接失败。
 * 教务请求与公开更新检查都复用这一份逻辑，避免出现第二套安全策略。
 */
final class HttpExecutor {

    /** 会话 Cookie 的回写接口；公开请求传 null。 */
    interface CookieHandler {
        String header();

        void onResponseHeaders(List<String> setCookies);
    }

    /** 调试日志钩子；生产环境传 null。 */
    interface Observer {
        void onRequest(Request request);

        void onResponse(Response response);

        void onRedirect(int code, HttpUrl next);
    }

    private HttpExecutor() {
    }

    static Response execute(OkHttpClient client, Request request, HttpPolicy policy,
                            int maxRedirects, CookieHandler cookies, Observer observer,
                            boolean keepCookies) throws IOException {
        Request current = request;
        for (int i = 0; i <= maxRedirects; i++) {
            policy.check(current.url());
            if (observer != null) {
                observer.onRequest(current);
            }
            Response response = client.newCall(current).execute();
            if (observer != null) {
                observer.onResponse(response);
            }
            if (keepCookies && cookies != null) {
                cookies.onResponseHeaders(response.headers("Set-Cookie"));
            }
            if (!isRedirect(response.code())) {
                return response;
            }

            HttpUrl next;
            int code = response.code();
            try {
                next = policy.resolveRedirect(current.url(), response.header("Location"));
            } finally {
                response.close();
            }
            if (observer != null) {
                observer.onRedirect(code, next);
            }
            // 301/302/303 按 RFC 改为 GET 并丢弃请求体，避免把表单数据重放到新地址
            Request.Builder nextBuilder = current.newBuilder().url(next);
            if (code == 301 || code == 302 || code == 303) {
                nextBuilder.get();
            }
            if (keepCookies && cookies != null) {
                String cookie = cookies.header();
                if (cookie != null && !cookie.isEmpty()) {
                    nextBuilder.header("Cookie", cookie);
                }
            }
            current = nextBuilder.build();
        }
        throw new IOException("重定向次数过多");
    }

    static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }
}
