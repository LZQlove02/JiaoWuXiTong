package com.lzpu.jwxt.widget;

import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

import com.lzpu.jwxt.R;

import java.util.Calendar;
import java.util.List;

/**
 * 4x2「今日课程」：最多 3 行（时间 / 课名 · 教室 / 状态），底部固定「下一节」。
 * 结构约束见《小组件执行方案》§7.2：
 * 不做超额提示、进行中不做选中效果、行数随实际课数变化但 footer 始终贴底。
 */
final class TodayWidgetBinder {

    private static final int MAX_ROWS = 3;

    private static final int[] ROWS = {R.id.widget_row1, R.id.widget_row2, R.id.widget_row3};
    private static final int[] TIMES = {R.id.widget_time1, R.id.widget_time2, R.id.widget_time3};
    private static final int[] NAMES = {R.id.widget_name1, R.id.widget_name2, R.id.widget_name3};
    private static final int[] ROOMS = {R.id.widget_room1, R.id.widget_room2, R.id.widget_room3};
    private static final int[] STATES = {R.id.widget_state1, R.id.widget_state2, R.id.widget_state3};

    private static final String[] WEEKDAYS = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    private static final int PENDING = 0;
    private static final int ONGOING = 1;
    private static final int DONE = 2;

    static RemoteViews build(Context context, WidgetPayload payload, WidgetTheme theme,
                             int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today);
        views.setInt(R.id.widget_root, "setBackgroundResource", theme.rootBackgroundRes());
        views.setOnClickPendingIntent(R.id.widget_root,
            WidgetDeepLink.open(context, "schedule", WidgetDeepLink.REQUEST_TODAY));

        views.setTextColor(R.id.widget_title, theme.text);
        views.setTextColor(R.id.widget_updated, theme.tertiary);
        views.setTextColor(R.id.widget_empty, theme.tertiary);
        views.setTextColor(R.id.widget_footer, theme.secondary);
        // 齿轮图标跟随主体文字色，不额外加深色底
        views.setInt(R.id.widget_style_chip, "setColorFilter", theme.secondary);
        views.setOnClickPendingIntent(R.id.widget_style_chip,
            WidgetDeepLink.cycleStyle(context, appWidgetId));
        for (int i = 0; i < MAX_ROWS; i += 1) {
            views.setTextColor(TIMES[i], theme.tertiary);
            views.setTextColor(NAMES[i], theme.text);
            views.setTextColor(ROOMS[i], theme.tertiary);
        }

        // 标题带星期：今日 · 周五（跨天刷新后能确认是哪天）
        views.setTextViewText(R.id.widget_title,
            context.getString(R.string.widget_title_today_day, weekdayLabel()));

        if (!payload.loggedIn) {
            hideRows(views, 0);
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE);
            views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_state_login));
            views.setTextViewText(R.id.widget_updated, "");
            views.setTextViewText(R.id.widget_footer, context.getString(R.string.widget_open_app_sync));
            return views;
        }

        // 跨周：今日课程来自上一周的 week[]，不再展示，统一提示回 App 同步
        if (payload.stale) {
            hideRows(views, 0);
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE);
            views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_open_app_sync));
            views.setTextViewText(R.id.widget_updated,
                payload.updatedLabel.isEmpty()
                    ? ""
                    : context.getString(R.string.widget_updated_at, payload.updatedLabel));
            views.setTextViewText(R.id.widget_footer, "");
            return views;
        }

        List<WidgetPayload.ClassItem> today = payload.today;
        int shown = Math.min(today.size(), MAX_ROWS);
        hideRows(views, shown);
        int now = minutesNow();
        for (int i = 0; i < shown; i += 1) {
            WidgetPayload.ClassItem item = today.get(i);
            views.setViewVisibility(ROWS[i], View.VISIBLE);
            views.setTextViewText(TIMES[i], item.startTime);
            views.setTextViewText(NAMES[i], item.name);
            views.setTextViewText(ROOMS[i], item.room);

            int state = stateOf(item, now);
            views.setTextViewText(STATES[i], context.getString(stateLabel(state)));
            // 只用文字区分状态，不做任何强调（用户明确不要「选中」效果）
            views.setTextColor(STATES[i], theme.tertiary);
        }

        if (today.isEmpty()) {
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE);
            views.setTextViewText(R.id.widget_empty, payload.scheduleLoaded
                ? context.getString(R.string.widget_no_class)
                : context.getString(R.string.widget_need_sync));
        } else {
            views.setViewVisibility(R.id.widget_empty, View.GONE);
        }

        views.setTextViewText(R.id.widget_updated,
            payload.updatedLabel.isEmpty() ? "" : context.getString(R.string.widget_updated_at, payload.updatedLabel));
        views.setTextViewText(R.id.widget_footer, footerText(context, payload));
        return views;
    }

    private static void hideRows(RemoteViews views, int from) {
        for (int i = from; i < MAX_ROWS; i += 1) {
            views.setViewVisibility(ROWS[i], View.GONE);
        }
    }

    // 0 待上 / 1 进行中 / 2 已结束；时间缺失按待上处理
    private static int stateOf(WidgetPayload.ClassItem item, int now) {
        int start = minutes(item.startTime);
        if (start < 0) return PENDING;
        int end = minutes(item.endTime);
        if (end < 0) end = start + 45;
        if (now >= end) return DONE;
        if (now >= start) return ONGOING;
        return PENDING;
    }

    private static int stateLabel(int state) {
        if (state == ONGOING) return R.string.widget_state_now;
        if (state == DONE) return R.string.widget_state_done;
        return R.string.widget_state_pending;
    }

    // "08:00" -> 480；解析失败返回 -1
    private static int minutes(String value) {
        if (value == null) return -1;
        String[] parts = value.split(":");
        if (parts.length != 2) return -1;
        try {
            return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    private static int minutesNow() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    }

    private static String weekdayLabel() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1;
        if (day < 0 || day >= WEEKDAYS.length) return "";
        return WEEKDAYS[day];
    }

    private static String footerText(Context context, WidgetPayload payload) {
        WidgetPayload.ClassItem next = payload.next;
        if (next == null) {
            // 课表未加载时上方占位已提示同步，这里不再重复
            if (!payload.scheduleLoaded) return "";
            return payload.today.isEmpty()
                ? context.getString(R.string.widget_no_class)
                : context.getString(R.string.widget_class_finished);
        }
        StringBuilder builder = new StringBuilder(context.getString(R.string.widget_next_prefix));
        if (!next.isToday()) {
            builder.append(next.dayLabel).append(' ');
        }
        if (!next.startTime.isEmpty()) {
            builder.append(next.startTime).append(' ');
        }
        builder.append(next.name);
        return builder.toString();
    }
}