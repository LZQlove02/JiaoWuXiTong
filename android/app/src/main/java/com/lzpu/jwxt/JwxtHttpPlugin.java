package com.lzpu.jwxt;

import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private static final String DEBUG_TAG = "JwxtHttp";
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

    private volatile String authCookieHeader = "";

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

    @PluginMethod
    public void fileExists(PluginCall call) {
        String path = call.getString("path");
        if (path == null || path.isEmpty()) {
            call.reject("path is required");
            return;
        }
        final File file;
        final File root;
        try {
            file = new File(path).getCanonicalFile();
            root = getContext().getFilesDir().getCanonicalFile();
        } catch (IOException e) {
            call.reject("path 无效");
            return;
        }
        // 只允许查询应用私有目录内的文件，避免被当作任意路径探测接口
        if (!file.getPath().startsWith(root.getPath() + File.separator)) {
            call.reject("path 超出应用私有目录");
            return;
        }
        JSObject result = new JSObject();
        result.put("exists", file.isFile() && file.length() > 0);
        call.resolve(result);
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
        authCookieHeader = cookie == null ? "" : cookie;
        if (!authCookieHeader.isEmpty()) {
            AuthCookieStore.save(getContext(), authCookieHeader);
        }
        call.resolve();
    }

    @PluginMethod
    public void loadAuthCookies(PluginCall call) {
        String cookie = AuthCookieStore.load(getContext());
        boolean restored = cookie != null && !cookie.isEmpty();
        if (restored) {
            authCookieHeader = cookie;
        }
        JSObject result = new JSObject();
        result.put("restored", restored);
        call.resolve(result);
    }

    @PluginMethod
    public void clearAuthCookies(PluginCall call) {
        authCookieHeader = "";
        AuthCookieStore.clear(getContext());
        call.resolve();
    }

    /**
     * 登录后的业务请求。只允许 zfjw HTTPS，Cookie 从 WebView 登录结果传入，
     * 内存中使用，并通过 Android Keystore 加密后持久化。
     */
    @PluginMethod
    public void authGet(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("url is required");
            return;
        }

        final Request request;
        try {
            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json,text/html;q=0.9,*/*;q=0.8");
            if (!authCookieHeader.isEmpty()) {
                builder.header("Cookie", authCookieHeader);
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
        final Request request;
        try {
            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
                    .header("X-Requested-With", "XMLHttpRequest");
            if (!authCookieHeader.isEmpty()) {
                builder.header("Cookie", authCookieHeader);
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
                return authCookieHeader;
            }

            @Override
            public void onResponseHeaders(List<String> setCookies) {
                if (keepCookies) updateAuthCookies(setCookies);
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

    /**
     * 用响应中的 Set-Cookie 更新会话：只保留 name=value，值为空视为删除；
     * 有变化才重新写入加密存储，避免每个请求都做一次 Keystore 加解密。
     */
    private synchronized void updateAuthCookies(List<String> setCookies) {
        if (setCookies == null || setCookies.isEmpty()) return;

        Map<String, String> jar = new LinkedHashMap<>();
        putCookiePairs(jar, authCookieHeader);
        for (String header : setCookies) {
            String pair = header.split(";", 2)[0].trim();
            int index = pair.indexOf('=');
            if (index <= 0) continue;
            String name = pair.substring(0, index).trim();
            String value = pair.substring(index + 1).trim();
            if (name.isEmpty()) continue;
            if (value.isEmpty()) {
                jar.remove(name);
            } else {
                jar.put(name, value);
            }
        }

        String merged = joinCookies(jar);
        if (merged.equals(authCookieHeader)) return;
        authCookieHeader = merged;
        if (merged.isEmpty()) {
            AuthCookieStore.clear(getContext());
        } else {
            AuthCookieStore.save(getContext(), merged);
        }
    }

    private static void putCookiePairs(Map<String, String> target, String header) {
        if (header == null || header.isEmpty()) return;
        for (String part : header.split(";")) {
            String pair = part.trim();
            int index = pair.indexOf('=');
            if (index <= 0) continue;
            target.put(pair.substring(0, index).trim(), pair.substring(index + 1).trim());
        }
    }

    private static String joinCookies(Map<String, String> jar) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : jar.entrySet()) {
            if (builder.length() > 0) builder.append("; ");
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }
}
