package com.lzpu.jwxt.change;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 后台检查闹钟。onReceive 里先排下一天，再起后台线程联网对账；
 * goAsync 保证进程不会在检查完成前被回收。
 */
public class ScheduleCheckReceiver extends BroadcastReceiver {

    public static final String ACTION_CHECK = "com.lzpu.jwxt.change.CHECK";
    public static final String EXTRA_SLOT = "slot";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final int slot = intent == null ? 0 : intent.getIntExtra(EXTRA_SLOT, 0);
        if (slot >= ScheduleCheckScheduler.SLOT_MORNING && slot <= ScheduleCheckScheduler.SLOT_NOON) {
            ScheduleCheckScheduler.scheduleSlot(app, slot);
        }
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ScheduleChangeService.checkNow(app);
                } catch (RuntimeException error) {
                    ChangeDebug.warn(app, "后台检查失败: " + error.getClass().getSimpleName());
                } finally {
                    pending.finish();
                }
            }
        }, "schedule-change-check").start();
    }
}