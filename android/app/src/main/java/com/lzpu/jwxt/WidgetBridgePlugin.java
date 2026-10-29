package com.lzpu.jwxt;

import android.util.Log;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.lzpu.jwxt.widget.WidgetStore;
import com.lzpu.jwxt.widget.WidgetTheme;
import com.lzpu.jwxt.widget.WidgetUpdater;

/**
 * JS → 原生组件数据桥。对应前端 src/native/widgetBridge.js。
 * 不访问网络、不读写登录凭据，只落盘 payload 与主题。
 */
@CapacitorPlugin(name = "WidgetBridge")
public class WidgetBridgePlugin extends Plugin {

    private static final String TAG = "LzpuWidget";

    @PluginMethod
    public void update(PluginCall call) {
        String payload = call.getString("payload");
        if (payload == null || payload.trim().isEmpty()) {
            call.reject("payload 不能为空");
            return;
        }
        WidgetStore.writePayload(getContext(), payload);
        Log.i(TAG, "update " + payload.length() + "B");
        WidgetUpdater.refreshAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void clear(PluginCall call) {
        // 只清内容、保留主题，组件显示「请登录」而不是旧课表
        WidgetStore.writePayload(getContext(), "{\"schema\":1,\"loggedIn\":false}");
        Log.i(TAG, "clear");
        WidgetUpdater.refreshAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void setTheme(PluginCall call) {
        String theme = call.getString("theme");
        String resolved = theme == null ? WidgetTheme.SYSTEM : theme;
        WidgetStore.writeTheme(getContext(), resolved);
        Log.i(TAG, "setTheme " + resolved);
        WidgetUpdater.refreshAll(getContext());
        call.resolve();
    }
}