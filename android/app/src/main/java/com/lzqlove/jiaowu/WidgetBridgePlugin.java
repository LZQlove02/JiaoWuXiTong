package com.lzqlove.jiaowu;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.lzqlove.jiaowu.widget.NextClassWidgetProvider;
import com.lzqlove.jiaowu.widget.TodayWidgetProvider;
import com.lzqlove.jiaowu.widget.WeekWidgetProvider;
import com.lzqlove.jiaowu.widget.WidgetStore;
import com.lzqlove.jiaowu.widget.WidgetTheme;
import com.lzqlove.jiaowu.widget.WidgetUpdater;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * JS → 原生组件数据桥。对应前端 src/native/widgetBridge.js。
 * 不访问网络、不读写登录凭据，只落盘 payload 与主题。
 */
@CapacitorPlugin(name = "WidgetBridge")
public class WidgetBridgePlugin extends Plugin {

    private static final String TAG = "JiaowuWidget";

    @PluginMethod
    public void update(PluginCall call) {
        String payload = call.getString("payload");
        if (payload == null || payload.trim().isEmpty()) {
            call.reject("payload 不能为空");
            return;
        }
        WidgetStore.writePayloadPreservingBadge(getContext(), payload);
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

    /** 设置页列出桌面上的组件实例；不支持程序化添加/删除。 */
    @PluginMethod
    public void listWidgets(PluginCall call) {
        AppWidgetManager manager = AppWidgetManager.getInstance(getContext());
        JSONArray widgets = new JSONArray();
        if (manager != null) {
            try {
                addWidgets(manager, NextClassWidgetProvider.class, "next", "下一节课（2×2）", widgets);
                addWidgets(manager, TodayWidgetProvider.class, "today", "今日课程（4×2）", widgets);
                addWidgets(manager, WeekWidgetProvider.class, "week", "本周课表（4×4）", widgets);
            } catch (JSONException error) {
                call.reject("WIDGET_LIST_FAILED");
                return;
            }
        }
        JSObject result = new JSObject();
        result.put("widgets", widgets);
        call.resolve(result);
    }

    private void addWidgets(AppWidgetManager manager, Class<?> provider, String type,
                            String label, JSONArray widgets) throws JSONException {
        int[] ids = manager.getAppWidgetIds(new ComponentName(getContext(), provider));
        if (ids == null) return;
        for (int id : ids) {
            JSONObject item = new JSONObject();
            item.put("id", id);
            item.put("type", type);
            item.put("label", label);
            item.put("style", WidgetStore.readStyle(getContext(), id));
            item.put("textColor", WidgetStore.readTextColor(getContext(), id));
            widgets.put(item);
        }
    }

    @PluginMethod
    public void setWidgetStyle(PluginCall call) {
        Integer id = call.getInt("id");
        String style = call.getString("style");
        if (id == null || id <= 0 || !isKnownStyle(style)) {
            call.reject("WIDGET_STYLE_INVALID");
            return;
        }
        if (!widgetExists(id)) {
            call.reject("WIDGET_NOT_FOUND");
            return;
        }
        WidgetStore.writeStyle(getContext(), id, style);
        WidgetUpdater.refreshAll(getContext());
        call.resolve();
    }

    /** 自定义文字色；color 传空串或非法值即恢复「自动」。 */
    @PluginMethod
    public void setWidgetTextColor(PluginCall call) {
        Integer id = call.getInt("id");
        String color = call.getString("color");
        if (id == null || id <= 0) {
            call.reject("WIDGET_COLOR_INVALID");
            return;
        }
        if (!widgetExists(id)) {
            call.reject("WIDGET_NOT_FOUND");
            return;
        }
        WidgetStore.writeTextColor(getContext(), id, color == null ? "" : color);
        // 回传实际落盘值：非法颜色被归一化成空串（自动），前端以它为准
        String saved = WidgetStore.readTextColor(getContext(), id);
        Log.i(TAG, "setWidgetTextColor id=" + id + " color=" + saved);
        WidgetUpdater.refreshAll(getContext());
        JSObject result = new JSObject();
        result.put("color", saved);
        call.resolve(result);
    }

    @PluginMethod
    public void refreshWidgets(PluginCall call) {
        WidgetUpdater.refreshAll(getContext());
        call.resolve();
    }

    private boolean widgetExists(int id) {
        AppWidgetManager manager = AppWidgetManager.getInstance(getContext());
        if (manager == null) return false;
        return contains(manager, NextClassWidgetProvider.class, id)
                || contains(manager, TodayWidgetProvider.class, id)
                || contains(manager, WeekWidgetProvider.class, id);
    }

    private boolean contains(AppWidgetManager manager, Class<?> provider, int id) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(getContext(), provider));
        if (ids == null) return false;
        for (int item : ids) {
            if (item == id) return true;
        }
        return false;
    }

    private static boolean isKnownStyle(String style) {
        return WidgetStore.STYLE_GLASS.equals(style)
                || WidgetStore.STYLE_TRANSPARENT.equals(style)
                || WidgetStore.STYLE_OPAQUE.equals(style);
    }
}