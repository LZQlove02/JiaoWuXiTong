package com.lzqlove.jiaowu.keepalive;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.lzqlove.jiaowu.AuthCookieStore;

/**
 * 会话保活的单次闹钟链：约每 30 分钟触发一次，触发后由接收器排下一次。
 * 使用 setAndAllowWhileIdle（非精确），不申请 SCHEDULE_EXACT_ALARM 权限。
 */
public final class SessionKeepAliveScheduler {

    static final long INTERVAL_MS = 30L * 60 * 1000;
    private static final int REQUEST_CODE = 2200;

    private SessionKeepAliveScheduler() {
    }

    /** 开启时排下一次；本地是否仍有会话由闹钟触发时的接收器判断。 */
    public static void resumeIfEnabled(Context context) {
        if (!SessionKeepAliveStore.isEnabled(context)) {
            cancel(context);
            return;
        }
        scheduleNext(context);
    }

    static void scheduleNext(Context context) {
        Context app = context.getApplicationContext();
        boolean enabled = SessionKeepAliveStore.isEnabled(app);
        // 开关开着但本地已没有会话（例如统一认证到期后清过 Cookie）：排一次检查必然立刻失败，
        // 还会让设置页显示一个并不存在的「下次保活」。直接清掉排程，等重新登录再续上。
        if (!shouldSchedule(enabled, AuthCookieStore.hasStoredCookie(app))) {
            if (enabled) cancel(app);
            return;
        }
        AlarmManager manager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        PendingIntent pending = pending(app);
        if (pending == null) return;
        long triggerAt = nextTrigger(System.currentTimeMillis());
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            }
            // 只有真正排上才记录下次触发时间，避免展示一个并不存在的排程
            SessionKeepAliveStore.setNextCheckAt(app, triggerAt);
        } catch (RuntimeException ignored) {
            // 个别 ROM 限制后台闹钟时静默失败，下一次打开 App 会重新排程
        }
    }

    public static void cancel(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager manager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) {
            PendingIntent pending = pending(app);
            if (pending != null) manager.cancel(pending);
        }
        // 取消后不再有排程：只清 nextCheckAt，保留 lastCheckAt / lastResult 供设置页展示历史
        SessionKeepAliveStore.setNextCheckAt(app, 0L);
    }

    /** 单测用：下一次触发时间固定为 30 分钟后。 */
    static long nextTrigger(long now) {
        return now + INTERVAL_MS;
    }

    /** 只有「开关已开 + 本地确有会话」才值得排程；抽成纯函数便于单测。 */
    static boolean shouldSchedule(boolean enabled, boolean hasStoredCookie) {
        return enabled && hasStoredCookie;
    }

    private static PendingIntent pending(Context context) {
        Intent intent = new Intent(context, SessionKeepAliveReceiver.class);
        intent.setAction(SessionKeepAliveReceiver.ACTION_CHECK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }
}
