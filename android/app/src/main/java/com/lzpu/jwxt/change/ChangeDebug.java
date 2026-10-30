package com.lzpu.jwxt.change;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

/** 只在 debug 包输出诊断日志；release 不写网络与响应内容。 */
final class ChangeDebug {

    private static final String TAG = "LzpuChange";

    private ChangeDebug() {
    }

    static boolean enabled(Context context) {
        return context != null
                && (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    static void warn(Context context, String message) {
        if (enabled(context)) Log.w(TAG, message);
    }
}