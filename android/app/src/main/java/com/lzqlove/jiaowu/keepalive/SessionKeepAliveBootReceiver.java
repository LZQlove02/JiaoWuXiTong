package com.lzqlove.jiaowu.keepalive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机 / 覆盖安装后补排保活闹钟。
 * 单独拆出是为了让对外导出的接收器只做本地排程，不参与任何网络请求，
 * 避免第三方应用借开机广播通道触发保活请求。
 */
public class SessionKeepAliveBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        SessionKeepAliveScheduler.resumeIfEnabled(context.getApplicationContext());
    }
}
