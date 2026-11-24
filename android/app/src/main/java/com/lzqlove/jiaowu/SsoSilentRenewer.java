package com.lzqlove.jiaowu;

import android.content.Context;
import android.webkit.CookieManager;

import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 后台静默续期：保活判定会话失效时，用统一认证票据（CASTGC 或 TGC）重放一次 CAS 服务跳转，
 * 换一份新的教务会话 Cookie。不接触账号密码，也不启动任何界面。
 *
 * 只在后台线程调用，调用方是广播接收器的 goAsync 线程；只访问 authserver 与 zfjw 两个主机。
 */
public final class SsoSilentRenewer {

    private static final String TAG = "SsoRenew";
    private static final String AUTH_HOST = "authserver.lzzy.net";
    private static final String ZFJW_HOST = "zfjw.lzzy.net";
    private static final String LOGIN_URL =
            "https://authserver.lzzy.net/authserver/login"
                    + "?service=http%3A%2F%2Fzfjw.lzzy.net%2Fsso%2Fjziotlogin";
    private static final String AUTH_LOGIN_URL =
            "https://authserver.lzzy.net/authserver/login";
    private static final String AUTH_COOKIE_URL = "https://authserver.lzzy.net";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int MAX_HOPS = 8;
    /**
     * 广播接收器的 goAsync 建议窗口约 10 秒：保活检查最长 9 秒，续期最多只留 6 秒。
     * CAS 换票正常只需 1–2 跳，这个上限只用于兜住异常跳转链。
     */
    private static final long DEADLINE_MS = 6000;

    /** 串行化整个换票事务：闹钟、回前台补保活可能同时触发，不能各自轮换票据。 */
    private static final Object RENEW_LOCK = new Object();

    public enum Result {
        OK,
        NO_TICKET,
        LOGIN_FORM,
        FAILED
    }

    /** authserver：固定指纹 + 有效期校验；系统链校验能过时同时要求通过。 */
    private static final OkHttpClient AUTH_CLIENT = buildAuthClient();
    /** zfjw：优先系统校验；系统信任库过旧（Android 6/7 缺 Let's Encrypt 根）时退回指纹 + 有效期。 */
    private static final OkHttpClient ZFJW_CLIENT = buildZfjwClient();

    private SsoSilentRenewer() {
    }

    public static Result renew(Context context) {
        // 同一时刻只跑一次换票事务：并发时后到的一路等前一路结束，
        // 避免两路各自拿到轮换票据、由后写回的旧值覆盖新值。
        synchronized (RENEW_LOCK) {
            return renewLocked(context);
        }
    }

