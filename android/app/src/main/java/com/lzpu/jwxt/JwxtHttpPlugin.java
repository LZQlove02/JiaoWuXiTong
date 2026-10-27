package com.lzpu.jwxt;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@CapacitorPlugin(name = "JwxtHttp")
public class JwxtHttpPlugin extends Plugin {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final int MAX_REDIRECTS = 5;

    /**
     * 公开内容客户端：关闭自动重定向，改为逐跳校验，避免被重定向到
     * 非白名单主机或明文 HTTP。认证客户端待 6.5 验证后再按同一策略建立。
     */
    private final OkHttpClient publicClient = new OkHttpClient.Builder()
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

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8");

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
                try (Response response = execute(publicClient, request, HttpPolicy.PUBLIC)) {
                    String body = response.body() == null ? "" : response.body().string();
                    JSObject result = new JSObject();
                    result.put("status", response.code());
                    result.put("url", response.request().url().toString());
                    result.put("body", body);
                    call.resolve(result);
                } catch (IOException e) {
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

        final Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build();

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
                try (Response response = execute(publicClient, request, HttpPolicy.PUBLIC)) {
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
        File file = new File(path);
        JSObject result = new JSObject();
        result.put("exists", file.exists() && file.isFile() && file.length() > 0);
        call.resolve(result);
    }

    /**
     * 手动跟随重定向：每一跳都校验协议、端口与允许主机，超过上限直接失败，
     * 不把请求转发到非白名单目标。
     */
    private Response execute(OkHttpClient client, Request request, HttpPolicy policy) throws IOException {
        Request current = request;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            policy.check(current.url());
            Response response = client.newCall(current).execute();
            if (!isRedirect(response.code())) {
                return response;
            }
            HttpUrl next;
            try {
                next = policy.resolveRedirect(current.url(), response.header("Location"));
            } finally {
                response.close();
            }
            current = current.newBuilder().url(next).build();
        }
        throw new IOException("重定向次数过多");
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }
}
