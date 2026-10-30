package com.lzpu.jwxt;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 更新检查与用户支持桥。对应前端 src/native/supportBridge.js。
 *
 * 安全边界：
 * - 只向 api.github.com 发匿名 GET，不带 Cookie、账号或查询参数；
 * - 外链与邮件收件人写死，不接受任意地址；
 * - 不新增权限，不下载/安装 APK；
 * - 只读取 Build 常量，不碰 IMEI、序列号、Android ID。
 */
@CapacitorPlugin(name = "SupportBridge")
public class SupportBridgePlugin extends Plugin {

    private static final String RELEASE_REPO = "LZQlove02/lzpu-jwxt-releases";
    private static final String LATEST_URL =
            "https://api.github.com/repos/" + RELEASE_REPO + "/releases/latest";
    private static final String RELEASE_PAGE_BASE =
            "https://github.com/" + RELEASE_REPO + "/releases/tag/";
    private static final String FEEDBACK_EMAIL = "3507924962@qq.com";

    /** 只接受 v1.2.3 / 1.2.3 / 1.2.3-beta.1 这类版本号，空串与 .. 一律拒绝。 */
    private static final String TAG_PATTERN =
            "^v?\\d+(?:\\.\\d+){1,3}(?:[-+][0-9A-Za-z.-]+)?$";
    private static final int MAX_TAG_LENGTH = 40;
    private static final int MAX_SUBJECT = 120;
    private static final int MAX_BODY = 6000;
    private static final int MAX_CLIP = 8000;
    private static final int MAX_REDIRECTS = 3;

    /** 独立的无 Cookie 客户端：超时短，失败即静默，不重试。 */
    private final OkHttpClient updateClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    private String userAgent() {
        String version = "1.0";
        try {
            String name = getContext().getPackageName();
            version = getContext().getPackageManager().getPackageInfo(name, 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            // 取不到版本号时退回固定值，不影响请求
        }
        return "LzpuJwxt/" + version + " (Android)";
    }

    /** 读取发布仓库最新正式 Release；无 Release 时返回 found=false。 */
    @PluginMethod
    public void checkLatest(PluginCall call) {
        final Request request;
        try {
            request = new Request.Builder()
                    .url(LATEST_URL)
                    .header("User-Agent", userAgent())
                    .header("Accept", "application/vnd.github+json")
                    .get()
                    .build();
        } catch (IllegalArgumentException e) {
            call.reject("URL_INVALID");
            return;
        }

        try {
            HttpPolicy.GITHUB.check(request.url());
        } catch (IOException e) {
            call.reject(e.getMessage());
            return;
        }

        updateClient.dispatcher().executorService().execute(new Runnable() {
            @Override
            public void run() {
                try (Response response = HttpExecutor.execute(
                        updateClient, request, HttpPolicy.GITHUB, MAX_REDIRECTS, null, null, false)) {
                    if (response.code() == 404) {
                        JSObject result = new JSObject();
                        result.put("found", false);
                        result.put("reason", "no-release");
                        call.resolve(result);
                        return;
                    }
                    if (!response.isSuccessful()) {
                        call.reject("HTTP_" + response.code());
                        return;
                    }
                    String body = response.body() == null ? "" : response.body().string();
                    UpdateInfo info = UpdateInfo.parse(body);
                    JSObject result = new JSObject();
                    result.put("found", true);
                    result.put("tag", info.tag);
                    result.put("name", info.name);
                    result.put("notes", info.notes);
                    result.put("url", info.url);
                    result.put("publishedAt", info.publishedAt);
                    call.resolve(result);
                } catch (JSONException e) {
                    call.reject("PARSE_FAILED");
                } catch (IOException e) {
                    call.reject(e.getMessage() == null ? "NETWORK_ERROR" : e.getMessage(), e);
                }
            }
        });
    }

    /** 用系统浏览器打开发布页；tag 必须先过白名单正则。 */
    @PluginMethod
    public void openReleasePage(PluginCall call) {
        String tag = call.getString("tag", "");
        tag = tag == null ? "" : tag.trim();
        if (tag.isEmpty() || tag.length() > MAX_TAG_LENGTH || !tag.matches(TAG_PATTERN)) {
            call.reject("TAG_INVALID");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_PAGE_BASE + tag));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(intent);
            call.resolve();
        } catch (ActivityNotFoundException e) {
            call.reject("NO_BROWSER");
        }
    }

    /** 拉起系统邮件 App，收件人写死；正文为空或超长直接拒绝。 */
    @PluginMethod
    public void composeFeedback(PluginCall call) {
        String subject = call.getString("subject", "");
        String body = call.getString("body", "");
        subject = subject == null ? "" : subject.trim();
        body = body == null ? "" : body.trim();
        if (body.isEmpty()) {
            call.reject("BODY_EMPTY");
            return;
        }
        if (subject.length() > MAX_SUBJECT) {
            call.reject("SUBJECT_TOO_LONG");
            return;
        }
        if (body.length() > MAX_BODY) {
            call.reject("BODY_TOO_LONG");
            return;
        }

        Uri uri = Uri.parse("mailto:" + Uri.encode(FEEDBACK_EMAIL)
                + "?subject=" + Uri.encode(subject)
                + "&body=" + Uri.encode(body));
        Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
        if (intent.resolveActivity(getContext().getPackageManager()) == null) {
            call.reject("NO_EMAIL_APP");
            return;
        }
        try {
            getContext().startActivity(intent);
            call.resolve();
        } catch (ActivityNotFoundException e) {
            call.reject("NO_EMAIL_APP");
        }
    }

    /** 只读 Build 常量，供反馈邮件附诊断信息。 */
    @PluginMethod
    public void getDeviceInfo(PluginCall call) {
        JSObject result = new JSObject();
        result.put("manufacturer", Build.MANUFACTURER == null ? "" : Build.MANUFACTURER);
        result.put("model", Build.MODEL == null ? "" : Build.MODEL);
        result.put("android", Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE);
        result.put("sdk", Build.VERSION.SDK_INT);
        call.resolve(result);
    }

    /** 复制诊断文本（无邮件 App 或用户需要粘贴到其它渠道时使用）。 */
    @PluginMethod
    public void copyText(PluginCall call) {
        String text = call.getString("text", "");
        if (text == null || text.trim().isEmpty()) {
            call.reject("TEXT_EMPTY");
            return;
        }
        if (text.length() > MAX_CLIP) {
            call.reject("TEXT_TOO_LONG");
            return;
        }
        ClipboardManager manager =
                (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) {
            call.reject("CLIPBOARD_UNAVAILABLE");
            return;
        }
        manager.setPrimaryClip(ClipData.newPlainText("柳职教务诊断信息", text));
        call.resolve();
    }
}
