package com.lzqlove.jiaowu.change;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 后台检查时段的本地存储。课表变更、成绩提醒等共用同一组时段。
 */
public final class CheckTimeStore {

    private static final String PREFS_NAME = "jiaowu_check_times";
    private static final String KEY_MORNING = "morning";
    private static final String KEY_NOON = "noon";

    private CheckTimeStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static CheckTimes read(Context context) {
        String morning = prefs(context).getString(KEY_MORNING, CheckTimes.DEFAULT_MORNING);
        String noon = prefs(context).getString(KEY_NOON, CheckTimes.DEFAULT_NOON);
        CheckTimes times = CheckTimes.parse(morning, noon);
        // 存储损坏或旧版本非法值时回退默认，不让调度器停在无效时间
        return times != null && times.isValid() ? times : CheckTimes.defaults();
    }

    /** 写入并校验；非法值不落盘，返回 false。 */
    public static boolean write(Context context, String morning, String noon) {
        CheckTimes times = CheckTimes.parse(morning, noon);
        if (times == null || !times.isValid()) return false;
        prefs(context).edit()
                .putString(KEY_MORNING, times.morningText())
                .putString(KEY_NOON, times.noonText())
                .apply();
        return true;
    }

    public static void reset(Context context) {
        prefs(context).edit().remove(KEY_MORNING).remove(KEY_NOON).apply();
    }
}
