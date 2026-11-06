package com.lzqlove.jiaowu;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
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

    /** 设置页「保持登录」开关：首次登录说明同意后默认开启，约每 30 分钟后台维持一次会话。 */
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

    /** 查询系统是否已把本应用列入电池优化白名单（部分 ROM 深睡时会冻结闹钟）。 */
    @PluginMethod
    public void getBatteryOptimizationState(PluginCall call) {
        JSObject result = new JSObject();
        result.put("ignoring", isIgnoringBatteryOptimizations());
        call.resolve(result);
    }

    /** 打开系统的电池优化白名单申请页；是否真正放行由用户在系统弹窗中确认。 */
    @PluginMethod
    public void requestBatteryOptimizationExemption(PluginCall call) {
        if (isIgnoringBatteryOptimizations()) {
            call.resolve(batteryState(true));
            return;
        }
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("当前没有可用的界面");
            return;
        }
        Intent intent = new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getContext().getPackageName()));
        try {
            activity.startActivity(intent);
            call.resolve(batteryState(false));
        } catch (RuntimeException error) {
            call.reject("系统不支持直接申请，请在设置中手动允许后台运行");
        }
    }

    private JSObject batteryState(boolean ignoring) {
        JSObject result = new JSObject();
        result.put("ignoring", ignoring);
        return result;
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager power = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        if (power == null) return false;
        return power.isIgnoringBatteryOptimizations(getContext().getPackageName());
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
