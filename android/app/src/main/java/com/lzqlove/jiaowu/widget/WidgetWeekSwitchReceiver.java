package com.lzqlove.jiaowu.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 4x4 标题行的「下周 › / 本周 ‹」：点一下只翻转被点的那个实例（0 ↔ 1），
 * 不打开 App、不联网，翻完立刻重绘。
 */
public class WidgetWeekSwitchReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !WidgetDeepLink.ACTION_SWITCH_WEEK.equals(intent.getAction())) return;
        int appWidgetId = intent.getIntExtra(WidgetDeepLink.EXTRA_APP_WIDGET_ID, -1);
        if (appWidgetId <= 0) return;

        int current = WidgetStore.readWeekOffset(context, appWidgetId);
        int next = current == WidgetWeekDisplay.NEXT_WEEK
            ? WidgetWeekDisplay.THIS_WEEK
            : WidgetWeekDisplay.NEXT_WEEK;
        WidgetStore.writeWeekOffset(context, appWidgetId, next);
        Log.i("JiaowuWidget", "切换周次 id=" + appWidgetId + " offset=" + current + " -> " + next);
        WidgetUpdater.refreshAll(context);
    }
}
