package com.lzqlove.jiaowu.widget;

import com.lzqlove.jiaowu.R;

import java.util.Collections;
import java.util.List;

/**
 * 4x4「本周课表」的两周切换：把「看哪一周」的取数与文案收成纯函数，方便单测。
 * offset 只认 0（本周）与 1（下周），其它值一律当本周，避免脏数据把组件翻到空白页。
 */
public final class WidgetWeekDisplay {

    public static final int THIS_WEEK = 0;
    public static final int NEXT_WEEK = 1;

    private WidgetWeekDisplay() {
    }

    public static int normalize(int offset) {
        return offset == NEXT_WEEK ? NEXT_WEEK : THIS_WEEK;
    }

    /** 只有登录、课表已加载且未跨周时才允许翻页；其余分支整块降级成状态文案。 */
    public static boolean canSwitch(WidgetPayload payload) {
        return payload != null && payload.loggedIn && payload.scheduleLoaded && !payload.stale;
    }

    public static List<WidgetPayload.WeekItem> items(WidgetPayload payload, int offset) {
        if (payload == null) return Collections.emptyList();
        return normalize(offset) == NEXT_WEEK ? payload.nextWeek : payload.week;
    }

    /** 标题里的周次；下周未知（学期末 / 老 payload）返回 0，调用方回退成不带周次的标题。 */
    public static int weekNo(WidgetPayload payload, int offset) {
        if (payload == null) return 0;
        return normalize(offset) == NEXT_WEEK ? payload.nextWeekNo : payload.termWeek;
    }

    /** 看下周时今天没意义：给渲染器传 0，既不铺今天那列背景，也不画蓝点。 */
    public static int todayWeekday(int offset, int realToday) {
        return normalize(offset) == NEXT_WEEK ? 0 : realToday;
    }

    /** 带周次的标题（widget_title_*_no）；周次为 0 时改用 plainTitleRes。 */
    public static int titleRes(int offset) {
        return normalize(offset) == NEXT_WEEK
            ? R.string.widget_title_next_week_no
            : R.string.widget_title_week_no;
    }

    public static int plainTitleRes(int offset) {
        return normalize(offset) == NEXT_WEEK
            ? R.string.widget_title_next_week
            : R.string.widget_title_week;
    }

    public static int emptyRes(int offset) {
        return normalize(offset) == NEXT_WEEK
            ? R.string.widget_empty_next_week
            : R.string.widget_empty_week;
    }

    /** 标题行按钮：本周页显示「下周 ›」，下周页显示「本周 ‹」。 */
    public static int toggleRes(int offset) {
        return normalize(offset) == NEXT_WEEK
            ? R.string.widget_switch_this_week
            : R.string.widget_switch_next_week;
    }
}
