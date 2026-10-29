package com.lzpu.jwxt.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 组件角落「样式」按钮：点一下按 玻璃 → 透明 → 不透明 循环，
 * 只改被点的那一个组件实例，不动其它组件。
 */
public class WidgetStyleReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !WidgetDeepLink.ACTION_CYCLE_STYLE.equals(intent.getAction())) return;
        int appWidgetId = intent.getIntExtra(WidgetDeepLink.EXTRA_APP_WIDGET_ID, -1);
        if (appWidgetId <= 0) return;

        String current = WidgetStore.readStyle(context, appWidgetId);
        String next = WidgetStore.nextStyle(current);
        WidgetStore.writeStyle(context, appWidgetId, next);
        Log.i("LzpuWidget", "切换样式 id=" + appWidgetId + " " + current + " -> " + next);
        WidgetUpdater.refreshAll(context);
    }
}