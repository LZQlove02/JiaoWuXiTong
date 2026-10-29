package com.lzpu.jwxt.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

import com.lzpu.jwxt.R;

/**
 * 2x2「下一节课」：课程、时间、地点 + 今天剩余节数 / 更新时间。
 */
public class NextClassWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WidgetUpdater.refreshAll(context);
    }

    @Override
    public void onEnabled(Context context) {
        // 首次添加时 onUpdate 未必立刻到达，主动渲染一次占位内容
        WidgetUpdater.refreshAll(context);
    }

    static RemoteViews build(Context context, WidgetPayload payload, WidgetTheme theme) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_next);
        views.setInt(R.id.widget_root, "setBackgroundResource", theme.backgroundRes());
        views.setOnClickPendingIntent(R.id.widget_root,
            WidgetDeepLink.open(context, "schedule", WidgetDeepLink.REQUEST_NEXT));

        views.setTextColor(R.id.widget_title, theme.secondary);
        views.setTextColor(R.id.widget_course, theme.text);
        views.setTextColor(R.id.widget_time, theme.secondary);
        views.setTextColor(R.id.widget_room, theme.tertiary);
        views.setTextColor(R.id.widget_footer, theme.tertiary);

        if (!payload.loggedIn) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title_next));
            views.setTextViewText(R.id.widget_course, context.getString(R.string.widget_state_login));
            views.setTextViewText(R.id.widget_time, "");
            views.setTextViewText(R.id.widget_room, "");
            views.setTextViewText(R.id.widget_footer, context.getString(R.string.widget_open_app_sync));
            return views;
        }

        WidgetPayload.ClassItem next = payload.next;
        if (next == null) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title_next));
            views.setTextViewText(R.id.widget_course, context.getString(R.string.widget_no_next));
            views.setTextViewText(R.id.widget_time, "");
            views.setTextViewText(R.id.widget_room, "");
            views.setTextViewText(R.id.widget_footer, footer(context, payload));
            return views;
        }

        String title = context.getString(R.string.widget_title_next);
        if (!next.isToday()) {
            title = title + " · " + next.dayLabel;
        }
        views.setTextViewText(R.id.widget_title, title);
        views.setTextViewText(R.id.widget_course,
            next.name.isEmpty() ? context.getString(R.string.widget_no_next) : next.name);
        views.setTextViewText(R.id.widget_time, next.timeRange());
        views.setTextViewText(R.id.widget_room, next.room);
        views.setTextViewText(R.id.widget_footer, footer(context, payload));
        return views;
    }

    static String footer(Context context, WidgetPayload payload) {
        StringBuilder builder = new StringBuilder();
        if (payload.remainingToday > 0) {
            builder.append(context.getString(R.string.widget_remaining, payload.remainingToday));
        }
        if (!payload.updatedLabel.isEmpty()) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append(context.getString(R.string.widget_updated_at, payload.updatedLabel));
        }
        if (builder.length() == 0) {
            builder.append(context.getString(R.string.widget_need_sync));
        }
        return builder.toString();
    }
}