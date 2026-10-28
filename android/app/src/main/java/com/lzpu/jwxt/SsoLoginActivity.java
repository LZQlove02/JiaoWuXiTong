package com.lzpu.jwxt;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
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

import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 仅用于学校统一认证登录：加载 authserver / zfjw 登录链路，
 * 登录成功后回传 Cookie 并立即销毁 WebView。
 */
public class SsoLoginActivity extends Activity {

    private static final String LOGIN_URL =
            "https://authserver.lzzy.net/authserver/login"
                    + "?service=http%3A%2F%2Fzfjw.lzzy.net%2Fsso%2Fjziotlogin";
    private static final String PREFS = "sso_login";
    private static final String PIN_KEY = "authserver_sha256";

    private WebView webView;
    private boolean finished;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("柳州职业技术大学统一认证");
        title.setTextSize(16);
        title.setPadding(32, 28, 32, 16);
        root.addView(title);

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
        return "authserver.lzzy.net".equals(host) || "zfjw.lzzy.net".equals(host);
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
        if (!"authserver.lzzy.net".equals(uri.getHost())) {
            handler.cancel();
            return;
        }

        SslCertificate certificate = error.getCertificate();
        String fingerprint = fingerprintOf(certificate);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String pinned = prefs.getString(PIN_KEY, null);
        if (pinned != null && pinned.equals(fingerprint)) {
            handler.proceed();
            return;
        }

        String details = describe(certificate, fingerprint);
        new AlertDialog.Builder(this)
                .setTitle("authserver 证书异常")
                .setMessage(details + "\n\n是否仅本次继续登录？")
                .setPositiveButton("继续", (dialog, which) -> {
                    if (fingerprint != null) {
                        prefs.edit().putString(PIN_KEY, fingerprint).apply();
                    }
                    handler.proceed();
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    handler.cancel();
                    cancel();
                })
                .setCancelable(false)
                .show();
    }

    private String describe(SslCertificate certificate, String fingerprint) {
        if (certificate == null) return "证书信息不可用";
        String subject = certificate.getIssuedTo() == null
                ? "" : certificate.getIssuedTo().getCName();
        String issuer = certificate.getIssuedBy() == null
                ? "" : certificate.getIssuedBy().getCName();
        String notBefore = certificate.getValidNotBeforeDate() == null
                ? "" : certificate.getValidNotBeforeDate().toString();
        String notAfter = certificate.getValidNotAfterDate() == null
                ? "" : certificate.getValidNotAfterDate().toString();
        return "主题：" + subject
                + "\n颁发者：" + issuer
                + "\n有效期：" + notBefore + " - " + notAfter
                + "\nSHA-256：" + (fingerprint == null ? "不可用" : fingerprint);
    }

    private String fingerprintOf(SslCertificate certificate) {
        try {
            Bundle state = SslCertificate.saveState(certificate);
            byte[] der = state == null ? null : state.getByteArray("x509cert");
            if (der == null) return null;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(der);
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) {
                builder.append(String.format("%02X:", value));
            }
            return builder.substring(0, builder.length() - 1);
        } catch (Exception error) {
            return null;
        }
    }

    private void finishWithCookies() {
        if (finished) return;
        finished = true;

        Map<String, String> cookies = new LinkedHashMap<>();
        collectCookies(cookies, "https://zfjw.lzzy.net");
        collectCookies(cookies, "http://zfjw.lzzy.net");
        collectCookies(cookies, "https://authserver.lzzy.net");
        collectCookies(cookies, "http://authserver.lzzy.net");

        StringBuilder header = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (header.length() > 0) header.append("; ");
            header.append(entry.getKey()).append('=').append(entry.getValue());
        }

        Intent data = new Intent();
        data.putExtra("cookieHeader", header.toString());
        data.putExtra("finalUrl", webView.getUrl());
        setResult(RESULT_OK, data);
        finish();
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
