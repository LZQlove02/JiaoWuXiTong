package com.lzqlove.jiaowu.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 组件用的单次闹钟：PendingIntent 构造、{@code setAndAllowWhileIdle} 与取消。
 * 跨天重算（{@link WidgetAlarmReceiver}）和每小时重绘（{@link WidgetTimeTickReceiver}）
 * 的 flags、API 分支、异常兜底都走这里，避免两处各写一份。
 */
final class WidgetAlarms {

    private static final String TAG = "JiaowuWidget";

    private WidgetAlarms() {
    }

    static PendingIntent pending(Context context, Class<? extends BroadcastReceiver> receiver, int requestCode) {
        Intent intent = new Intent(context, receiver);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    // 单次闹钟：setInexactRepeating 会给到最长 18h 的窗口，可能拖到第二天下午才响；
    // setAndAllowWhileIdle 的窗口通常只有几分钟，触发后由接收方自己排下一次。
    static void setOnce(Context context, PendingIntent pending, long triggerAtMillis, String failureMessage) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending);
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending);
            }
        } catch (RuntimeException error) {
            Log.w(TAG, failureMessage, error);
        }
    }

    static void cancel(Context context, PendingIntent pending) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null || pending == null) return;
        manager.cancel(pending);
    }
}
