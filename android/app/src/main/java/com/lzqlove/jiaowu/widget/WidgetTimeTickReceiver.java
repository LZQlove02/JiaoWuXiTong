package com.lzqlove.jiaowu.widget;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

/**
 * 「更新于 今天 19:58 更新」这类相对时间会随时间变旧，但它只在有数据变化时才刷新。
 * 这里每小时整点后第 2 分钟做一次轻量重绘：只重新渲染三档组件的文案与网格，
 * 不联网、不写 payload（跨天重算仍由 {@link WidgetAlarmReceiver} 的 00:05 负责）。
 *
 * Doze 下 setAndAllowWhileIdle 可能被推迟，最坏情况「5 分钟前」要晚一小时才变成「今天 19:58」，
 * 这是省电与准确度的取舍；触发后 onReceive 会自己排下一次。
 */
public class WidgetTimeTickReceiver extends BroadcastReceiver {

    private static final int REQUEST_CODE = 1002;
    private static final int TICK_MINUTE = 2;

    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        schedule(app);
        WidgetUpdater.refreshAll(app);
    }

    // 排下一次「下一个整点 + 2 分钟」；单次闹钟链，触发后由 onReceive 续排。
    // 桌面一个实例都没有（例如开机后用户把组件全删了）就不排，避免空转唤醒。
    static void schedule(Context context) {
        if (!WidgetUpdater.hasAnyWidget(context)) return;
        PendingIntent pending = WidgetAlarms.pending(context, WidgetTimeTickReceiver.class, REQUEST_CODE);
        if (pending == null) return;

        Calendar next = Calendar.getInstance();
        next.set(Calendar.MINUTE, TICK_MINUTE);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.HOUR_OF_DAY, 1);
        }
        WidgetAlarms.setOnce(context, pending, next.getTimeInMillis(), "每小时刷新闹钟排程失败");
    }

    static void cancel(Context context) {
        WidgetAlarms.cancel(context, WidgetAlarms.pending(context, WidgetTimeTickReceiver.class, REQUEST_CODE));
    }
}
