package com.lzqlove.jiaowu.change;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/**
 * 每天两次单次闹钟（默认 07:10 / 12:50，可在设置中修改），触发后各自排下一天。
 * 用 setAndAllowWhileIdle（非精确），避免申请 SCHEDULE_EXACT_ALARM 权限。
 */
public final class ScheduleCheckScheduler {

    static final int SLOT_MORNING = 1;
    static final int SLOT_NOON = 2;
    private static final int REQUEST_BASE = 2100;

    private ScheduleCheckScheduler() {
    }

    public static void scheduleAll(Context context) {
        Context app = context.getApplicationContext();
        if (!BackgroundCheckPolicy.isAnyEnabled(app)) {
            cancelAll(app);
            return;
        }
        scheduleSlot(app, SLOT_MORNING);
        scheduleSlot(app, SLOT_NOON);
    }

    public static void cancelAll(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager manager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        cancelSlot(manager, app, SLOT_MORNING);
        cancelSlot(manager, app, SLOT_NOON);
    }

    static void scheduleSlot(Context context, int slot) {
        if (slot < SLOT_MORNING || slot > SLOT_NOON) return;
        Context app = context.getApplicationContext();
        if (!BackgroundCheckPolicy.isAnyEnabled(app)) return;
        AlarmManager manager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;

        long triggerAt = nextTrigger(slot, System.currentTimeMillis(), CheckTimeStore.read(app));
        PendingIntent pending = pending(app, slot);
        if (pending == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            }
        } catch (RuntimeException error) {
            ChangeDebug.warn(app, "课表检查闹钟排程失败 slot=" + slot
                    + " " + error.getClass().getSimpleName());
        }
    }

    private static void cancelSlot(AlarmManager manager, Context context, int slot) {
        PendingIntent pending = pending(context, slot);
        if (pending != null) manager.cancel(pending);
    }

    private static PendingIntent pending(Context context, int slot) {
        Intent intent = new Intent(context, ScheduleCheckReceiver.class);
        intent.setAction(ScheduleCheckReceiver.ACTION_CHECK);
        intent.putExtra(ScheduleCheckReceiver.EXTRA_SLOT, slot);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, REQUEST_BASE + slot, intent, flags);
    }

    /** 单测用：按给定时段计算下一次触发；早于当前时间则顺延到明天。 */
    static long nextTrigger(int slot, long now, CheckTimes times) {
        int minutes = slot == SLOT_MORNING ? times.morningMinutes : times.noonMinutes;
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(now);
        next.set(Calendar.HOUR_OF_DAY, minutes / 60);
        next.set(Calendar.MINUTE, minutes % 60);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= now) {
            next.add(Calendar.DAY_OF_MONTH, 1);
        }
        return next.getTimeInMillis();
    }
}