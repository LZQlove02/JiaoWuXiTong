package com.lzqlove.jiaowu;

import android.util.Log;

/**
 * 诊断日志开关：发布包不输出网络与 Cookie 细节（方案 §7.5）。
 * 只有 debug 包才会真正写 logcat；release 包即使被手动改配置也不会有输出。
 */
public final class DebugLog {

    private DebugLog() {
    }

    public static void i(String tag, String message) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message);
        }
    }

    public static void d(String tag, String message) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message);
        }
    }
}
