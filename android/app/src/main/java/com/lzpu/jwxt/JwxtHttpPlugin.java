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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@CapacitorPlugin(name = "JwxtHttp")
public class JwxtHttpPlugin extends Plugin {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
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

        client.newCall(builder.get().build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call requestCall, IOException e) {
                call.reject(e.getMessage() == null ? "network error" : e.getMessage(), e);
            }

            @Override
            public void onResponse(Call requestCall, Response response) {
                try (Response closeable = response) {
                    String body = closeable.body() == null ? "" : closeable.body().string();
                    JSObject result = new JSObject();
                    result.put("status", closeable.code());
                    result.put("url", closeable.request().url().toString());
                    result.put("body", body);
                    call.resolve(result);
                } catch (IOException e) {
                    call.reject(e.getMessage() == null ? "read error" : e.getMessage(), e);
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

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call requestCall, IOException e) {
                call.reject(e.getMessage() == null ? "download failed" : e.getMessage(), e);
            }

            @Override
            public void onResponse(Call requestCall, Response response) {
                try (Response closeable = response) {
                    if (!closeable.isSuccessful() || closeable.body() == null) {
                        call.reject("download failed: " + closeable.code());
                        return;
                    }
                    File target = new File(getContext().getFilesDir(), filename);
                    try (FileOutputStream output = new FileOutputStream(target)) {
                        output.write(closeable.body().bytes());
                    }
                    JSObject result = new JSObject();
                    result.put("path", target.getAbsolutePath());
                    result.put("bytes", target.length());
                    call.resolve(result);
                } catch (IOException e) {
                    call.reject(e.getMessage() == null ? "write failed" : e.getMessage(), e);
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
}