    private static Result renewLocked(Context context) {
        if (!BuildConfig.SSO_RENEW_ENABLED) {
            // 发布回退开关：关闭后与 2.2.2 行为一致（失效即退避），不访问 authserver
            DebugLog.i(TAG, "disabled by build");
            return Result.FAILED;
        }
        String stored = SsoTicketStore.load(context);
        if (stored.isEmpty()) stored = readTicketFromWebView();
        if (stored.isEmpty()) {
            DebugLog.i(TAG, "no ticket");
            return Result.NO_TICKET;
        }
        // 学校 CAS 的票据 Cookie 可能叫 CASTGC 也可能叫 TGC，按登录时记下的名字原样回放
        String ticketName = ticketName(stored);
        String ticketValue = ticketValue(stored);
        if (ticketValue.isEmpty()) return Result.NO_TICKET;
        Map<String, String> authCookies = new LinkedHashMap<>();
        authCookies.put(ticketName, ticketValue);
        Map<String, String> zfjwCookies = new LinkedHashMap<>();
        List<String> zfjwSetCookies = new ArrayList<>();

        HttpUrl current = HttpUrl.parse(LOGIN_URL);
        if (current == null) return fail("bad-login-url");
        long deadline = System.currentTimeMillis() + DEADLINE_MS;
        for (int hop = 0; hop < MAX_HOPS; hop += 1) {
            if (System.currentTimeMillis() > deadline) return fail("deadline");
            HttpUrl requestUrl = useHttps(current);
            if (!"https".equals(requestUrl.scheme())) return fail("scheme");
            String host = requestUrl.host();
            if (!AUTH_HOST.equals(host) && !ZFJW_HOST.equals(host)) return fail("host");
            Request.Builder builder = new Request.Builder()
                    .url(requestUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Cache-Control", "no-cache");
            String cookieHeader = cookieHeader(AUTH_HOST.equals(host) ? authCookies : zfjwCookies);
            if (!cookieHeader.isEmpty()) builder.header("Cookie", cookieHeader);
            OkHttpClient client = AUTH_HOST.equals(host) ? AUTH_CLIENT : ZFJW_CLIENT;
            try (Response response = client.newCall(builder.build()).execute()) {
                int code = response.code();
                // 只记录主机与路径；jsessionid 之类的矩阵参数不进日志
                DebugLog.i(TAG, "hop " + hop + " " + code + " " + host
                        + sanitizePath(requestUrl.encodedPath()));
                collect(response.headers("Set-Cookie"), host, authCookies, zfjwCookies, zfjwSetCookies);
                if (isRedirect(code)) {
                    String location = response.header("Location");
                    if (location == null) return fail("no-location");
                    HttpUrl next = requestUrl.resolve(location);
                    if (next == null) return fail("bad-location");
                    if (!AUTH_HOST.equals(next.host()) && !ZFJW_HOST.equals(next.host())) {
                        return fail("redirect-host");
                    }
                    if (AUTH_HOST.equals(next.host())
                            && next.encodedPath().contains("/authserver/login")) {
                        // 票据失效时 CAS 会把人送回登录表单：副本已无用，清掉避免每 3 小时重试死票
                        return loginForm(context);
                    }
                    current = next;
                    continue;
                }
                if (AUTH_HOST.equals(host)) {
                    // authserver 返回 200 说明停在登录页，票据已不可用
                    return loginForm(context);
                }
                if (code != 200) return fail("code " + code);
                String path = requestUrl.encodedPath();
                if (ZFJW_HOST.equals(host) && path.startsWith("/xtgl/")
                        && !path.contains("login_slogin")) {
                    if (zfjwSetCookies.isEmpty()) return fail("no-cookie");
                    AuthCookieStore.mergeSetCookies(context, zfjwSetCookies);
                    // CAS 换票时可能同时轮换票据：把新值写回副本，下一次续期才不会用到旧票
                    String rotated = ticketFrom(authCookies);
                    if (!rotated.isEmpty()) SsoTicketStore.save(context, rotated);
                    DebugLog.i(TAG, "ok, cookies=" + zfjwSetCookies.size());
                    return Result.OK;
                }
                String body = response.body() == null ? "" : response.peekBody(4096).string();
                if (body.contains("login_slogin") || body.contains("name=\"yhm\"")) {
                    return loginForm(context);
                }
                return fail("unexpected-200");
            } catch (IOException error) {
                return fail(error.getClass().getSimpleName());
            }
        }
        return fail("too-many-hops");
    }

    private static Result loginForm(Context context) {
        // 票据确定失效：清掉加密副本；WebView Cookie 里的票据由登录页自行处理
        SsoTicketStore.clear(context);
        return Result.LOGIN_FORM;
    }

    private static HttpUrl useHttps(HttpUrl url) {
        // CAS 给的服务地址是 http；浏览器环境下会自动升级，OkHttp 需要手动改写
        if ("http".equals(url.scheme()) && ZFJW_HOST.equals(url.host())) {
            return url.newBuilder().scheme("https").build();
        }
        return url;
    }

    private static Result fail(String reason) {
        DebugLog.i(TAG, "failed: " + reason);
        return Result.FAILED;
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    static String ticketName(String stored) {
        if (stored == null) return "CASTGC";
        int separator = stored.indexOf('=');
        if (separator <= 0) return "CASTGC";
        String name = stored.substring(0, separator).trim();
        return name.isEmpty() ? "CASTGC" : name;
    }

    static String ticketValue(String stored) {
        if (stored == null) return "";
        int separator = stored.indexOf('=');
        if (separator < 0) return stored.trim();
        return stored.substring(separator + 1).trim();
    }

    /** Set-Cookie 头只取首段 name=value，丢弃 Path/HttpOnly 等属性。 */
    static String setCookiePair(String header) {
        if (header == null) return "";
        String pair = header.split(";", 2)[0].trim();
        int index = pair.indexOf('=');
        if (index <= 0) return "";
        String name = pair.substring(0, index).trim();
        String value = pair.substring(index + 1).trim();
        if (name.isEmpty() || value.isEmpty()) return "";
        return name + "=" + value;
    }

    /** 日志里的路径去掉 query 与 ;jsessionid 之类的矩阵参数。 */
    static String sanitizePath(String encodedPath) {
        if (encodedPath == null || encodedPath.isEmpty()) return "/";
        String path = encodedPath;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int matrix = path.indexOf(';');
        if (matrix >= 0) path = path.substring(0, matrix);
        return path.isEmpty() ? "/" : path;
    }

    static String cookieHeader(Map<String, String> jar) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : jar.entrySet()) {
            if (builder.length() > 0) builder.append("; ");
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static String ticketFrom(Map<String, String> auth) {
        String castgc = auth.get("CASTGC");
        if (castgc != null && !castgc.isEmpty()) return "CASTGC=" + castgc;
        String tgc = auth.get("TGC");
        if (tgc != null && !tgc.isEmpty()) return "TGC=" + tgc;
        return "";
    }

    private static void collect(List<String> setCookies, String host,
                                Map<String, String> auth, Map<String, String> zfjw,
                                List<String> zfjwSetCookies) {
        Map<String, String> jar = AUTH_HOST.equals(host) ? auth : zfjw;
        for (String header : setCookies) {
            String pair = setCookiePair(header);
            if (pair.isEmpty()) continue;
            int index = pair.indexOf('=');
            jar.put(pair.substring(0, index), pair.substring(index + 1));
            if (ZFJW_HOST.equals(host)) zfjwSetCookies.add(header);
        }
    }

    /**
     * 票据优先读加密副本；没有副本时退回 WebView Cookie（同一进程内可用）。
     * 只读探测，不打印票据值。
     */
    private static String readTicketFromWebView() {
        try {
            CookieManager manager = CookieManager.getInstance();
            String raw = manager.getCookie(AUTH_LOGIN_URL);
            String rootRaw = manager.getCookie(AUTH_COOKIE_URL);
            // 只打印 Cookie 名称，便于排查票据是否还在（不打印值）
            DebugLog.i(TAG, "webview cookies login=" + cookieNames(raw) + " root=" + cookieNames(rootRaw));
            if (raw == null || raw.isEmpty()) raw = rootRaw;
            if (raw == null) return "";
            for (String part : raw.split(";")) {
                String pair = part.trim();
                int index = pair.indexOf('=');
                if (index <= 0) continue;
                String name = pair.substring(0, index).trim();
                if (!"CASTGC".equals(name) && !"TGC".equals(name)) continue;
                String value = pair.substring(index + 1).trim();
                if (!value.isEmpty()) return name + "=" + value;
            }
        } catch (RuntimeException error) {
            DebugLog.i(TAG, "webview cookie unavailable: " + error.getClass().getSimpleName());
        }
        return "";
    }

    private static String cookieNames(String raw) {
        if (raw == null) return "null";
        StringBuilder builder = new StringBuilder();
        for (String part : raw.split(";")) {
            String pair = part.trim();
            int index = pair.indexOf('=');
            if (index <= 0) continue;
            if (builder.length() > 0) builder.append(',');
            builder.append(pair.substring(0, index).trim());
        }
        return builder.length() == 0 ? "empty" : builder.toString();
    }

    private static OkHttpClient buildAuthClient() {
        try {
            PinnedTrustManager trustManager =
                    new PinnedTrustManager(systemTrustManager(), true);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, null);
            return baseClient().sslSocketFactory(sslContext.getSocketFactory(), trustManager).build();
        } catch (Exception error) {
            throw new IllegalStateException("TLS init failed", error);
        }
    }

    private static OkHttpClient buildZfjwClient() {
        try {
            PinnedTrustManager trustManager =
                    new PinnedTrustManager(systemTrustManager(), false);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, null);
            return baseClient().sslSocketFactory(sslContext.getSocketFactory(), trustManager).build();
        } catch (Exception error) {
            throw new IllegalStateException("TLS init failed", error);
        }
    }

