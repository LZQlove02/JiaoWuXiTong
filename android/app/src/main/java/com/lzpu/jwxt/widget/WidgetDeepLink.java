package com.lzpu.jwxt.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import com.lzpu.jwxt.MainActivity;

/**
 * 组件点击 → 深链 → MainActivity → App.vue 路由。
 * 每个热区使用独立 requestCode，避免 PendingIntent 被 filterEquals 判重后串页。
 */
public final class WidgetDeepLink {

    public static final String SCHEME = "lzpu";

    public static final int REQUEST_NEXT = 1;
    public static final int REQUEST_TODAY = 2;

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
}