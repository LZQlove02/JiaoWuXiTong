package com.lzqlove.jiaowu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 仅用于学校统一认证登录：加载 authserver / zfjw 登录链路，
 * 登录成功后回传 Cookie 并立即销毁 WebView。
 */
public class SsoLoginActivity extends Activity {

    static final String EXTRA_SILENT = "silent";
    static final String EXTRA_TIMEOUT_MS = "timeoutMs";
    static final String EXTRA_REASON = "reason";
    private static final int DEFAULT_SILENT_TIMEOUT_MS = 4000;
    private static final int MIN_SILENT_TIMEOUT_MS = 1500;
    private static final int MAX_SILENT_TIMEOUT_MS = 10000;

    private static final String AUTH_SERVER_HOST = "authserver.lzzy.net";
    private static final String SSO_PREFS = "jiaowu_sso";
    private static final String KEY_COOKIE_RESET = "cookie_cleanup_v3";
    private static final String LOGIN_URL =
            "https://authserver.lzzy.net/authserver/login"
                    + "?service=http%3A%2F%2Fzfjw.lzzy.net%2Fsso%2Fjziotlogin";
    /**
     * 统一认证服务器固定证书指纹（SHA-256，证书有效期至 2027-01-12）。
     * 与 res/xml/network_security_config.xml 的 SPKI pin 对应；证书轮换时必须同步更新两者。
     * 只有指纹完全一致才放行，不一致直接阻止登录并提示更新 App，不再使用首次信任（TOFU）。
     */
    private static final String AUTH_SERVER_SHA256 =
            "85:BA:F1:69:43:27:A8:B7:82:D2:CE:BC:A6:A1:A8:14:"
                    + "E1:22:19:D3:F2:D6:4F:1B:F3:6B:A5:66:E9:13:4A:50";

    /**
     * 加载 WebView 前的 TLS 预检：只接受内置指纹命中的证书，
     * 不依赖系统 CA（Android 6 的系统信任库不包含 Let's Encrypt 根证书）。
     * WebView 自身的证书固定只在系统校验失败时触发，无法覆盖被系统信任的中间人证书。
     */
    private static final OkHttpClient PREFLIGHT_CLIENT = buildPreflightClient();

