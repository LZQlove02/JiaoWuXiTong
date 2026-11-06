package com.lzqlove.jiaowu;

import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.lzqlove.jiaowu.keepalive.SessionKeepAliveScheduler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@CapacitorPlugin(name = "JwxtHttp")
public class JwxtHttpPlugin extends Plugin {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final int MAX_REDIRECTS = 5;
    private static final long MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024;
    private static final long MAX_ATTACHMENT_DIR_BYTES = 50L * 1024 * 1024;
    private static final Set<String> ATTACHMENT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "zip", "rar", "7z", "txt", "jpg", "jpeg", "png"));

    private static final String DEBUG_TAG = "JwxtHttp";
    /** 本机会话存在但 Keystore 暂时无法解密时返回给 JS 的错误码，前端按临时故障处理。 */
    private static final String SESSION_STORE_UNAVAILABLE = "SESSION_STORE_UNAVAILABLE";
    /** debug 日志只保留能定位接口的参数值，其余查询值一律脱敏。 */
    private static final Set<String> SAFE_QUERY_KEYS = new HashSet<>(Arrays.asList(
            "gnmkdm", "doType", "xnm", "xqm", "type", "pageNum", "pageSize"));

    private boolean isDebugBuild() {
        return (getContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private static String debugUrl(HttpUrl url) {
        HttpUrl.Builder builder = url.newBuilder();
        for (int i = 0; i < url.querySize(); i++) {
            String name = url.queryParameterName(i);
            if (!SAFE_QUERY_KEYS.contains(name)) {
                builder.setQueryParameter(name, "***");
            }
        }
        return builder.build().toString();
    }

    /**
     * 公开内容客户端：关闭自动重定向，改为逐跳校验，避免被重定向到
     * 非白名单主机或明文 HTTP。
     */
    private final OkHttpClient publicClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    /** 登录后的教务业务请求：仅允许 zfjw HTTPS，Cookie 只在内存中维护并加密持久化。 */
    private final OkHttpClient authClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    @PluginMethod
    public void get(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("url is required");
            return;
        }

        Request.Builder builder;
        try {
            builder = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8");
        } catch (IllegalArgumentException e) {
            call.reject("url 无效");
            return;
        }

        JSObject headers = call.getObject("headers");
        if (headers != null) {
            Iterator<String> keys = headers.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = headers.getString(key);
                if (value != null) builder.header(key, value);
            }
        }

        final Request request = builder.get().build();

        try {
            HttpPolicy.PUBLIC.check(request.url());
        } catch (IOException e) {
            call.reject(e.getMessage());
            return;
        }

        publicClient.dispatcher().executorService().execute(new Runnable() {
            @Override
            public void run() {
                try (Response response = execute(publicClient, request, HttpPolicy.PUBLIC, false)) {
                    String body = response.body() == null ? "" : response.body().string();
                    JSObject result = new JSObject();
                    result.put("status", response.code());
                    result.put("url", response.request().url().toString());
                    result.put("body", body);
                    call.resolve(result);
                } catch (IOException e) {
                    if (isDebugBuild()) {
                        Log.d(DEBUG_TAG, "失败 GET " + debugUrl(request.url()) + ": " + e.getMessage());
                    }
                    call.reject(e.getMessage() == null ? "network error" : e.getMessage(), e);
                }
            }
        });
    }

    @PluginMethod
    public void download(PluginCall call) {
        String url = call.getString("url");
        String filename = call.getString("filename");
        if (url == null || url.isEmpty() || filename == null
                || !filename.matches("[A-Za-z0-9._-]+")) {
            call.reject("url and a safe filename are required");
            return;
        }

        final Request request;
        try {
            request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build();
        } catch (IllegalArgumentException e) {
            call.reject("url 无效");
            return;
        }

        try {
            HttpPolicy.PUBLIC.check(request.url());
        } catch (IOException e) {
            call.reject(e.getMessage());
            return;
        }

        final String targetName = filename;
        publicClient.dispatcher().executorService().execute(new Runnable() {
            @Override
            public void run() {
                try (Response response = execute(publicClient, request, HttpPolicy.PUBLIC, false)) {
                    if (!response.isSuccessful() || response.body() == null) {
                        call.reject("download failed: " + response.code());
                        return;
                    }
                    File target = new File(getContext().getFilesDir(), targetName);
                    try (FileOutputStream output = new FileOutputStream(target)) {
                        output.write(response.body().bytes());
                    }
                    JSObject result = new JSObject();
                    result.put("path", target.getAbsolutePath());
                    result.put("bytes", target.length());
                    call.resolve(result);
                } catch (IOException e) {
                    if (isDebugBuild()) {
                        Log.d(DEBUG_TAG, "失败 DOWNLOAD " + debugUrl(request.url()) + ": " + e.getMessage());
                    }
                    call.reject(e.getMessage() == null ? "download failed" : e.getMessage(), e);
                }
            }
        });
    }

    /**
     * 带登录态下载通知附件/正文图片。只允许 zfjw HTTPS，文件存到私有 cache，
     * 限制 20 MB 与扩展名白名单，避免把任意 URL 变成文件写入接口。
     */
    @PluginMethod
    public void authDownload(PluginCall call) {
        String url = call.getString("url");
        String rawName = call.getString("filename");
        String filename = safeFileName(rawName);
        if (url == null || url.isEmpty() || !isAllowedAttachment(filename)) {
            call.reject("附件地址或文件名不合法");
            return;
        }

        // 前后台共用加密存储中的最新会话，冷启动后也能直接读到
        String saved = AuthCookieStore.load(getContext());
        final String cookie = saved == null ? "" : saved;
        final Request request;
        try {
            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .header("Cookie", cookie);
            request = builder.get().build();
        } catch (IllegalArgumentException e) {
            call.reject("url 无效");
            return;
        }

        try {
            HttpPolicy.AUTH.check(request.url());
        } catch (IOException e) {
            call.reject(e.getMessage());
            return;
        }

        final String targetName = filename;
        authClient.dispatcher().executorService().execute(new Runnable() {
            @Override
            public void run() {
                try (Response response = execute(authClient, request, HttpPolicy.AUTH, true)) {
                    if (!response.isSuccessful() || response.body() == null) {
                        call.reject("下载失败：" + response.code());
                        return;
                    }
                    String contentType = response.header("Content-Type");
                    if (contentType != null
                            && contentType.toLowerCase(Locale.US).contains("text/html")) {
                        call.reject("返回的是登录页或错误页，不是文件");
                        return;
                    }
                    long declared = response.body().contentLength();
                    if (declared > MAX_ATTACHMENT_BYTES) {
                        call.reject("文件超过 20 MB");
                        return;
                    }
                    byte[] data = response.body().bytes();
                    if (data.length > MAX_ATTACHMENT_BYTES) {
                        call.reject("文件超过 20 MB");
                        return;
                    }
                    File dir = new File(getContext().getCacheDir(), "attachments");
                    if (!dir.isDirectory() && !dir.mkdirs()) {
                        call.reject("无法创建附件目录");
                        return;
                    }
                    File target = new File(dir, hashOf(request.url().toString()) + "_" + targetName);
                    // 同 URL 重新下载时先移除旧文件，再按 50 MB 上限淘汰最久未使用的附件
                    if (target.isFile()) {
                        //noinspection ResultOfMethodCallIgnored
                        target.delete();
                    }
                    pruneAttachments(dir, data.length);
                    try (FileOutputStream output = new FileOutputStream(target)) {
                        output.write(data);
                    }
                    JSObject result = new JSObject();
                    result.put("path", target.getAbsolutePath());
                    result.put("name", targetName);
                    result.put("bytes", target.length());
                    call.resolve(result);
                } catch (IOException e) {
                    call.reject(e.getMessage() == null ? "下载失败" : e.getMessage(), e);
                }
            }
        });
    }

    /** 清除通知附件缓存；不影响登录态与其它数据。 */
    @PluginMethod
    public void clearAuthDownloads(PluginCall call) {
        File dir = new File(getContext().getCacheDir(), "attachments");
        deleteTree(dir);
        call.resolve();
    }

    /** 清除校历与办事指南图片缓存（公开下载统一落在 files 目录）。 */
    @PluginMethod
    public void clearPublicDownloads(PluginCall call) {
        File dir = getContext().getFilesDir();
        File[] files = dir == null ? null : dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith("guide-") || name.equals("calendar.jpg")) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
        call.resolve();
    }

    /** 附件目录超过 50 MB 时按最后修改时间从旧到新删除，给新文件腾空间。 */
    private static void pruneAttachments(File dir, long incoming) {
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null || files.length == 0) return;
        long total = 0;
        for (File file : files) total += file.length();
        if (total + incoming <= MAX_ATTACHMENT_DIR_BYTES) return;
        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return Long.compare(left.lastModified(), right.lastModified());
            }
        });
        for (File file : files) {
            if (total + incoming <= MAX_ATTACHMENT_DIR_BYTES) break;
            long length = file.length();
            //noinspection ResultOfMethodCallIgnored
            if (file.delete()) total -= length;
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static String safeFileName(String input) {
        String name = input == null ? "" : input.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        name = name.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        name = name.replaceAll("_+", "_");
        if (name.length() > 80) {
            int dot = name.lastIndexOf('.');
            String ext = dot >= 0 && name.length() - dot <= 8 ? name.substring(dot) : "";
            String base = dot >= 0 ? name.substring(0, dot) : name;
            name = base.substring(0, Math.min(base.length(), 80 - ext.length())) + ext;
        }
        return name.isEmpty() ? "attachment" : name;
    }

    private static boolean isAllowedAttachment(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = filename.substring(dot + 1).toLowerCase(Locale.US);
        return ATTACHMENT_EXTENSIONS.contains(ext);
    }

    private static String hashOf(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8; i += 1) builder.append(String.format(Locale.US, "%02x", bytes[i]));
            return builder.toString();
        } catch (NoSuchAlgorithmException error) {
            return String.valueOf(value.hashCode());
        }
    }

    @PluginMethod
    public void fileExists(PluginCall call) {
        String path = call.getString("path");
        if (path == null || path.isEmpty()) {
            call.reject("path is required");
            return;
        }
        final File file;
        try {
            file = new File(path).getCanonicalFile();
        } catch (IOException e) {
            call.reject("path 无效");
            return;
        }
        // 只允许查询应用私有 files / cache 目录，避免被当作任意路径探测接口
        if (!isInsidePrivateDir(file)) {
            call.reject("path 超出应用私有目录");
            return;
        }
        JSObject result = new JSObject();
        result.put("exists", file.isFile() && file.length() > 0);
        call.resolve(result);
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

    /** 供前端判断是否 debug 构建，仅在 debug 包中开启接口探测。 */
    @PluginMethod
    public void isDebugBuild(PluginCall call) {
        JSObject result = new JSObject();
        result.put("debug", isDebugBuild());
        call.resolve(result);
    }

    @PluginMethod
    public void setAuthCookies(PluginCall call) {
        String cookie = call.getString("cookie");
        String value = cookie == null ? "" : cookie;
        if (!value.isEmpty()) {
            AuthCookieStore.save(getContext(), value);
            SessionKeepAliveScheduler.resumeIfEnabled(getContext());
        } else {
            Log.w(DEBUG_TAG, "setAuthCookies: empty cookie, not saving");
        }
        call.resolve();
    }

    @PluginMethod
    public void loadAuthCookies(PluginCall call) {
        String cookie = AuthCookieStore.load(getContext());
        boolean restored = cookie != null && !cookie.isEmpty();
        // 本机存有会话但本次解密失败：交给前端保留快照登录态并重试，
        // 直接回 restored=false 会让冷启动误跳登录页
        boolean pending = !restored && AuthCookieStore.hasStoredCookie(getContext());
        Log.i(DEBUG_TAG, "loadAuthCookies: restored=" + restored + ", pending=" + pending);
        JSObject result = new JSObject();
        result.put("restored", restored);
        result.put("pending", pending);
        call.resolve(result);
    }

    @PluginMethod
    public void clearAuthCookies(PluginCall call) {
        AuthCookieStore.clear(getContext());
        SessionKeepAliveScheduler.cancel(getContext());
        call.resolve();
    }

    /**
     * 登录后的业务请求。只允许 zfjw HTTPS，Cookie 使用本地加密存储中的最新会话。
     */
    @PluginMethod
    public void authGet(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("url is required");
            return;
        }
        String cookie = AuthCookieStore.load(getContext());
        // 本机存有会话但解密失败（Keystore 尚未就绪）时不能发无 Cookie 请求：
        // 服务端会跳转登录页，前端将误判会话失效并永久清空本地 Cookie。
        if ((cookie == null || cookie.isEmpty()) && AuthCookieStore.hasStoredCookie(getContext())) {
            call.reject(SESSION_STORE_UNAVAILABLE);
            return;
        }

        final Request request;
        try {
            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json,text/html;q=0.9,*/*;q=0.8");
            if (cookie != null && !cookie.isEmpty()) {
                builder.header("Cookie", cookie);
            }
            request = builder.get().build();
        } catch (IllegalArgumentException e) {
            call.reject("url 无效");
            return;
        }

        try {
            HttpPolicy.AUTH.check(request.url());
        } catch (IOException e) {
            call.reject(e.getMessage());
            return;
        }

        authClient.dispatcher().executorService().execute(new Runnable() {
            @Override
            public void run() {
                try (Response response = execute(authClient, request, HttpPolicy.AUTH, true)) {
                    String body = response.body() == null ? "" : response.body().string();
                    JSObject result = new JSObject();
                    result.put("status", response.code());
                    result.put("url", response.request().url().toString());
                    result.put("body", body);
                    call.resolve(result);
                } catch (IOException e) {
                    if (isDebugBuild()) {
                        Log.d(DEBUG_TAG, "失败 GET " + debugUrl(request.url()) + ": " + e.getMessage());
                    }
                    call.reject(e.getMessage() == null ? "network error" : e.getMessage(), e);
                }
            }
        });
    }

    @PluginMethod
    public void authPost(PluginCall call) {
        String url = call.getString("url");
        String body = call.getString("body");
        if (url == null || url.isEmpty()) {
            call.reject("url is required");
            return;
        }

        RequestBody requestBody = RequestBody.create(
                body == null ? "" : body,
                MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8"));
        String cookie = AuthCookieStore.load(getContext());
        // 同 authGet：解密失败时不能发无 Cookie 请求，避免误判会话失效
        if ((cookie == null || cookie.isEmpty()) && AuthCookieStore.hasStoredCookie(getContext())) {
            call.reject(SESSION_STORE_UNAVAILABLE);
            return;
        }
        final Request request;
        try {
            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
                    .header("X-Requested-With", "XMLHttpRequest");
            if (cookie != null && !cookie.isEmpty()) {
                builder.header("Cookie", cookie);
            }
            request = builder.build();
        } catch (IllegalArgumentException e) {
            call.reject("url 无效");
            return;
        }

        try {
            HttpPolicy.AUTH.check(request.url());
        } catch (IOException e) {
            call.reject(e.getMessage());
            return;
        }

        authClient.dispatcher().executorService().execute(new Runnable() {
            @Override
            public void run() {
                try (Response response = execute(authClient, request, HttpPolicy.AUTH, true)) {
                    String responseBody = response.body() == null ? "" : response.body().string();
                    JSObject result = new JSObject();
                    result.put("status", response.code());
                    result.put("url", response.request().url().toString());
                    result.put("body", responseBody);
                    call.resolve(result);
                } catch (IOException e) {
                    if (isDebugBuild()) {
                        Log.d(DEBUG_TAG, "失败 POST " + debugUrl(request.url()) + ": " + e.getMessage());
                    }
                    call.reject(e.getMessage() == null ? "network error" : e.getMessage(), e);
                }
            }
        });
    }

    /**
     * 手动跟随重定向：每一跳都校验协议、端口与允许主机，超过上限直接失败，
     * 不把请求转发到非白名单目标。keepCookies 为 true 时同步服务端下发的 Set-Cookie。
     * 具体循环在 {@link HttpExecutor} 中，与更新检查共用。
     */
    private Response execute(OkHttpClient client, Request request, HttpPolicy policy,
                             boolean keepCookies) throws IOException {
        return HttpExecutor.execute(client, request, policy, MAX_REDIRECTS,
                cookieHandler(keepCookies), observer(), keepCookies);
    }

    private HttpExecutor.CookieHandler cookieHandler(final boolean keepCookies) {
        return new HttpExecutor.CookieHandler() {
            @Override
            public String header() {
                String cookie = AuthCookieStore.load(getContext());
                return cookie == null ? "" : cookie;
            }

            @Override
            public void onResponseHeaders(List<String> setCookies) {
                if (keepCookies) {
                    AuthCookieStore.mergeSetCookies(getContext(), setCookies);
                }
            }
        };
    }

    /** debug 包才打的请求日志，查询值按白名单脱敏。 */
    private HttpExecutor.Observer observer() {
        if (!isDebugBuild()) return null;
        return new HttpExecutor.Observer() {
            @Override
            public void onRequest(Request request) {
                Log.d(DEBUG_TAG, "请求 " + request.method() + " " + debugUrl(request.url()));
            }

            @Override
            public void onResponse(Response response) {
                Log.d(DEBUG_TAG, "响应 " + response.code() + " " + response.request().method() + " "
                        + debugUrl(response.request().url())
                        + " [" + response.header("Content-Type") + "]");
            }

            @Override
            public void onRedirect(int code, HttpUrl next) {
                Log.d(DEBUG_TAG, "重定向 " + code + " -> " + debugUrl(next));
            }
        };
    }
}
