package com.lzpu.jwxt.change;

import android.content.Context;

/**
 * 后台检查是否还需要保留闹钟：课表变更、成绩提醒、通知/消息提醒任一开启即可。
 * 各功能在这里合并开关，避免互相取消闹钟。
 */
public final class BackgroundCheckPolicy {

    private BackgroundCheckPolicy() {
    }

    public static boolean isAnyEnabled(Context context) {
        return ScheduleChangeStore.isEnabled(context)
                || GradeAlertStore.isEnabled(context)
                || PortalAlertStore.isAnyEnabled(context);
    }
}
