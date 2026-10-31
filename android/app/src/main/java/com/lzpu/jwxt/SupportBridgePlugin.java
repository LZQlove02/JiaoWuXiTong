package com.lzpu.jwxt;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
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

    /** 本地附件只允许这些可读格式，避免把下载目录暴露成任意文件启动器。 */
    private static final Set<String> LOCAL_FILE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "zip", "rar", "7z", "txt", "jpg", "jpeg", "png"));

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
        try {
            launch(intent);
            call.resolve();
        } catch (ActivityNotFoundException e) {
            call.reject("NO_BROWSER");
        }
    }

    /** 打开已下载到私有目录的附件；路径与扩展名都做白名单校验。 */
    @PluginMethod
    public void openLocalFile(PluginCall call) {
        String path = call.getString("path");
        if (path == null || path.isEmpty()) {
            call.reject("PATH_INVALID");
            return;
        }
        final File file;
        try {
            file = new File(path).getCanonicalFile();
        } catch (IOException error) {
            call.reject("PATH_INVALID");
            return;
        }
        if (!file.isFile() || !isInsidePrivateDir(file) || !isAllowedLocalFile(file)) {
            call.reject("FILE_NOT_ALLOWED");
            return;
        }
        Uri uri = FileProvider.getUriForFile(
                getContext(), getContext().getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeOf(file.getName()))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            launch(intent);
            call.resolve();
        } catch (ActivityNotFoundException error) {
            call.reject("NO_APP");
        } catch (RuntimeException error) {
            call.reject("OPEN_FAILED");
        }
    }

    /** 打开公开官网链接：只允许 https://www.lzpu.edu.cn，避免变成任意 Intent 跳板。 */
    @PluginMethod
    public void openExternal(PluginCall call) {
        String url = call.getString("url");
        HttpUrl parsed;
        try {
            parsed = HttpUrl.parse(url == null ? "" : url);
        } catch (IllegalArgumentException error) {
            parsed = null;
        }
        if (parsed == null) {
            call.reject("URL_INVALID");
            return;
        }
        try {
            HttpPolicy.PUBLIC.check(parsed);
        } catch (IOException error) {
            call.reject("URL_NOT_ALLOWED");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(parsed.toString()));
        try {
            launch(intent);
            call.resolve();
        } catch (ActivityNotFoundException error) {
            call.reject("NO_BROWSER");
        } catch (RuntimeException error) {
            call.reject("OPEN_FAILED");
        }
    }

    private boolean isInsidePrivateDir(File file) {
        try {
            File files = getContext().getFilesDir().getCanonicalFile();
            File cache = getContext().getCacheDir().getCanonicalFile();
            String path = file.getCanonicalPath();
            return path.startsWith(files.getPath() + File.separator)
                    || path.startsWith(cache.getPath() + File.separator);
        } catch (IOException error) {
            return false;
        }
    }

    private static boolean isAllowedLocalFile(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);
        return LOCAL_FILE_EXTENSIONS.contains(ext);
    }

    private static String mimeOf(String filename) {
        int dot = filename.lastIndexOf('.');
        String ext = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.US);
        switch (ext) {
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "zip":
                return "application/zip";
            case "rar":
                return "application/vnd.rar";
            case "7z":
                return "application/x-7z-compressed";
            case "txt":
                return "text/plain";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            default:
                return "application/octet-stream";
        }
    }

    /** 邮件 App 与浏览器都可能在应用上下文里启动，统一补 NEW_TASK。 */
    private void launch(Intent intent) {
        if (getActivity() != null) {
            getActivity().startActivity(intent);
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
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
            launch(intent);
            call.resolve();
        } catch (ActivityNotFoundException e) {
            call.reject("NO_EMAIL_APP");
        }
    }

    /** 缓存占用统计：只返回应用私有目录的字节数，不读取文件内容。 */
    @PluginMethod
    public void getStorageInfo(PluginCall call) {
        // 只统计「清除缓存」会真正删除的文件，避免把 WebView 等不可清缓存算进去
        long cacheBytes = dirSize(new File(getContext().getCacheDir(), "attachments"));
        long filesBytes = publicDownloadSize(getContext().getFilesDir());
        JSObject result = new JSObject();
        result.put("cacheBytes", cacheBytes);
        result.put("filesBytes", filesBytes);
        result.put("totalBytes", cacheBytes + filesBytes);
        call.resolve(result);
    }

    private static long publicDownloadSize(File dir) {
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return 0;
        long total = 0;
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("guide-") || name.equals("calendar.jpg")) {
                total += file.length();
            }
        }
        return total;
    }

    private static long dirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        if (dir.isFile()) return dir.length();
        File[] children = dir.listFiles();
        if (children == null) return 0;
        long total = 0;
        for (File child : children) total += dirSize(child);
        return total;
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

    /** 读取本地应用/原生崩溃摘要；没有记录时 found=false。只读私有目录，不联网。 */
    @PluginMethod
    public void getLastCrash(PluginCall call) {
        String appCrash = CrashLog.readSummary(getContext());
        String nativeCrash = NativeCrashLog.readSummary(getContext());
        String summary = joinSummaries(appCrash, nativeCrash);
        JSObject result = new JSObject();
        result.put("found", !summary.isEmpty());
        result.put("summary", summary);
        call.resolve(result);
    }

    private static String joinSummaries(String first, String second) {
        if (first.isEmpty()) return second;
        if (second.isEmpty()) return first;
        return first + "\n\n" + second;
    }

    /** 清除本地应用与原生崩溃记录。 */
    @PluginMethod
    public void clearCrashLog(PluginCall call) {
        CrashLog.clear(getContext());
        NativeCrashLog.clear(getContext());
        call.resolve();
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
