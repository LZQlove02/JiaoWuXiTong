package com.lzqlove.jiaowu.widget;

import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

import com.lzqlove.jiaowu.R;

import java.util.Calendar;
import java.util.List;

/**
 * 4x2「今日课程」：最多 3 行（时间 / 课名 · 教室），底部固定「下一节」。
 * 结构约束见《小组件执行方案》§7.2：
 * 不做超额提示、行数随实际课数变化但 footer 始终贴底。
 * 课程行不再显示「待上 / 进行中 / 已结束」，把宽度让给课名与教室。
 */
final class TodayWidgetBinder {

    private static final int MAX_ROWS = 3;

    private static final int[] ROWS = {R.id.widget_row1, R.id.widget_row2, R.id.widget_row3};
    private static final int[] TIMES = {R.id.widget_time1, R.id.widget_time2, R.id.widget_time3};
    private static final int[] NAMES = {R.id.widget_name1, R.id.widget_name2, R.id.widget_name3};
    private static final int[] ROOMS = {R.id.widget_room1, R.id.widget_room2, R.id.widget_room3};

    private static final String[] WEEKDAYS = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

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
        // 「有变」角标：只读 payload，不碰第二份存储；RemoteViews 复用视图，每次显式设置
        views.setViewVisibility(R.id.widget_today_change,
            payload.loggedIn && !payload.stale && payload.changeBadge
                ? View.VISIBLE : View.GONE);
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
        for (int i = 0; i < shown; i += 1) {
            WidgetPayload.ClassItem item = today.get(i);
            views.setViewVisibility(ROWS[i], View.VISIBLE);
            views.setTextViewText(TIMES[i], item.startTime);
            views.setTextViewText(NAMES[i], item.name);
            views.setTextViewText(ROOMS[i], item.room);
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
        // 与 2x2 同口径：不在今天时统一显示「周六 14:30」，不再出现「明天」这种相对日期
        String prefix = WidgetScheduleDeriver.dayPrefix(next, Calendar.getInstance());
        if (!prefix.isEmpty()) {
            builder.append(prefix).append(' ');
        }
        if (!next.startTime.isEmpty()) {
            builder.append(next.startTime).append(' ');
        }
        builder.append(next.name);
        return builder.toString();
    }
}