    private static OkHttpClient.Builder baseClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false);
    }

    private static X509TrustManager systemTrustManager() throws Exception {
        TrustManagerFactory factory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) return (X509TrustManager) manager;
        }
        throw new IllegalStateException("no system trust manager");
    }

    /**
     * 系统链校验 + 固定指纹：
     * requirePin=true（authserver）时指纹必须命中，有效期必须有效，系统链校验失败不再直接放行；
     * requirePin=false（zfjw）时系统校验通过即可，系统信任库过旧时退回指纹 + 有效期。
     */
    private static final class PinnedTrustManager implements X509TrustManager {
        private final X509TrustManager delegate;
        private final boolean requirePin;

        PinnedTrustManager(X509TrustManager delegate, boolean requirePin) {
            this.delegate = delegate;
            this.requirePin = requirePin;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            if (chain == null || chain.length == 0) {
                throw new CertificateException("证书链为空");
            }
            CertificateException systemError = null;
            try {
                delegate.checkServerTrusted(chain, authType);
            } catch (CertificateException error) {
                systemError = error;
            }
            boolean pinned = SsoPin.matches(chain[0]);
            if (pinned) {
                // 指纹命中时仍要求证书在有效期内，避免固定了一张过期证书
                chain[0].checkValidity();
            }
            if (requirePin) {
                if (!pinned) throw new CertificateException("证书指纹不一致", systemError);
                return;
            }
            if (systemError == null || pinned) return;
            throw systemError;
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }
    }
}
