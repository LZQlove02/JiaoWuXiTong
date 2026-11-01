package com.lzqlove.jiaowu;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * 让系统导航栏跟随应用内主题，避免深色模式下底部出现浅色条。
 */
@CapacitorPlugin(name = "AppTheme")
public class AppThemePlugin extends Plugin {

    @PluginMethod
    public void setNavigationBar(PluginCall call) {
        Boolean value = call.getBoolean("dark", false);
        final boolean dark = value != null && value;
        String requested = call.getString("color");
        // 只接受 #RRGGBB，非法值回退到主题背景色，避免 Color.parseColor 抛异常
        String fallback = requested != null && requested.matches("#[0-9a-fA-F]{6}")
            ? requested
            : (dark ? "#0f1216" : "#f5f6f8");
        // API 26 以下不支持深色导航栏按键，浅色底会导致按键不可见，统一改用深色底
        final String color = Build.VERSION.SDK_INT < Build.VERSION_CODES.O && !dark
            ? "#1a1c1f"
            : fallback;
        final Activity activity = getActivity();
        activity.runOnUiThread(() -> {
            Window window = activity.getWindow();
            window.setNavigationBarColor(Color.parseColor(color));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.setNavigationBarDividerColor(Color.TRANSPARENT);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setNavigationBarContrastEnforced(false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                View decor = window.getDecorView();
                int flags = decor.getSystemUiVisibility();
                if (dark) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                decor.setSystemUiVisibility(flags);
            }
            call.resolve();
        });
    }
}
