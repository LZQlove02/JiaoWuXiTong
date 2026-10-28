package com.lzpu.jwxt;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(JwxtHttpPlugin.class);
        registerPlugin(SsoLoginPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
