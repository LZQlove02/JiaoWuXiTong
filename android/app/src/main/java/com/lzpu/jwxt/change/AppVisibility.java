package com.lzpu.jwxt.change;

/** App 是否在前台：后台检查据此决定「发系统通知」还是「只刷新应用内提醒条」。 */
public final class AppVisibility {

    private static volatile boolean foreground = false;

    private AppVisibility() {
    }

    public static void setForeground(boolean value) {
        foreground = value;
    }

    public static boolean isForeground() {
        return foreground;
    }
}