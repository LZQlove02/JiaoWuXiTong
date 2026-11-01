package com.lzqlove.jiaowu.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

/**
 * 跨天重算：每天 00:05 由 AlarmManager 唤醒，按 week[] + 当前时间重新算 today / next /
 * remainingToday 并写回 payload，然后刷新三个组件；开机后重排闹钟并刷新一次。
 *
 * 只做本地重算，不联网、不发通知。
 */
public class WidgetAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "JiaowuWidget";
    private static final int REQUEST_CODE = 1001;
    private static final int MIDNIGHT_HOUR = 0;
    private static final int MIDNIGHT_MINUTE = 5;

    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        schedule(app);
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // 开机后系统会清掉闹钟，这里只补排 + 用现有快照刷一次
            WidgetUpdater.refreshAll(app);
            return;
        }
        recompute(app);
    }

    // 排下一次 00:05；重复闹钟由系统自己往后滚，这里只在首次/开机时调用
    static void schedule(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, WidgetAlarmReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
        if (pending == null) return;

        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, MIDNIGHT_HOUR);
        next.set(Calendar.MINUTE, MIDNIGHT_MINUTE);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_MONTH, 1);
        }
        try {
            // 单次闹钟：setInexactRepeating 会给到最长 18h 的窗口，可能拖到第二天下午才响。
            // 这里用 setAndAllowWhileIdle，窗口通常只有几分钟；触发后 onReceive 会再排下一天。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pending);
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pending);
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "跨天闹钟排程失败", error);
        }
    }

    static void cancel(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, WidgetAlarmReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
        if (pending != null) manager.cancel(pending);
    }

    private static void recompute(Context app) {
        String raw = WidgetStore.readPayload(app);
        WidgetPayload payload = WidgetPayload.parse(raw);
        if (!payload.loggedIn || !payload.scheduleLoaded || payload.week.isEmpty()) return;

        // 跨周：week[] 还是上一周的课，重算只会把上周的课当成今天上，直接降级
        if (WidgetScheduleDeriver.isStale(payload, Calendar.getInstance())) {
            markStale(app, raw);
            return;
        }

        WidgetScheduleDeriver.Result result =
            WidgetScheduleDeriver.derive(payload, Calendar.getInstance());
        try {
            JSONObject root = new JSONObject(raw);
            root.put("today", toArray(result.today));
            root.put("next", result.next == null ? JSONObject.NULL : toObject(result.next));
            root.put("remainingToday", result.remainingToday);
            // 同一周内的重算，陈旧标记归零
            root.put("stale", false);
            WidgetStore.writePayload(app, root.toString());
        } catch (Exception error) {
            Log.w(TAG, "跨天重算写回失败", error);
            return;
        }
        Log.i(TAG, "跨天重算 today=" + result.today.size() + " remaining=" + result.remainingToday);
        WidgetUpdater.refreshAll(app);
    }

    // 跨周降级：清空 today / next（否则 2x2 还会显示上周的「下一节」），
    // 只置 stale 标记，由三个组件统一显示「打开 App 同步」。
    private static void markStale(Context app, String raw) {
        try {
            JSONObject root = new JSONObject(raw);
            root.put("today", new JSONArray());
            root.put("next", JSONObject.NULL);
            root.put("remainingToday", 0);
            root.put("stale", true);
            WidgetStore.writePayload(app, root.toString());
        } catch (Exception error) {
            Log.w(TAG, "跨周降级写回失败", error);
            return;
        }
        Log.i(TAG, "快照已跨周，组件改为提示同步");
        WidgetUpdater.refreshAll(app);
    }

    private static JSONArray toArray(List<WidgetPayload.ClassItem> items) throws Exception {
        JSONArray array = new JSONArray();
        for (WidgetPayload.ClassItem item : items) array.put(toObject(item));
        return array;
    }

    private static JSONObject toObject(WidgetPayload.ClassItem item) throws Exception {
        JSONObject object = new JSONObject();
        object.put("dayLabel", item.dayLabel);
        object.put("start", item.start);
        object.put("end", item.end);
        object.put("startTime", item.startTime);
        object.put("endTime", item.endTime);
        object.put("name", item.name);
        object.put("room", item.room);
        return object;
    }
}