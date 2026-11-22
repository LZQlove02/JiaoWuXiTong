package com.lzqlove.jiaowu.keepalive;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 「保持登录」开关与保活可观测状态：原生偏好默认关闭，产品默认开启由前端在首次登录说明同意后写入；
 * 未同意或明确关闭时不调度后台保活。
 *
 * 可观测字段只记录时间戳与结果枚举名，不记录 Cookie、响应内容或账号信息。
 * lastCheckAt / nextCheckAt 用 0 表示「没有记录」，由调用方按 null 语义返回给前端；
 * lastResult 用 null 表示「没有记录」，历史脏值也按 null 处理，保证契约里只出现三种枚举名。
 */
public final class SessionKeepAliveStore {

    private static final String PREFS = "jiaowu_keepalive";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LAST_CHECK_AT = "lastCheckAt";
    private static final String KEY_LAST_RESULT = "lastResult";
    private static final String KEY_NEXT_CHECK_AT = "nextCheckAt";

    private SessionKeepAliveStore() {
    }

    public static boolean isEnabled(Context context) {
        return isEnabled(prefs(context));
    }

    public static void setEnabled(Context context, boolean enabled) {
        setEnabled(prefs(context), enabled);
    }

    /** 上次真正发起保活检查的时间（毫秒）；0 表示从未记录。 */
    public static long getLastCheckAt(Context context) {
        return getLastCheckAt(prefs(context));
    }

    /** 上次检查结果 ALIVE / EXPIRED / FAILED；null 表示从未记录或历史值不可识别。 */
    public static String getLastResult(Context context) {
        return getLastResult(prefs(context));
    }

    /** 下一次闹钟触发时间（毫秒）；0 表示当前没有排程。 */
    public static long getNextCheckAt(Context context) {
        return getNextCheckAt(prefs(context));
    }

    /**
     * 一次检查结束后记录时间与结果。在 checkNow 的统一出口调用，
     * 避免以后新增返回分支时漏记时间或结果。
     */
    public static void recordCheckResult(Context context, long checkedAt, String result) {
        recordCheckResult(prefs(context), checkedAt, result);
    }

    /** 排程成功后记录下一次触发时间；传 0 表示取消排程。 */
    public static void setNextCheckAt(Context context, long triggerAt) {
        setNextCheckAt(prefs(context), triggerAt);
    }

    /**
     * 清除保活观测状态（lastCheckAt / lastResult / nextCheckAt），不影响 enabled 开关本身。
     * 只在明确退出登录时调用：历史结果属于上一个会话，不应带到下一次登录展示。
     */
    public static void clear(Context context) {
        clear(prefs(context));
    }

    // 以下 SharedPreferences 重载为包内可见：本地单测没有真实 Context / Robolectric，
    // 直接对偏好对象读写，才能覆盖「写入 → 读取 → 清理」的真实语义。
    static boolean isEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(SharedPreferences prefs, boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    static long getLastCheckAt(SharedPreferences prefs) {
        return prefs.getLong(KEY_LAST_CHECK_AT, 0L);
    }

    static String getLastResult(SharedPreferences prefs) {
        String raw = prefs.getString(KEY_LAST_RESULT, null);
        if (raw == null || raw.isEmpty()) return null;
        try {
            // 用枚举名做一次白名单校验，旧版本或手工写入的未知值按「没有记录」返回
            return SessionKeepAliveService.Result.valueOf(raw).name();
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    static long getNextCheckAt(SharedPreferences prefs) {
        return prefs.getLong(KEY_NEXT_CHECK_AT, 0L);
    }

    static void recordCheckResult(SharedPreferences prefs, long checkedAt, String result) {
        prefs.edit()
                .putLong(KEY_LAST_CHECK_AT, checkedAt)
                .putString(KEY_LAST_RESULT, result)
                .apply();
    }

    static void setNextCheckAt(SharedPreferences prefs, long triggerAt) {
        if (triggerAt > 0L) {
            prefs.edit().putLong(KEY_NEXT_CHECK_AT, triggerAt).apply();
        } else {
            // 用 remove 而不是写 0：偏好里不存在该键就是「没有排程」，避免误读为有效时间戳
            prefs.edit().remove(KEY_NEXT_CHECK_AT).apply();
        }
    }

    static void clear(SharedPreferences prefs) {
        prefs.edit()
                .remove(KEY_LAST_CHECK_AT)
                .remove(KEY_LAST_RESULT)
                .remove(KEY_NEXT_CHECK_AT)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
