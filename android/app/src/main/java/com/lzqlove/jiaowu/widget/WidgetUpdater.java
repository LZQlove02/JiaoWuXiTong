package com.lzqlove.jiaowu.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.Calendar;

/**
 * 统一刷新入口：读一次 payload + 主题，再按「每个组件实例自己的样式」逐个渲染。
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
        String themeName = WidgetStore.readTheme(app);
        // 跨周降级放在渲染入口统一兜一次：闹钟可能被 Doze 延迟、开机广播也可能先于重算到达，
        // 只拦闹钟那一条路径会让组件继续画上一周的课表。
        if (WidgetScheduleDeriver.isStale(payload, Calendar.getInstance())) {
            payload.markStale();
        }
        // 只记状态，不记成绩、未读数等个人数据（日志脱敏）
        Log.i("JiaowuWidget", "payload loggedIn=" + payload.loggedIn
            + " loaded=" + payload.scheduleLoaded
            + " stale=" + payload.stale
            + " week=" + payload.week.size());

        pushNext(app, manager, payload, themeName);
        pushToday(app, manager, payload, themeName);
        pushWeek(app, manager, payload, themeName);
    }

    private static void pushNext(Context app, AppWidgetManager manager,
                                 WidgetPayload payload, String themeName) {
        int[] ids = ids(app, manager, NextClassWidgetProvider.class);
        for (int id : ids) {
            String style = WidgetStore.readStyle(app, id);
            WidgetTheme theme = WidgetTheme.resolve(app, themeName, style);
            safeUpdate(manager, id, NextClassWidgetBinder.build(app, payload, theme, id),
                NextClassWidgetProvider.class.getSimpleName(), style);
        }
    }

    private static void pushToday(Context app, AppWidgetManager manager,
                                  WidgetPayload payload, String themeName) {
        int[] ids = ids(app, manager, TodayWidgetProvider.class);
        for (int id : ids) {
            String style = WidgetStore.readStyle(app, id);
            WidgetTheme theme = WidgetTheme.resolve(app, themeName, style);
            safeUpdate(manager, id, TodayWidgetBinder.build(app, payload, theme, id),
                TodayWidgetProvider.class.getSimpleName(), style);
        }
    }

    private static void pushWeek(Context app, AppWidgetManager manager,
                                 WidgetPayload payload, String themeName) {
        int[] ids = ids(app, manager, WeekWidgetProvider.class);
        for (int id : ids) {
            String style = WidgetStore.readStyle(app, id);
            WidgetTheme theme = WidgetTheme.resolve(app, themeName, style);
            safeUpdate(manager, id, WeekWidgetBinder.build(app, payload, theme, id),
                WeekWidgetProvider.class.getSimpleName(), style);
        }
    }

    private static int[] ids(Context app, AppWidgetManager manager, Class<?> provider) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, provider));
        return ids == null ? new int[0] : ids;
    }

    private static void safeUpdate(AppWidgetManager manager, int id, RemoteViews views,
                                   String name, String style) {
        if (views == null) return;
        try {
            manager.updateAppWidget(id, views);
            Log.d("JiaowuWidget", name + ": 刷新 id=" + id + " style=" + style);
        } catch (RuntimeException error) {
            // 位图过大时 Binder 会抛 TransactionTooLargeException，这里不能崩 App
            Log.w("JiaowuWidget", name + " 刷新失败 id=" + id, error);
        }
    }
}