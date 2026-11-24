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
import com.lzqlove.jiaowu.keepalive.SessionKeepAliveService;
import com.lzqlove.jiaowu.keepalive.SessionKeepAliveStore;

import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

@CapacitorPlugin(name = "SsoLogin")
public class SsoLoginPlugin extends Plugin {

    private static final int DEFAULT_SILENT_TIMEOUT_MS = 12000;
    /** 同一时刻只允许一次补保活，避免快速切前台并发打网络。 */
    private static final AtomicBoolean topUpRunning = new AtomicBoolean(false);
    /** 票据副本的补录来源：统一认证登录页与根域各读一次（CASTGC 常挂 Path=/authserver）。 */
    private static final String AUTH_LOGIN_COOKIE_URL = "https://authserver.lzzy.net/authserver/login";
    private static final String AUTH_ROOT_COOKIE_URL = "https://authserver.lzzy.net";

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
        SsoTicketStore.clear(getContext());
        SessionKeepAliveScheduler.cancel(getContext());
        // 明确退出登录：上一个会话的保活历史不再适用于新账号，一并清掉观测字段
        SessionKeepAliveStore.clear(getContext());
        call.resolve();
    }

    /** 设置页「保持登录」开关：首次登录说明同意后默认开启，约每 30 分钟后台维持一次会话。 */
    @PluginMethod
    public void setKeepAlive(PluginCall call) {
        Boolean enabled = call.getBoolean("enabled");
        boolean on = enabled != null && enabled;
        SessionKeepAliveStore.setEnabled(getContext(), on);
        if (on) {
            // 旧版本登录或登录时还没开启保活时没有票据副本，这里从 WebView 补一份
            ensureTicketFromWebView();
            SessionKeepAliveScheduler.resumeIfEnabled(getContext());
        } else {
            SessionKeepAliveScheduler.cancel(getContext());
            // 关闭即删副本：文案承诺「关闭/撤回后删除」，也避免 WebView 之外的长期票据残留
            SsoTicketStore.clear(getContext());
        }
        call.resolve(keepAliveState());
    }

    /** 从 WebView Cookie 补录票据副本；已有副本、没有票据或 WebView 未就绪时静默跳过。 */
    private void ensureTicketFromWebView() {
        if (!SsoTicketStore.load(getContext()).isEmpty()) return;
        try {
            CookieManager manager = CookieManager.getInstance();
            String raw = manager.getCookie(AUTH_LOGIN_COOKIE_URL);
            if (raw == null || raw.isEmpty()) raw = manager.getCookie(AUTH_ROOT_COOKIE_URL);
            if (raw == null) return;
            for (String part : raw.split(";")) {
                String pair = part.trim();
                int index = pair.indexOf('=');
                if (index <= 0) continue;
                String name = pair.substring(0, index).trim();
                if (!"CASTGC".equals(name) && !"TGC".equals(name)) continue;
                String value = pair.substring(index + 1).trim();
                if (!value.isEmpty()) {
                    SsoTicketStore.save(getContext(), name + "=" + value);
                    return;
                }
            }
        } catch (RuntimeException ignored) {
            // WebView 未就绪：下次登录或再开开关时补
        }
    }

    @PluginMethod
    public void getKeepAliveState(PluginCall call) {
        call.resolve(keepAliveState());
    }

    /**
     * 回前台补一次保活：距上次检查超过 25 分钟才真正发起，避免每次切前台都打网络。
     * 原生先轻量检查、明确失效再用票据换票；返回 result=ALIVE/EXPIRED/FAILED/SKIPPED。
     */
    @PluginMethod
    public void checkKeepAliveNow(PluginCall call) {
        Context context = getContext();
        if (!SessionKeepAliveStore.isEnabled(context) || !AuthCookieStore.hasStoredCookie(context)) {
            call.resolve(keepAliveTopUpState("SKIPPED", "inactive"));
            return;
        }
        long lastCheckAt = SessionKeepAliveStore.getLastCheckAt(context);
        boolean backoff = SessionKeepAliveStore.isBackoff(context);
        if (!SessionKeepAliveService.shouldTopUp(System.currentTimeMillis(), lastCheckAt, backoff,
                SessionKeepAliveService.TOP_UP_MIN_INTERVAL_MS)) {
            call.resolve(keepAliveTopUpState("SKIPPED", backoff ? "backoff" : "fresh"));
            return;
        }
        if (!topUpRunning.compareAndSet(false, true)) {
            call.resolve(keepAliveTopUpState("SKIPPED", "running"));
            return;
        }
        final PluginCall pending = call;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SessionKeepAliveService.Result result =
                            SessionKeepAliveService.checkNowAndReschedule(getContext());
                    // 只记录结果，不记录 Cookie 或响应内容
                    DebugLog.i("JiaowuKeepAlive", "前台补保活 " + result);
                    pending.resolve(keepAliveTopUpState(result.name(), null));
                } catch (RuntimeException error) {
                    pending.resolve(keepAliveTopUpState("FAILED", null));
                } finally {
                    topUpRunning.set(false);
                }
            }
        }, "keepalive-topup").start();
    }

    private JSObject keepAliveTopUpState(String result, String reason) {
        JSObject state = keepAliveState();
        state.put("result", result);
        if (reason != null) state.put("reason", reason);
        return state;
    }

    /**
     * 会话失效时用统一认证票据静默换票：不展示界面、不弹对话框。
     * 没有 CASTGC 时直接返回 no-ticket，不拉起 WebView。
     */
    @PluginMethod
    public void silentRelogin(PluginCall call) {
        CookieManager manager = CookieManager.getInstance();
        if (!SsoCookieCleaner.hasCastgc(manager)) {
            resolveSilent(call, false, "no-ticket", null, null, 0);
            return;
        }
        Integer requested = call.getInt("timeoutMs");
        int timeout = requested == null ? DEFAULT_SILENT_TIMEOUT_MS : requested;
        Intent intent = new Intent();
        intent.setClassName(getContext(), getContext().getPackageName() + ".SilentSsoLoginActivity");
        intent.putExtra(SsoLoginActivity.EXTRA_SILENT, true);
        intent.putExtra(SsoLoginActivity.EXTRA_TIMEOUT_MS, timeout);
        startActivityForResult(call, intent, "handleSilentResult");
    }

    /** 只读探测是否还持有 CASTGC，不返回票据值。 */
    @PluginMethod
    public void hasSsoTicket(PluginCall call) {
        JSObject result = new JSObject();
        result.put("hasTicket", SsoCookieCleaner.hasCastgc(CookieManager.getInstance()));
        call.resolve(result);
    }

    private void resolveSilent(PluginCall call, boolean ok, String reason,
                               String cookieHeader, String finalUrl, long elapsedMs) {
        JSObject result = new JSObject();
        result.put("ok", ok);
        result.put("reason", reason);
        if (cookieHeader != null && !cookieHeader.isEmpty()) {
            result.put("cookieHeader", cookieHeader);
        }
        if (finalUrl != null && !finalUrl.isEmpty()) result.put("finalUrl", finalUrl);
        result.put("elapsedMs", elapsedMs);
        call.resolve(result);
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
        // 可观测字段显式放 JSONObject.NULL 保证键存在：前端不需要区分「字段缺失」和「从未保活」
        long lastCheckAt = SessionKeepAliveStore.getLastCheckAt(getContext());
        String lastResult = SessionKeepAliveStore.getLastResult(getContext());
        long nextCheckAt = SessionKeepAliveStore.getNextCheckAt(getContext());
        result.put("lastCheckAt", lastCheckAt > 0L ? Long.valueOf(lastCheckAt) : JSONObject.NULL);
        result.put("lastResult", lastResult == null ? JSONObject.NULL : lastResult);
        result.put("nextCheckAt", nextCheckAt > 0L ? Long.valueOf(nextCheckAt) : JSONObject.NULL);
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

    @ActivityCallback
    private void handleSilentResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Bundle data = result.getData() == null ? null : result.getData().getExtras();
        if (result.getResultCode() == Activity.RESULT_OK && data != null) {
            String cookieHeader = data.getString("cookieHeader", "");
            if (cookieHeader.isEmpty()) {
                resolveSilent(call, false, "no-cookie", null, null, 0);
                return;
            }
            resolveSilent(call, true, "ok", cookieHeader,
                    data.getString("finalUrl", ""), 0);
            return;
        }
        String reason = data == null ? null : data.getString(SsoLoginActivity.EXTRA_REASON);
        resolveSilent(call, false, reason == null || reason.isEmpty() ? "canceled" : reason,
                null, null, 0);
    }
}
