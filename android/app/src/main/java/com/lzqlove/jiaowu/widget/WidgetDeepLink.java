package com.lzqlove.jiaowu.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import com.lzqlove.jiaowu.MainActivity;

/**
 * 组件点击 → 深链 → MainActivity → App.vue 路由。
 * 每个热区使用独立 requestCode，避免 PendingIntent 被 filterEquals 判重后串页。
 */
public final class WidgetDeepLink {

    public static final String SCHEME = "jiaowu";

    public static final int REQUEST_NEXT = 1;
    public static final int REQUEST_TODAY = 2;
    public static final int REQUEST_WEEK_SCHEDULE = 3;

    // 组件内「样式」按钮：广播给自己换样式，不打开 App
    public static final String ACTION_CYCLE_STYLE = "com.lzqlove.jiaowu.widget.CYCLE_STYLE";
    // 4x4 标题行的「下周 › / 本周 ‹」：广播给自己翻页，同样不打开 App
    public static final String ACTION_SWITCH_WEEK = "com.lzqlove.jiaowu.widget.SWITCH_WEEK";
    public static final String EXTRA_APP_WIDGET_ID = "app_widget_id";

    private WidgetDeepLink() {
    }

    public static PendingIntent open(Context context, String path, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(SCHEME + "://" + path));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }

    // 每个实例用 1000 + appWidgetId 作 requestCode，避免多个组件共用一个 PendingIntent
    public static PendingIntent cycleStyle(Context context, int appWidgetId) {
        Intent intent = new Intent(context, WidgetStyleReceiver.class);
        intent.setAction(ACTION_CYCLE_STYLE);
        intent.putExtra(EXTRA_APP_WIDGET_ID, appWidgetId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, 1000 + appWidgetId, intent, flags);
    }

    // 翻周与样式用不同的 requestCode 段：2000 + appWidgetId，两者不能互相顶掉
    public static PendingIntent switchWeek(Context context, int appWidgetId) {
        Intent intent = new Intent(context, WidgetWeekSwitchReceiver.class);
        intent.setAction(ACTION_SWITCH_WEEK);
        intent.putExtra(EXTRA_APP_WIDGET_ID, appWidgetId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, 2000 + appWidgetId, intent, flags);
    }
}
