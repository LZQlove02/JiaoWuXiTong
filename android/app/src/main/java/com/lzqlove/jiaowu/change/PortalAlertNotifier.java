package com.lzqlove.jiaowu.change;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.lzqlove.jiaowu.R;
import com.lzqlove.jiaowu.widget.WidgetDeepLink;

/**
 * 新通知/新消息提醒：合并成一条摘要，锁屏不展示标题。
 */
public final class PortalAlertNotifier {

    static final String CHANNEL_ID = "portal_update";
    private static final int NOTIFICATION_ID = 8300;

    private PortalAlertNotifier() {
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "教务通知与消息",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("新通知、新消息摘要提醒");
        manager.createNotificationChannel(channel);
    }

    static int notifyUpdates(Context context, int noticeCount, int messageCount) {
        if (noticeCount <= 0 && messageCount <= 0) return 0;
        if (!ScheduleChangeNotifier.notificationsEnabled(context)) return 0;
        ensureChannel(context);
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return 0;

        String body;
        if (noticeCount > 0 && messageCount > 0) {
            body = noticeCount + " 条新通知，" + messageCount + " 条新消息";
        } else if (noticeCount > 0) {
            body = noticeCount + " 条新通知";
        } else {
            body = messageCount + " 条新消息";
        }

        String path = noticeCount > 0 ? "notices" : "messages";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_calendar)
                .setContentTitle("教务有更新")
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(WidgetDeepLink.open(context, path, NOTIFICATION_ID))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

        NotificationCompat.Builder publicBuilder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification_calendar)
                        .setContentTitle("教务有更新")
                        .setContentText("打开应用查看详情")
                        .setAutoCancel(true)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(WidgetDeepLink.open(context, path, NOTIFICATION_ID));
        builder.setPublicVersion(publicBuilder.build());
        try {
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (RuntimeException error) {
            // 权限在发送瞬间被撤销时不能崩后台线程
            return 0;
        }
        return 1;
    }

    static void cancel(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }
}
