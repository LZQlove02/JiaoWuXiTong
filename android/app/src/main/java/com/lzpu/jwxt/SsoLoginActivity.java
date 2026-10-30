package com.lzpu.jwxt;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Bundle;
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
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 仅用于学校统一认证登录：加载 authserver / zfjw 登录链路，
 * 登录成功后回传 Cookie 并立即销毁 WebView。
 */
public class SsoLoginActivity extends Activity {

    private static final String AUTH_SERVER_HOST = "authserver.lzzy.net";
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

    private WebView webView;
    private boolean finished;
    private boolean sslDialogShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView status = new TextView(this);
        status.setText("柳州职业技术大学统一认证");
        status.setTextSize(16);
        status.setPadding(32, 28, 32, 16);
        root.addView(status);

        webView = new WebView(this);
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
                Uri uri = Uri.parse(url);
                if (!"zfjw.lzzy.net".equals(uri.getHost())) return;
                String path = uri.getPath() == null ? "" : uri.getPath();
                if (path.startsWith("/xtgl/") && !path.contains("login_slogin")) {
                    finishWithCookies();
                }
            }
        });

        webView.loadUrl(LOGIN_URL);
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
        if (!isAllowed(uri)) return true;
        if ("http".equals(uri.getScheme())) {
            view.loadUrl(uri.buildUpon().scheme("https").build().toString());
            return true;
        }
        return false;
    }

    private void handleSslError(SslErrorHandler handler, SslError error) {
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
        finished = true;

        // 只回传教务系统自身的会话 Cookie；统一认证侧的票据 Cookie 不得跨域发送。
        Map<String, String> cookies = new LinkedHashMap<>();
        collectCookies(cookies, "https://zfjw.lzzy.net");

        StringBuilder header = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (header.length() > 0) header.append("; ");
            header.append(entry.getKey()).append('=').append(entry.getValue());
        }

        Intent data = new Intent();
        data.putExtra("cookieHeader", header.toString());
        data.putExtra("finalUrl", webView.getUrl());
        clearSessionData();
        setResult(RESULT_OK, data);
        finish();
    }

    /**
     * 登录流程结束后清空 WebView 持有的 Cookie 与页面缓存，
     * 避免会话明文残留在应用私有目录。不清 WebStorage，否则会连带清掉
     * 应用自身的 localStorage（主题、导入数据、课表快照）。
     */
    private void clearSessionData() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(null);
        cookieManager.flush();
        if (webView == null) return;
        webView.clearCache(true);
        webView.clearHistory();
        webView.clearFormData();
        webView.loadUrl("about:blank");
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
        finished = true;
        clearSessionData();
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onBackPressed() {
        cancel();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
