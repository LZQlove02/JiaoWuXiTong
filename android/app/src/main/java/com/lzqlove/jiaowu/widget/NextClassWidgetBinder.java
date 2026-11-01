package com.lzqlove.jiaowu.widget;

import android.content.Context;
import android.widget.RemoteViews;

import com.lzqlove.jiaowu.R;

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

        // 跨周后 week[] 还是上一周的，next 已不可信：宁可不显示，也不显示上周的课
        if (payload.stale) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title_next));
            views.setTextViewText(R.id.widget_course, context.getString(R.string.widget_need_sync));
            views.setTextViewText(R.id.widget_time, "");
            views.setTextViewText(R.id.widget_room, "");
            views.setTextViewText(R.id.widget_updated, updatedText(context, payload));
            views.setTextViewText(R.id.widget_footer, "");
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

        // 标题只放「下一节课」：2x2 内容区约 82dp，再挂「· 周X」会被省略号吃掉
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title_next));
        views.setTextViewText(R.id.widget_course,
            next.name.isEmpty() ? context.getString(R.string.widget_no_next) : next.name);
        // 不在今天时把「周X」放进时间行：周二 14:30（放不下结束时间，只给开始时间）
        views.setTextViewText(R.id.widget_time,
            timeLine(next, WidgetScheduleDeriver.dayPrefix(next, java.util.Calendar.getInstance())));
        views.setTextViewText(R.id.widget_room, next.room);
        views.setTextViewText(R.id.widget_updated, updated);
        views.setTextViewText(R.id.widget_footer, remainingText(context, payload));
        return views;
    }

    // 2x2 宽度只够放一行，剩余节数与更新时间分成两行显示
    static void bindStyleChip(Context context, RemoteViews views, WidgetTheme theme, int appWidgetId) {
        // 齿轮图标跟随主体文字色，不额外加深色底
        views.setInt(R.id.widget_style_chip, "setColorFilter", theme.secondary);
        views.setOnClickPendingIntent(R.id.widget_style_chip,
            WidgetDeepLink.cycleStyle(context, appWidgetId));
    }

    // 时间行：今天给完整区间；不在今天时前面挂「周X」，只给开始时间（2x2 放不下区间）
    static String timeLine(WidgetPayload.ClassItem item, String dayPrefix) {
        if (dayPrefix == null || dayPrefix.isEmpty()) return item.timeRange();
        return item.startTime.isEmpty() ? dayPrefix : dayPrefix + " " + item.startTime;
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