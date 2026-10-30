package com.lzpu.jwxt.change;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机 / 覆盖安装后补排两个检查闹钟（用户强停 App 会清掉闹钟，下次打开 App 再补）。 */
public class ScheduleBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            ScheduleCheckScheduler.scheduleAll(context.getApplicationContext());
        }
    }
}