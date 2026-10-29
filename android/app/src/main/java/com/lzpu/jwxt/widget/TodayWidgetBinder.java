package com.lzpu.jwxt.widget;

import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

import com.lzpu.jwxt.R;

import java.util.List;

/**
 * 4x2「今日课程」：最多 3 行（时间 / 课程名 / 地点）+ 下一节提示。
 * 固定槽位，不用 addView，兼容 minSdk 22。
 */
final class TodayWidgetBinder {

    private static final int MAX_ROWS = 3;

    private static final int[] ROWS = {R.id.widget_row1, R.id.widget_row2, R.id.widget_row3};
    private static final int[] TIMES = {R.id.widget_time1, R.id.widget_time2, R.id.widget_time3};
    private static final int[] NAMES = {R.id.widget_name1, R.id.widget_name2, R.id.widget_name3};
    private static final int[] ROOMS = {R.id.widget_room1, R.id.widget_room2, R.id.widget_room3};



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
        views.setOnClickPendingIntent(R.id.widget_style_chip,
            WidgetDeepLink.cycleStyle(context, appWidgetId));
        for (int i = 0; i < MAX_ROWS; i += 1) {
            views.setTextColor(TIMES[i], theme.tertiary);
            views.setTextColor(NAMES[i], theme.text);
            views.setTextColor(ROOMS[i], theme.tertiary);
        }

        if (!payload.loggedIn) {
            hideRows(views, 0);
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE);
            views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_state_login));
            views.setTextViewText(R.id.widget_updated, "");
            views.setTextViewText(R.id.widget_footer, context.getString(R.string.widget_open_app_sync));
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