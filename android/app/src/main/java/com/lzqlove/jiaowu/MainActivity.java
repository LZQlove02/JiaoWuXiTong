package com.lzqlove.jiaowu;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;
import com.lzqlove.jiaowu.change.AppVisibility;
import com.lzqlove.jiaowu.change.ScheduleChangeBridge;
import com.lzqlove.jiaowu.change.GradeAlertNotifier;
import com.lzqlove.jiaowu.change.PortalAlertNotifier;
import com.lzqlove.jiaowu.change.ScheduleChangeNotifier;
import com.lzqlove.jiaowu.change.ScheduleCheckScheduler;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(JwxtHttpPlugin.class);
        registerPlugin(SsoLoginPlugin.class);
        registerPlugin(AppThemePlugin.class);
        registerPlugin(WidgetBridgePlugin.class);
        registerPlugin(ScheduleChangeBridge.class);
        registerPlugin(SupportBridgePlugin.class);
        super.onCreate(savedInstanceState);
        // 用户强停 App 会清掉闹钟，每次打开 App 补排一次
        ScheduleChangeNotifier.ensureChannel(this);
        GradeAlertNotifier.ensureChannel(this);
        PortalAlertNotifier.ensureChannel(this);
        ScheduleCheckScheduler.scheduleAll(this);
        applySystemFontScale();
    }

    /**
     * 文字大小跟随系统无障碍设置。上限 1.5 倍，避免固定像素的课表网格在大字体下溢出；
     * 系统字号变化会让 Activity 重建，onCreate 时重新读取即可。
     */
    private void applySystemFontScale() {
        try {
            WebView webView = getBridge() == null ? null : getBridge().getWebView();
            if (webView == null) return;
            float scale = getResources().getConfiguration().fontScale;
            float clamped = Math.min(Math.max(scale, 0.85f), 1.5f);
            webView.getSettings().setTextZoom(Math.round(clamped * 100));
        } catch (RuntimeException error) {
            // WebView 未就绪时忽略，使用系统默认缩放
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        AppVisibility.setForeground(true);
        // 每次回到前台补排一次：退出登录会取消闹钟，弱网重新登录时也能恢复检查
        ScheduleCheckScheduler.scheduleAll(this);
    }

    @Override
    public void onStop() {
        AppVisibility.setForeground(false);
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 桌面小组件点击会带 jiaowu:// 深链，这里留痕便于真机排查
        Log.d("JiaowuWidget", "onNewIntent action=" + (intent == null ? "null" : intent.getAction())
            + " data=" + (intent == null || intent.getData() == null ? "null" : intent.getDataString()));
    }
}