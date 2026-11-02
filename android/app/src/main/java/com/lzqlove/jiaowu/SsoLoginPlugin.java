package com.lzqlove.jiaowu;

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
import com.lzqlove.jiaowu.keepalive.SessionKeepAliveScheduler;
import com.lzqlove.jiaowu.keepalive.SessionKeepAliveStore;

@CapacitorPlugin(name = "SsoLogin")
public class SsoLoginPlugin extends Plugin {

    @PluginMethod
    public void login(PluginCall call) {
        Intent intent = new Intent(getContext(), SsoLoginActivity.class);
        startActivityForResult(call, intent, "handleLoginResult");
    }

    /** 明确退出登录：清除 CASTGC 会话与挑战 Cookie，保证可切换账号。 */
    @PluginMethod
    public void clearSession(PluginCall call) {
        CookieManager manager = CookieManager.getInstance();
        SsoCookieCleaner.expireSsoSession(manager);
        SsoCookieCleaner.expireZfjw(manager);
        manager.flush();
        SessionKeepAliveScheduler.cancel(getContext());
        call.resolve();
    }

    /** 设置页「保持登录」开关：默认关闭，开启后约每 30 分钟后台维持一次会话。 */
    @PluginMethod
    public void setKeepAlive(PluginCall call) {
        Boolean enabled = call.getBoolean("enabled");
        SessionKeepAliveStore.setEnabled(getContext(), enabled != null && enabled);
        if (enabled != null && enabled) {
            SessionKeepAliveScheduler.resumeIfEnabled(getContext());
        } else {
            SessionKeepAliveScheduler.cancel(getContext());
        }
        call.resolve(keepAliveState());
    }

    @PluginMethod
    public void getKeepAliveState(PluginCall call) {
        call.resolve(keepAliveState());
    }

    private JSObject keepAliveState() {
        JSObject result = new JSObject();
        boolean enabled = SessionKeepAliveStore.isEnabled(getContext());
        String cookie = AuthCookieStore.load(getContext());
        result.put("enabled", enabled);
        result.put("active", enabled && cookie != null && !cookie.isEmpty());
        result.put("intervalMinutes", 30);
        return result;
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
