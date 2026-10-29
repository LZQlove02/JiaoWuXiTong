package com.lzpu.jwxt;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(JwxtHttpPlugin.class);
        registerPlugin(SsoLoginPlugin.class);
        registerPlugin(AppThemePlugin.class);
        registerPlugin(WidgetBridgePlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 桌面小组件点击会带 lzpu:// 深链，这里留痕便于真机排查
        Log.d("LzpuWidget", "onNewIntent action=" + (intent == null ? "null" : intent.getAction())
            + " data=" + (intent == null || intent.getData() == null ? "null" : intent.getDataString()));
    }
}