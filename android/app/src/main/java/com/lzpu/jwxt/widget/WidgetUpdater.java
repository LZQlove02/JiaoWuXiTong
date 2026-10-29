package com.lzpu.jwxt.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * 统一刷新入口：读一次 payload + 主题，再推给所有已添加的组件。
 * 组件不访问网络，全部数据来自 App 写入的快照。
 */
public final class WidgetUpdater {

    private WidgetUpdater() {
    }

    public static void refreshAll(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        if (manager == null) return;

        WidgetPayload payload = WidgetPayload.parse(WidgetStore.readPayload(app));
        WidgetTheme theme = WidgetTheme.resolve(app, WidgetStore.readTheme(app));

        push(app, manager, NextClassWidgetProvider.class,
            NextClassWidgetProvider.build(app, payload, theme));
        push(app, manager, TodayWidgetProvider.class,
            TodayWidgetProvider.build(app, payload, theme));
    }

    private static void push(Context context, AppWidgetManager manager, Class<?> provider,
                             RemoteViews views) {
        if (views == null) return;
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, provider));
        if (ids == null || ids.length == 0) {
            Log.i("LzpuWidget", provider.getSimpleName() + ": 桌面未添加，跳过");
            return;
        }
        Log.i("LzpuWidget", provider.getSimpleName() + ": 刷新 " + ids.length + " 个组件");
        for (int id : ids) {
            manager.updateAppWidget(id, views);
        }
    }
}