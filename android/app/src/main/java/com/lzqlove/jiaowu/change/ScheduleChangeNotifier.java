package com.lzqlove.jiaowu.change;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.lzqlove.jiaowu.R;
import com.lzqlove.jiaowu.widget.WidgetDeepLink;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 变更通知：一处变更一条，单次最多 5 条；点开进变更列表。
 * App 在前台时由 Service 跳过发通知，交给应用内提醒条。
 */
public final class ScheduleChangeNotifier {

    static final String CHANNEL_ID = "schedule_change";
    private static final int EXPIRED_NOTIFICATION_ID = 9001;
    private static final int MAX_NOTIFICATIONS = 5;
    private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private ScheduleChangeNotifier() {
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_schedule_change),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("停课、调课与新增课程提醒");
        manager.createNotificationChannel(channel);
    }

    static boolean notificationsEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return manager.areNotificationsEnabled();
        }
        return true;
    }

    static int notifyChanges(Context context, List<ChangeEntry> entries) {
        if (entries == null || entries.isEmpty()) return 0;
        if (!ScheduleChangeStore.isEnabled(context) || !notificationsEnabled(context)) return 0;
        ensureChannel(context);
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return 0;

        List<String> notifiedIds = new ArrayList<>();
        int posted = 0;
        for (ChangeEntry entry : entries) {
            if (posted >= MAX_NOTIFICATIONS) break;
            if (!entry.isRemindable() || entry.notified || entry.read) continue;
            post(context, manager, notificationId(entry), title(entry), body(entry),
                    "changes", 4100 + (entry.id.hashCode() & 0x7FFFFFFF) % 500);
            notifiedIds.add(entry.id);
            posted += 1;
        }
        if (!notifiedIds.isEmpty()) {
            ScheduleChangeStore.markNotified(context, notifiedIds);
        }
        return posted;
    }

    static boolean notifySessionExpired(Context context) {
        if (!ScheduleChangeStore.isEnabled(context) || !notificationsEnabled(context)) return false;
        ensureChannel(context);
        String today = DateUtils.dayKey(DateUtils.isoNow(Calendar.getInstance()));
        if (today.equals(ScheduleChangeStore.readLastSessionExpiredDate(context))) return false;
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return false;
        post(context, manager, EXPIRED_NOTIFICATION_ID, "登录已失效", "课表变更提醒已暂停，点此重新登录",
                "login", 4600);
        ScheduleChangeStore.writeLastSessionExpiredDate(context, today);
        return true;
    }

    /** 已读或关闭开关时清掉本功能发过的通知。 */
    static void cancelAll(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.cancel(EXPIRED_NOTIFICATION_ID);
        for (ChangeEntry entry : ScheduleChangeStore.readEntries(context)) {
            if (entry.notified) {
                manager.cancel(notificationId(entry));
            }
        }
    }

    private static void post(Context context, NotificationManager manager, int id, String title,
                             String body, String deepLinkPath, int requestCode) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_calendar)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(WidgetDeepLink.open(context, deepLinkPath, requestCode))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        // 锁屏只显示脱敏版本，课程名、教室等个人信息不进入锁屏通知
        NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_calendar)
                .setContentTitle("课表有更新")
                .setContentText("打开应用查看详情")
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(WidgetDeepLink.open(context, deepLinkPath, requestCode));
        builder.setPublicVersion(publicBuilder.build());
        Notification notification = builder.build();
        try {
            manager.notify(id, notification);
        } catch (RuntimeException error) {
            // 权限在请求与发送之间被撤销时不能崩后台线程
        }
    }

    private static int notificationId(ChangeEntry entry) {
        return 7000 + (entry.id.hashCode() & 0x7FFFFFFF) % 1000;
    }

    private static String title(ChangeEntry entry) {
        return entry.courseName + " · " + typeLabel(entry.type);
    }

    private static String typeLabel(String type) {
        if (ChangeEntry.TYPE_CANCEL.equals(type)) return "已停课";
        if (ChangeEntry.TYPE_MOVE.equals(type)) return "调课";
        if (ChangeEntry.TYPE_ADD.equals(type)) return "新增";
        return "换教室";
    }

    private static String body(ChangeEntry entry) {
        if (ChangeEntry.TYPE_ROOM.equals(entry.type)) {
            return roomLabel(entry.from) + " → " + roomLabel(entry.to);
        }
        if (ChangeEntry.TYPE_CANCEL.equals(entry.type)) {
            return snapshotLabel(entry.from) + weeksSuffix(entry.from);
        }
        if (ChangeEntry.TYPE_ADD.equals(entry.type)) {
            return snapshotLabel(entry.to) + weeksSuffix(entry.to);
        }
        // 调课：只变周次时不必重复时间，直接给周次前后
        if (entry.from != null && entry.to != null && !timeChanged(entry)) {
            return entry.from.weeksLabel + " → " + entry.to.weeksLabel;
        }
        return "原：" + snapshotLabel(entry.from) + " → 新：" + snapshotLabel(entry.to);
    }

    private static boolean timeChanged(ChangeEntry entry) {
        return entry.from.weekday != entry.to.weekday
                || entry.from.startSection != entry.to.startSection
                || entry.from.endSection != entry.to.endSection;
    }

    private static String weeksSuffix(ChangeEntry.Snapshot snapshot) {
        if (snapshot == null || snapshot.weeksLabel.isEmpty()) return "";
        return " · " + snapshot.weeksLabel;
    }

    private static String snapshotLabel(ChangeEntry.Snapshot snapshot) {
        if (snapshot == null) return "";
        String sections = snapshot.startSection == snapshot.endSection
                ? snapshot.startSection + " 节"
                : snapshot.startSection + "-" + snapshot.endSection + " 节";
        String day = snapshot.weekday >= 1 && snapshot.weekday <= 7
                ? WEEKDAYS[snapshot.weekday - 1] : "";
        String room = snapshot.room.isEmpty() ? "" : " · " + snapshot.room;
        return day + " " + sections + room;
    }

    private static String roomLabel(ChangeEntry.Snapshot snapshot) {
        return snapshot == null || snapshot.room.isEmpty() ? "未写教室" : snapshot.room;
    }
}