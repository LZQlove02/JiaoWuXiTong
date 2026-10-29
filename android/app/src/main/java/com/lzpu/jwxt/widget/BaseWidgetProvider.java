package com.lzpu.jwxt.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/**
 * 三个尺寸（2x2 / 4x2 / 4x4）的组件共用：样式与主题由 WidgetUpdater 按实例解析，这里只负责触发刷新。
 */
public abstract class BaseWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WidgetUpdater.refreshAll(context);
    }

    @Override
    public void onEnabled(Context context) {
        // 首次添加时 onUpdate 未必立刻到达，主动渲染一次占位内容
        WidgetUpdater.refreshAll(context);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // 组件被移除时顺手清掉它的样式记录
        WidgetStore.clearStyles(context, appWidgetIds);
        super.onDeleted(context, appWidgetIds);
    }
}