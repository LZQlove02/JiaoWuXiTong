package com.lzpu.jwxt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.CookieManager;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "SsoLogin")
public class SsoLoginPlugin extends Plugin {

    @PluginMethod
    public void login(PluginCall call) {
        Intent intent = new Intent(getContext(), SsoLoginActivity.class);
        startActivityForResult(call, intent, "handleLoginResult");
    }

    /** 明确退出登录：清除 CASTGC 一周会话与挑战 Cookie，保证可切换账号。 */
    @PluginMethod
    public void clearSession(PluginCall call) {
        CookieManager manager = CookieManager.getInstance();
        SsoCookieCleaner.expireSsoSession(manager);
        SsoCookieCleaner.expireZfjw(manager);
        manager.flush();
        call.resolve();
    }

    @ActivityCallback
    private void handleLoginResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Bundle data = result.getData().getExtras();
            if (data == null || data.getString("cookieHeader", "").isEmpty()) {
                call.reject("未获取到登录会话");
                return;
            }
            JSObject ret = new JSObject();
            ret.put("cookieHeader", data.getString("cookieHeader", ""));
            ret.put("finalUrl", data.getString("finalUrl", ""));
            call.resolve(ret);
        } else {
            call.reject("登录已取消");
        }
    }
}
