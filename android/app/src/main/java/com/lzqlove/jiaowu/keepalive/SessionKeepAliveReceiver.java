package com.lzqlove.jiaowu.keepalive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 会话保活闹钟。先排下一次，再起后台线程做一次轻量请求，
 * goAsync 保证进程不会在请求完成前被回收。会话是否仍存在由工作线程判断。
 * 只接受本应用自己排的闹钟广播；开机与覆盖安装后的补排由 SessionKeepAliveBootReceiver 负责。
 */
public class SessionKeepAliveReceiver extends BroadcastReceiver {

    public static final String ACTION_CHECK = "com.lzqlove.jiaowu.keepalive.CHECK";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_CHECK.equals(intent.getAction())) return;
        final Context app = context.getApplicationContext();
        if (!SessionKeepAliveStore.isEnabled(app)) {
            SessionKeepAliveScheduler.cancel(app);
            return;
        }
        // 先续上闹钟链，单次网络失败不会导致保活永久停止
        SessionKeepAliveScheduler.scheduleNext(app);
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SessionKeepAliveService.Result result = SessionKeepAliveService.checkNow(app);
                    // 只记录结果状态，不记录 Cookie、响应内容或账号信息
                    Log.i("JiaowuKeepAlive", "后台保活结果 " + result);
                } catch (RuntimeException ignored) {
                    // 后台保活失败静默处理，等下一次闹钟或用户打开 App
                } finally {
                    pending.finish();
                }
            }
        }, "session-keepalive").start();
    }
}
