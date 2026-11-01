package com.lzqlove.jiaowu.change;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机 / 覆盖安装后补排两个检查闹钟（仅当用户已开启任一后台提醒）。 */
public class ScheduleBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Context app = context.getApplicationContext();
            // 默认关闭时不再无意义地唤醒后台
            if (BackgroundCheckPolicy.isAnyEnabled(app)) {
                ScheduleCheckScheduler.scheduleAll(app);
            }
        }
    }
}