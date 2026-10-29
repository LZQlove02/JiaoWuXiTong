package com.lzpu.jwxt.widget;

import android.content.Context;
import android.widget.RemoteViews;

import com.lzpu.jwxt.R;

/**
 * 2x2「下一节课」：课程、时间、地点 + 今天剩余节数 / 更新时间。
 */
final class NextClassWidgetBinder {

    static RemoteViews build(Context context, WidgetPayload payload, WidgetTheme theme,
                             int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_next);
        views.setInt(R.id.widget_root, "setBackgroundResource", theme.rootBackgroundRes());
        views.setOnClickPendingIntent(R.id.widget_root,
            WidgetDeepLink.open(context, "schedule", WidgetDeepLink.REQUEST_NEXT));

        views.setTextColor(R.id.widget_title, theme.secondary);
        views.setTextColor(R.id.widget_course, theme.text);
        views.setTextColor(R.id.widget_time, theme.secondary);
        views.setTextColor(R.id.widget_room, theme.tertiary);
        views.setTextColor(R.id.widget_footer, theme.tertiary);
        views.setTextColor(R.id.widget_updated, theme.tertiary);
        bindStyleChip(context, views, theme, appWidgetId);

        if (!payload.loggedIn) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title_next));
            views.setTextViewText(R.id.widget_course, context.getString(R.string.widget_state_login));
            views.setTextViewText(R.id.widget_time, "");
            views.setTextViewText(R.id.widget_room, "");
            views.setTextViewText(R.id.widget_updated, "");
            views.setTextViewText(R.id.widget_footer, context.getString(R.string.widget_open_app_sync));
            return views;
        }

        String updated = updatedText(context, payload);
        WidgetPayload.ClassItem next = payload.next;
        if (next == null) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title_next));
            views.setTextViewText(R.id.widget_course, context.getString(R.string.widget_no_next));
            views.setTextViewText(R.id.widget_time, "");
            views.setTextViewText(R.id.widget_room, "");
            views.setTextViewText(R.id.widget_updated, updated);
            views.setTextViewText(R.id.widget_footer, remainingText(context, payload));
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
        views.setTextViewText(R.id.widget_updated, updated);
        views.setTextViewText(R.id.widget_footer, remainingText(context, payload));
        return views;
    }

    // 2x2 宽度只够放一行，剩余节数与更新时间分成两行显示
    static void bindStyleChip(Context context, RemoteViews views, WidgetTheme theme, int appWidgetId) {
        views.setTextViewText(R.id.widget_style_chip,
            context.getString(R.string.widget_style_chip, WidgetStore.styleLabel(context, theme.style)));
        views.setOnClickPendingIntent(R.id.widget_style_chip,
            WidgetDeepLink.cycleStyle(context, appWidgetId));
    }

    static String updatedText(Context context, WidgetPayload payload) {
        return payload.updatedLabel.isEmpty()
            ? ""
            : context.getString(R.string.widget_updated_at, payload.updatedLabel);
    }

    static String remainingText(Context context, WidgetPayload payload) {
        if (payload.remainingToday > 0) {
            return context.getString(R.string.widget_remaining, payload.remainingToday);
        }
        if (!payload.scheduleLoaded) {
            return context.getString(R.string.widget_need_sync);
        }
        return payload.today.isEmpty()
            ? context.getString(R.string.widget_no_class)
            : context.getString(R.string.widget_class_finished);
    }
}