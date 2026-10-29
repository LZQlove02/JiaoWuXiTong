package com.lzpu.jwxt.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/**
 * 9 个桌面组件共用：样式与尺寸由 WidgetUpdater 按类映射，这里只负责触发刷新。
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