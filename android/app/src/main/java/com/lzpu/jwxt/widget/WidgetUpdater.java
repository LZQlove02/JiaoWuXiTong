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
        WidgetTheme theme = WidgetTheme.resolve(app,
            WidgetStore.readTheme(app), WidgetStore.readStyle(app));
        Log.i("LzpuWidget", "style=" + theme.style + " dark=" + theme.dark);
        Log.i("LzpuWidget", "payload loggedIn=" + payload.loggedIn
            + " loaded=" + payload.scheduleLoaded
            + " week=" + payload.week.size()
            + " unread=" + payload.unread
            + " grade=" + (payload.gradeLabel.isEmpty() ? "-" : payload.gradeLabel));

        push(app, manager, NextClassWidgetProvider.class,
            NextClassWidgetProvider.build(app, payload, theme));
        push(app, manager, TodayWidgetProvider.class,
            TodayWidgetProvider.build(app, payload, theme));
        pushWeek(app, manager, payload, theme);
    }

    // 4x4 的位图尺寸跟每个组件实例的实际格子大小有关，必须按 id 单独渲染
    private static void pushWeek(Context app, AppWidgetManager manager,
                                 WidgetPayload payload, WidgetTheme theme) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, WeekWidgetProvider.class));
        if (ids == null || ids.length == 0) {
            Log.i("LzpuWidget", "WeekWidgetProvider: 桌面未添加，跳过");
            return;
        }
        Log.i("LzpuWidget", "WeekWidgetProvider: 刷新 " + ids.length + " 个组件");
        for (int id : ids) {
            RemoteViews views = WeekWidgetProvider.build(app, payload, theme, id);
            if (views == null) continue;
            try {
                manager.updateAppWidget(id, views);
            } catch (RuntimeException error) {
                // 位图过大时 Binder 会抛 TransactionTooLargeException，这里不能崩 App
                Log.w("LzpuWidget", "周课表刷新失败 id=" + id, error);
            }
        }
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