package com.lzqlove.jiaowu.change;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.lzqlove.jiaowu.R;
import com.lzqlove.jiaowu.widget.WidgetDeepLink;

import java.util.List;

/**
 * 成绩提醒通知：只在不合格成绩新出现时发送；锁屏使用脱敏文案。
 */
public final class GradeAlertNotifier {

    static final String CHANNEL_ID = "grade_alert";
    private static final int MAX_NOTIFICATIONS = 3;
    private static final int ID_BASE = 8100;

    private GradeAlertNotifier() {
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "成绩提醒",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("仅在出现不及格成绩时提醒");
        manager.createNotificationChannel(channel);
    }

    static int notifyFailures(Context context, List<GradeRecord> failures) {
        if (failures == null || failures.isEmpty()) return 0;
        if (!GradeAlertStore.isEnabled(context)) return 0;
        if (!ScheduleChangeNotifier.notificationsEnabled(context)) return 0;
        ensureChannel(context);
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return 0;

        int posted = 0;
        for (GradeRecord record : failures) {
            if (posted >= MAX_NOTIFICATIONS) break;
            if (record == null || record.key.isEmpty()) continue;
            post(context, manager, notificationId(record.key), "成绩提醒",
                    record.courseName + " 未通过");
            posted += 1;
        }
        return posted;
    }

    /** 关闭开关或退出登录时，只取消本功能发过的通知。 */
    static void cancelAll(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        GradeAlertStore.Baseline baseline = GradeAlertStore.readBaseline(context);
        if (baseline == null) return;
        for (String key : baseline.courses.keySet()) {
            manager.cancel(notificationId(key));
        }
    }

    private static void post(Context context, NotificationManager manager, int id,
                             String title, String body) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_calendar)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(WidgetDeepLink.open(context, "grades", id))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

        // 锁屏不展示课程名，避免把成绩信息暴露在公共环境
        NotificationCompat.Builder publicBuilder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification_calendar)
                        .setContentTitle("成绩有更新")
                        .setContentText("打开应用查看详情")
                        .setAutoCancel(true)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(WidgetDeepLink.open(context, "grades", id));
        builder.setPublicVersion(publicBuilder.build());
        try {
            manager.notify(id, builder.build());
        } catch (RuntimeException error) {
            // 权限在发送瞬间被撤销时不能崩后台线程
        }
    }

    private static int notificationId(String key) {
        return ID_BASE + (key.hashCode() & 0x7FFFFFFF) % 500;
    }
}
