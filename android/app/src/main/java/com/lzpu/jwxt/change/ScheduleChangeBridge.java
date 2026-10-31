package com.lzpu.jwxt.change;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JS → 原生变更引擎。对应前端 src/native/scheduleChangeBridge.js。
 * applySchedule 负责前台对账；其余方法读写状态、开关与通知权限。
 */
@CapacitorPlugin(name = "ScheduleChange", permissions = {
        @Permission(alias = "notifications", strings = {Manifest.permission.POST_NOTIFICATIONS})
})
public class ScheduleChangeBridge extends Plugin {

    private static volatile ScheduleChangeBridge instance;

    @Override
    public void load() {
        instance = this;
        ScheduleChangeNotifier.ensureChannel(getContext());
        GradeAlertNotifier.ensureChannel(getContext());
        PortalAlertNotifier.ensureChannel(getContext());
        ScheduleCheckScheduler.scheduleAll(getContext());
    }

    /** 后台检查发现变更且 App 在前台时，通知 JS 刷新提醒条。 */
    static void emitChanged() {
        ScheduleChangeBridge local = instance;
        if (local == null) return;
        JSObject data = new JSObject();
        data.put("source", "background");
        local.notifyListeners("changed", data);
    }

    @PluginMethod
    public void applySchedule(PluginCall call) {
        String raw = call.getString("raw");
        JSObject termObject = call.getObject("term");
        if (raw == null || raw.trim().isEmpty() || termObject == null) {
            call.reject("参数缺失");
            return;
        }
        TermInfo term = new TermInfo(
                termObject.getString("xnm"),
                termObject.getString("xqm"),
                termObject.getString("startDate"));
        Map<Integer, String> sectionTimes = new LinkedHashMap<>();
        JSObject times = call.getObject("sectionTimes");
        if (times != null) {
            java.util.Iterator<String> keys = times.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject range = times.optJSONObject(key);
                if (range == null) continue;
                String start = range.optString("start");
                String end = range.optString("end");
                try {
                    sectionTimes.put(Integer.parseInt(key), start + "-" + end);
                } catch (NumberFormatException ignored) {
                    // 非数字节次忽略
                }
            }
        }
        String studentId = call.getString("studentId");
        try {
            ScheduleChangeService.apply(getContext(), raw, term, studentId == null ? "" : studentId,
                    sectionTimes, "foreground");
        } catch (RuntimeException error) {
            call.reject("课表对账失败");
            return;
        }
        if (BackgroundCheckPolicy.isAnyEnabled(getContext())) {
            ScheduleCheckScheduler.scheduleAll(getContext());
        }
        call.resolve(state());
    }

    @PluginMethod
    public void getState(PluginCall call) {
        call.resolve(state());
    }

    @PluginMethod
    public void markAllRead(PluginCall call) {
        ScheduleChangeService.markAllRead(getContext());
        call.resolve(state());
    }

    @PluginMethod
    public void clear(PluginCall call) {
        ScheduleChangeService.clearForLogout(getContext());
        call.resolve(state());
    }

    @PluginMethod
    public void setEnabled(PluginCall call) {
        Boolean enabled = call.getBoolean("enabled");
        ScheduleChangeService.setEnabled(getContext(), enabled != null && enabled);
        call.resolve(state());
    }

    @PluginMethod
    public void getGradeAlertState(PluginCall call) {
        call.resolve(gradeState());
    }

    @PluginMethod
    public void setGradeAlertEnabled(PluginCall call) {
        Boolean enabled = call.getBoolean("enabled");
        GradeAlertService.setEnabled(getContext(), enabled != null && enabled);
        call.resolve(gradeState());
    }

    private JSObject gradeState() {
        JSObject result = new JSObject();
        result.put("enabled", GradeAlertStore.isEnabled(getContext()));
        result.put("notificationsEnabled", ScheduleChangeNotifier.notificationsEnabled(getContext()));
        return result;
    }

    @PluginMethod
    public void getPortalAlertState(PluginCall call) {
        call.resolve(portalAlertState());
    }

    @PluginMethod
    public void setPortalAlertEnabled(PluginCall call) {
        Boolean notices = call.getBoolean("notices");
        Boolean messages = call.getBoolean("messages");
        if (notices != null) {
            PortalAlertService.setEnabled(getContext(), true, notices);
        }
        if (messages != null) {
            PortalAlertService.setEnabled(getContext(), false, messages);
        }
        call.resolve(portalAlertState());
    }

    private JSObject portalAlertState() {
        JSObject result = new JSObject();
        result.put("noticesEnabled", PortalAlertStore.isNoticesEnabled(getContext()));
        result.put("messagesEnabled", PortalAlertStore.isMessagesEnabled(getContext()));
        result.put("notificationsEnabled", ScheduleChangeNotifier.notificationsEnabled(getContext()));
        return result;
    }

    @PluginMethod
    public void getCheckTimes(PluginCall call) {
        call.resolve(checkTimes());
    }

    /** 两个时段同时生效；格式错误或间隔不足 60 分钟时拒绝，不修改旧值。 */
    @PluginMethod
    public void setCheckTimes(PluginCall call) {
        String morning = call.getString("morning");
        String noon = call.getString("noon");
        if (!CheckTimeStore.write(getContext(), morning, noon)) {
            call.reject("时间格式无效，或两次检查间隔不足 60 分钟");
            return;
        }
        ScheduleCheckScheduler.scheduleAll(getContext());
        call.resolve(checkTimes());
    }

    @PluginMethod
    public void resetCheckTimes(PluginCall call) {
        CheckTimeStore.reset(getContext());
        ScheduleCheckScheduler.scheduleAll(getContext());
        call.resolve(checkTimes());
    }

    private JSObject checkTimes() {
        CheckTimes times = CheckTimeStore.read(getContext());
        JSObject result = new JSObject();
        result.put("morning", times.morningText());
        result.put("noon", times.noonText());
        return result;
    }

    @PluginMethod
    public void requestNotificationPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT < 33
                || ScheduleChangeNotifier.notificationsEnabled(getContext())) {
            resolvePermission(call, false);
            return;
        }
        ScheduleChangeStore.markNotificationAsked(getContext());
        requestPermissionForAlias("notifications", call, "notificationPermissionCallback");
    }

    /**
     * 首次成功对账后由 JS 调用：只在 Android 13+、开关开启、尚未授权、前台且没问过时申请一次。
     * 后台检查不会走这里（没有 Activity），因此不会在用户不知情时弹框。
     */
    @PluginMethod
    public void maybeRequestNotificationPermission(PluginCall call) {
        boolean shouldAsk = Build.VERSION.SDK_INT >= 33
                && BackgroundCheckPolicy.isAnyEnabled(getContext())
                && !ScheduleChangeNotifier.notificationsEnabled(getContext())
                && !ScheduleChangeStore.hasAskedNotification(getContext())
                && AppVisibility.isForeground();
        if (!shouldAsk) {
            JSObject result = new JSObject();
            result.put("asked", false);
            call.resolve(result);
            return;
        }
        ScheduleChangeStore.markNotificationAsked(getContext());
        requestPermissionForAlias("notifications", call, "notificationPermissionCallback");
    }

    @PermissionCallback
    private void notificationPermissionCallback(PluginCall call) {
        resolvePermission(call, true);
    }

    @PluginMethod
    public void openNotificationSettings(PluginCall call) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getContext().getPackageName()));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(intent);
        } catch (RuntimeException ignored) {
            // 个别 ROM 没有对应设置页，忽略
        }
        call.resolve();
    }

    private void resolvePermission(PluginCall call, boolean asked) {
        JSObject result = new JSObject();
        result.put("granted", ScheduleChangeNotifier.notificationsEnabled(getContext()));
        result.put("asked", asked);
        call.resolve(result);
    }

    private JSObject state() {
        JSONObject raw = ScheduleChangeService.state(getContext());
        JSObject result = new JSObject();
        result.put("enabled", raw.optBoolean("enabled", true));
        result.put("unread", raw.optInt("unread", 0));
        result.put("notificationsEnabled", raw.optBoolean("notificationsEnabled", true));
        JSONArray entries = raw.optJSONArray("entries");
        result.put("entries", entries == null ? new JSONArray() : entries);
        return result;
    }
}