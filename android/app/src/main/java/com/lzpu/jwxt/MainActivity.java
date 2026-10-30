package com.lzpu.jwxt;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.getcapacitor.BridgeActivity;
import com.lzpu.jwxt.change.AppVisibility;
import com.lzpu.jwxt.change.ScheduleChangeBridge;
import com.lzpu.jwxt.change.ScheduleChangeNotifier;
import com.lzpu.jwxt.change.ScheduleCheckScheduler;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(JwxtHttpPlugin.class);
        registerPlugin(SsoLoginPlugin.class);
        registerPlugin(AppThemePlugin.class);
        registerPlugin(WidgetBridgePlugin.class);
        registerPlugin(ScheduleChangeBridge.class);
        super.onCreate(savedInstanceState);
        // 用户强停 App 会清掉闹钟，每次打开 App 补排一次
        ScheduleChangeNotifier.ensureChannel(this);
        ScheduleCheckScheduler.scheduleAll(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        AppVisibility.setForeground(true);
    }

    @Override
    public void onStop() {
        AppVisibility.setForeground(false);
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 桌面小组件点击会带 lzpu:// 深链，这里留痕便于真机排查
        Log.d("LzpuWidget", "onNewIntent action=" + (intent == null ? "null" : intent.getAction())
            + " data=" + (intent == null || intent.getData() == null ? "null" : intent.getDataString()));
    }
}