    private static OkHttpClient buildPreflightClient() {
        try {
            PinnedTrustManager trustManager = new PinnedTrustManager();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, null);
            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .callTimeout(8, TimeUnit.SECONDS)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build();
        } catch (Exception error) {
            // 初始化失败时返回 null，预检按失败关闭处理
            return null;
        }
    }

    /** 只放行与内置 SHA-256 指纹完全一致的服务端证书，链式校验交由指纹代替。 */
    private static final class PinnedTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
            throw new java.security.cert.CertificateException("仅校验服务端证书");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
            if (chain == null || chain.length == 0) {
                throw new java.security.cert.CertificateException("证书链为空");
            }
            if (!AUTH_SERVER_SHA256.equals(sha256(chain[0].getEncoded()))) {
                throw new java.security.cert.CertificateException("证书指纹不一致");
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private WebView webView;
    private TextView status;
    private boolean finished;
    private boolean sslDialogShown;
    private boolean preflightRunning;
    private boolean silent;
    private int silentTimeoutMs;
    private String silentReason = "canceled";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable silentTimeout = new Runnable() {
        @Override
        public void run() {
            finishSilent("timeout");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        silent = getIntent().getBooleanExtra(EXTRA_SILENT, false);
        silentTimeoutMs = Math.min(Math.max(
                getIntent().getIntExtra(EXTRA_TIMEOUT_MS, DEFAULT_SILENT_TIMEOUT_MS),
                MIN_SILENT_TIMEOUT_MS), MAX_SILENT_TIMEOUT_MS);
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        if (!silent) {
            status = new TextView(this);
            status.setText("柳州职业技术大学统一认证");
            status.setTextSize(16);
            status.setPadding(32, 28, 32, 16);
            root.addView(status);
        }

        webView = new WebView(this);
        if (silent) {
            // 静默模式不显示任何界面：透明主题 + 近似全透明的 WebView，
            // 仅用于让 WebView 完成 CAS 跳转并读取 Cookie。
            webView.setAlpha(0.01f);
            webView.setBackgroundColor(Color.TRANSPARENT);
            webView.setFocusable(false);
            webView.setClickable(false);
        }
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(view, request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(view, Uri.parse(url));
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handleSslError(handler, error);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (finished) return;
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                String path = uri.getPath() == null ? "" : uri.getPath();
                if ("zfjw.lzzy.net".equals(host)) {
                    if (path.startsWith("/xtgl/") && !path.contains("login_slogin")) {
                        finishWithCookies();
                    }
                    return;
                }
                // 静默模式停在统一认证登录表单，说明 CASTGC 已不可用，
                // 立即按 login-form 失败，不把表单或验证码页展示给用户。
                if (silent && AUTH_SERVER_HOST.equals(host)) {
                    detectSilentLoginForm(view);
                }
            }
        });

        // 升级后首次打开登录页清理一次一次性安全挑战 Cookie（challenge_cookie/_shmas 等），
        // 保留 `_shmat` 设备信任与 CASTGC（有效期由学校控制，应用不重写）。
        if (!getSharedPreferences(SSO_PREFS, MODE_PRIVATE)
                .getBoolean(KEY_COOKIE_RESET, false)) {
            SsoCookieCleaner.expireTransient(cookieManager);
            SsoCookieCleaner.expireZfjw(cookieManager);
            cookieManager.flush();
            getSharedPreferences(SSO_PREFS, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_COOKIE_RESET, true)
                    .apply();
        }
        if (silent) handler.postDelayed(silentTimeout, silentTimeoutMs);
        startSso();
    }

    /**
     * 静默模式用 DOM 查询判断是否停在登录表单（存在密码框或工号/学号输入框）。
     * 只由 App 主动执行，不向页面暴露任何 JS 接口。
     */
    private void detectSilentLoginForm(WebView view) {
        view.evaluateJavascript(
                "(function(){try{return document.querySelector("
                        + "'input[type=\"password\"],input[name=\"yhm\"]')?'1':'0'"
                        + "}catch(e){return '0'}})()",
                value -> {
                    if (!finished && "\"1\"".equals(value)) finishSilent("login-form");
                });
    }

    /**
     * 先做一次只认内置指纹的 TLS 预检，再加载 WebView。
     * WebViewClient 的 onReceivedSslError 只在系统校验失败时触发，
     * 无法覆盖被系统信任的中间人证书；预检失败直接终止登录，
     * 避免账号密码提交到未固定的连接。
     */
    private void startSso() {
        if (preflightRunning) return;
        preflightRunning = true;
        if (status != null) status.setText("正在校验统一认证服务器证书…");
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                final PreflightResult result = preflight();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        preflightRunning = false;
                        if (webView == null || isFinishing() || finished) return;
                        if (result.ok) {
                            if (status != null) status.setText("正在加载统一认证登录页…");
                            webView.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    // 用户可能在这 300ms 内退出登录页，WebView 已在 onDestroy 置空
                                    if (webView != null && !finished) webView.loadUrl(LOGIN_URL);
                                }
                            }, 300);
                            return;
                        }
                        if (silent) {
                            // 静默模式不弹任何对话框，把失败原因回传给 JS 统一降级
                            finishSilent(result.certError ? "cert" : "network");
                            return;
                        }
                        if (result.certError) {
                            showCertErrorDialog();
                        } else {
                            showNetworkErrorDialog();
                        }
                    }
                });
            }
        }, "sso-preflight");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 预检：返回是否通过，以及失败来自证书固定/系统校验还是网络不可达，
     * 用于区分「可能被劫持」与「网络没通」两种提示。
     */
    private PreflightResult preflight() {
        if (PREFLIGHT_CLIENT == null) return PreflightResult.certError();
        Request request = new Request.Builder().url(LOGIN_URL).head().build();
        try (Response response = PREFLIGHT_CLIENT.newCall(request).execute()) {
            return PreflightResult.ok();
        } catch (SSLException error) {
            return PreflightResult.certError();
        } catch (IOException error) {
            return PreflightResult.networkError();
        }
    }

    private static final class PreflightResult {
        final boolean ok;
        final boolean certError;

        private PreflightResult(boolean ok, boolean certError) {
            this.ok = ok;
            this.certError = certError;
        }

        static PreflightResult ok() {
            return new PreflightResult(true, false);
        }

        static PreflightResult certError() {
            return new PreflightResult(false, true);
        }

        static PreflightResult networkError() {
            return new PreflightResult(false, false);
        }
    }

    private void showCertErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle("证书校验失败")
                .setMessage("无法通过内置证书校验连接统一认证服务器。\n\n"
                        + "可能是网络被劫持、服务器证书已轮换，或系统时间异常。已停止登录，"
                        + "请确认网络环境或更新 App 后重试。")
                .setPositiveButton("知道了", (dialog, which) -> cancel())
                .setCancelable(false)
                .show();
    }

    private void showNetworkErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle("无法连接统一认证")
                .setMessage("暂时无法连接统一认证服务器，请检查网络后重试。")
                .setPositiveButton("重试", (dialog, which) -> startSso())
                .setNegativeButton("取消", (dialog, which) -> cancel())
                .setCancelable(false)
                .show();
    }

    private boolean isAllowed(Uri uri) {
        String host = uri.getHost();
        return AUTH_SERVER_HOST.equals(host) || "zfjw.lzzy.net".equals(host);
    }

    /**
     * 只允许白名单主机；CAS 的 HTTP 回调在 WebView 内自动升级为 HTTPS，
     * 避免明文加载，同时保留 ticket 参数。
     */
    private boolean handleUrl(WebView view, Uri uri) {
        if (!isAllowed(uri)) {
            if (silent) finishSilent("blocked");
            return true;
        }
        if ("http".equals(uri.getScheme())) {
            view.loadUrl(uri.buildUpon().scheme("https").build().toString());
            return true;
        }
        return false;
    }

    private void handleSslError(SslErrorHandler handler, SslError error) {
        if (silent) {
            // 静默模式失败关闭：不展示任何证书/网络对话框
            handler.cancel();
            finishSilent("cert");
            return;
        }
        Uri uri = Uri.parse(error.getUrl());
        if (!AUTH_SERVER_HOST.equals(uri.getHost())) {
            blockSsl(handler, "证书校验失败", "zfjw.lzzy.net 的证书无法通过系统校验，已停止登录。");
            return;
        }

        CertInfo info = certInfoFrom(error.getCertificate());
        if (info == null) {
            blockSsl(handler, "无法读取证书指纹", "已停止登录，请检查网络环境后重试。");
            return;
        }
        if (AUTH_SERVER_SHA256.equals(info.sha256)) {
            handler.proceed();
            return;
        }
        blockSsl(handler, "证书指纹不一致",
                info.describe() + "\n\n内置指纹：\n" + AUTH_SERVER_SHA256
                        + "\n\n证书可能已变更或被篡改，已阻止登录。请更新 App 后再试。");
    }

    /**
     * 证书异常一律失败关闭：不再提供「仍然继续」选项，避免首次信任被中间人利用。
     */
    private void blockSsl(SslErrorHandler handler, String title, String message) {
        handler.cancel();
        if (sslDialogShown) return;
        sslDialogShown = true;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("知道了", (dialog, which) -> cancel())
                .setCancelable(false)
                .show();
    }

    /**
     * 直接从 WebView 的 SSL 错误中解析证书 DER，
     * 无需额外的网络探测，避免探测超时导致无法固定指纹。
     */
    private CertInfo certInfoFrom(SslCertificate certificate) {
        if (certificate == null) return null;
        try {
            Bundle state = SslCertificate.saveState(certificate);
            byte[] der = state == null ? null : state.getByteArray("x509-certificate");
            if (der == null || der.length == 0) return null;
            X509Certificate parsed = (X509Certificate) CertificateFactory
                    .getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(der));
            return new CertInfo(
                    parsed.getSubjectX500Principal().getName(),
                    parsed.getIssuerX500Principal().getName(),
                    parsed.getNotBefore().toString(),
                    parsed.getNotAfter().toString(),
                    sha256(parsed.getEncoded()));
        } catch (Exception error) {
            return null;
        }
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(data);
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) {
                builder.append(String.format("%02X:", value));
            }
            return builder.substring(0, builder.length() - 1);
        } catch (Exception error) {
            return "";
        }
    }

    private static final class CertInfo {
        final String subject;
        final String issuer;
        final String notBefore;
        final String notAfter;
        final String sha256;

        CertInfo(String subject, String issuer, String notBefore, String notAfter, String sha256) {
            this.subject = subject;
            this.issuer = issuer;
            this.notBefore = notBefore;
            this.notAfter = notAfter;
            this.sha256 = sha256;
        }

        String describe() {
            return "主题：" + subject
                    + "\n颁发者：" + issuer
                    + "\n有效期：" + notBefore + " - " + notAfter
                    + "\nSHA-256：" + sha256;
        }
    }

    private void finishWithCookies() {
        if (finished) return;

        // 只回传教务系统自身的会话 Cookie；统一认证侧的票据 Cookie 不得跨域发送。
        Map<String, String> cookies = new LinkedHashMap<>();
        collectCookies(cookies, "https://zfjw.lzzy.net");

        StringBuilder header = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (header.length() > 0) header.append("; ");
            header.append(entry.getKey()).append('=').append(entry.getValue());
        }

        if (header.length() == 0) {
            finishSilent("no-cookie");
            return;
        }

        finished = true;
        handler.removeCallbacks(silentTimeout);
        if (silent) Log.i("SsoSilent", "finish ok, cookies=" + cookies.size());
        final Intent data = new Intent();
        data.putExtra("cookieHeader", header.toString());
        data.putExtra("finalUrl", webView.getUrl());
        clearSessionData(new Runnable() {
            @Override
            public void run() {
                setResult(RESULT_OK, data);
                finish();
            }
        });
    }

    /** 静默失败统一出口：回传 reason，由前端决定是否降级到手动登录。 */
    private void finishSilent(String reason) {
        if (finished) return;
        finished = true;
        silentReason = reason == null ? "canceled" : reason;
        Log.i("SsoSilent", "finish failed, reason=" + silentReason);
        handler.removeCallbacks(silentTimeout);
        clearSessionData(new Runnable() {
            @Override
            public void run() {
                Intent data = new Intent();
                data.putExtra(EXTRA_REASON, silentReason);
                setResult(RESULT_CANCELED, data);
                finish();
            }
        });
    }

    /**
     * 登录流程结束后清除一次性安全挑战与教务会话，保留 CASTGC 与 `_shmat` 设备信任。
     * 不再整体 removeAllCookies / 重写设备 Cookie，
     * 避免同名重复或过期挑战值导致“设备请求校验失败”。
     * 不清 WebStorage，否则会连带清掉应用自身的 localStorage（主题、导入数据、课表快照）。
     */
    private void clearSessionData(Runnable onDone) {
        CookieManager cookieManager = CookieManager.getInstance();
        SsoCookieCleaner.expireTransient(cookieManager);
        SsoCookieCleaner.expireZfjw(cookieManager);
        cookieManager.flush();
        if (webView != null) {
            webView.clearCache(true);
            webView.clearHistory();
            webView.clearFormData();
            webView.loadUrl("about:blank");
        }
        if (onDone != null) onDone.run();
    }

    private void collectCookies(Map<String, String> target, String url) {
        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie == null) return;
        for (String part : cookie.split(";")) {
            String pair = part.trim();
            if (pair.isEmpty()) continue;
            int index = pair.indexOf('=');
            if (index <= 0) continue;
            target.put(pair.substring(0, index), pair.substring(index + 1));
        }
    }

    private void cancel() {
        if (finished) return;
        if (silent) {
            finishSilent("canceled");
            return;
        }
        finished = true;
        handler.removeCallbacks(silentTimeout);
        clearSessionData(new Runnable() {
            @Override
            public void run() {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
    }

    @Override
    public void onBackPressed() {
        cancel();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(silentTimeout);
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
