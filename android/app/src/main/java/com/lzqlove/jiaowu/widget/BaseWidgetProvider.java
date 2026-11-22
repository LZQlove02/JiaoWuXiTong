package com.lzqlove.jiaowu.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/**
 * 三个尺寸（2x2 / 4x2 / 4x4）的组件共用：样式与主题由 WidgetUpdater 按实例解析，这里只负责触发刷新。
 */
public abstract class BaseWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WidgetAlarmReceiver.schedule(context);
        // 「更新于」的相对时间每小时会变旧，另排一条轻量重绘的闹钟链
        WidgetTimeTickReceiver.schedule(context);
        WidgetUpdater.refreshAll(context);
    }

    @Override
    public void onEnabled(Context context) {
        // 首次添加时 onUpdate 未必立刻到达，主动渲染一次占位内容
        WidgetAlarmReceiver.schedule(context);
        WidgetTimeTickReceiver.schedule(context);
        WidgetUpdater.refreshAll(context);
    }

    @Override
    public void onDisabled(Context context) {
        // onDisabled 是「这个 provider 的最后一个实例被删」时触发，另外两档可能还在用闹钟：
        // 只有三档都没有实例了才停掉每天重算与每小时重绘
        if (WidgetUpdater.hasAnyWidget(context)) return;
        WidgetAlarmReceiver.cancel(context);
        WidgetTimeTickReceiver.cancel(context);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // 组件被移除时顺手清掉它的样式与文字色记录
        WidgetStore.clearInstanceSettings(context, appWidgetIds);
        super.onDeleted(context, appWidgetIds);
    }
}