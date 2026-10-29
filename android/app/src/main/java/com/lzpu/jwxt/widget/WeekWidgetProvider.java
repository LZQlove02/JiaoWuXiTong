package com.lzpu.jwxt.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import com.lzpu.jwxt.R;

/**
 * 4x4「本周课表 + 摘要」：位图网格 + 未阅消息 / 最新通知 / 成绩。
 * 三个热区各自独立 PendingIntent，避免点击串页。
 */
public class WeekWidgetProvider extends AppWidgetProvider {

    // 与 widget_week.xml 对应：标题 + 摘要三行 + 更新时间的固定开销
    private static final int CHROME_DP = 96;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WidgetUpdater.refreshAll(context);
    }

    @Override
    public void onEnabled(Context context) {
        WidgetUpdater.refreshAll(context);
    }

    static RemoteViews build(Context context, WidgetPayload payload, WidgetTheme theme,
                             int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_week);
        views.setInt(R.id.widget_root, "setBackgroundResource", theme.rootBackgroundRes());
        views.setInt(R.id.widget_week_strip_head, "setBackgroundResource", theme.panelBackgroundRes());
        views.setInt(R.id.widget_week_strip_summary, "setBackgroundResource", theme.panelBackgroundRes());
        views.setTextColor(R.id.widget_week_title, theme.text);
        views.setTextColor(R.id.widget_week_updated, theme.tertiary);
        views.setTextColor(R.id.widget_week_state, theme.tertiary);
        views.setTextColor(R.id.widget_week_messages, theme.secondary);
        views.setTextColor(R.id.widget_week_notices, theme.tertiary);
        views.setTextColor(R.id.widget_week_grades, theme.secondary);

        views.setOnClickPendingIntent(R.id.widget_week_frame,
            WidgetDeepLink.open(context, "schedule", WidgetDeepLink.REQUEST_WEEK_SCHEDULE));
        views.setOnClickPendingIntent(R.id.widget_week_messages,
            WidgetDeepLink.open(context, "messages", WidgetDeepLink.REQUEST_WEEK_MESSAGES));
        views.setOnClickPendingIntent(R.id.widget_week_notices,
            WidgetDeepLink.open(context, "notices", WidgetDeepLink.REQUEST_WEEK_NOTICES));
        views.setOnClickPendingIntent(R.id.widget_week_grades,
            WidgetDeepLink.open(context, "grades", WidgetDeepLink.REQUEST_WEEK_GRADES));

        views.setTextViewText(R.id.widget_week_updated,
            payload.updatedLabel.isEmpty() ? "" : context.getString(R.string.widget_updated_at, payload.updatedLabel));

        if (!payload.loggedIn) {
            showState(views, context.getString(R.string.widget_state_login));
            return views;
        }
        if (!payload.scheduleLoaded) {
            showState(views, context.getString(R.string.widget_need_sync));
            return views;
        }

        int[] size = gridSize(context, appWidgetId);
        android.graphics.Bitmap bitmap = WidgetBitmapRenderer.render(
            payload, theme, size[0], size[1], todayWeekday());
        if (bitmap == null) {
            showState(views, context.getString(R.string.widget_need_sync));
            return views;
        }

        views.setViewVisibility(R.id.widget_week_state, View.GONE);
        views.setViewVisibility(R.id.widget_week_grid, View.VISIBLE);
        views.setImageViewBitmap(R.id.widget_week_grid, bitmap);
        views.setTextViewText(R.id.widget_week_messages, messageText(context, payload));
        views.setTextViewText(R.id.widget_week_notices, noticeText(context, payload));
        views.setTextViewText(R.id.widget_week_grades, gradeText(context, payload));
        return views;
    }

    private static void showState(RemoteViews views, String state) {
        views.setViewVisibility(R.id.widget_week_grid, View.GONE);
        views.setViewVisibility(R.id.widget_week_state, View.VISIBLE);
        views.setTextViewText(R.id.widget_week_state, state);
        views.setTextViewText(R.id.widget_week_messages, "");
        views.setTextViewText(R.id.widget_week_notices, "");
        views.setTextViewText(R.id.widget_week_grades, "");
    }

    // 位图只画网格本身，尺寸要扣掉标题、摘要与内边距，避免被 fitXY 拉伸变形
    private static int[] gridSize(Context context, int appWidgetId) {
        int[] fallback = {480, 360};
        Bundle options;
        try {
            options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId);
        } catch (RuntimeException error) {
            return fallback;
        }
        if (options == null) return fallback;

        int widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
        int heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        if (widthDp <= 0 || heightDp <= 0) {
            widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        }
        if (widthDp <= 0 || heightDp <= 0) return fallback;

        float density = context.getResources().getDisplayMetrics().density;
        int widthPx = Math.round(widthDp * density);
        int heightPx = Math.round((heightDp - CHROME_DP) * density);
        if (widthPx <= 0 || heightPx <= 0) return fallback;
        return new int[]{widthPx, heightPx};
    }

    // 周一=1 ... 周日=7，与 JS 侧 getWeekday 保持一致
    private static int todayWeekday() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int day = calendar.get(java.util.Calendar.DAY_OF_WEEK);
        return day == java.util.Calendar.SUNDAY ? 7 : day - 1;
    }

    private static String messageText(Context context, WidgetPayload payload) {
        if (payload.unread > 0) {
            return context.getString(R.string.widget_unread, payload.unread);
        }
        return context.getString(R.string.widget_no_unread);
    }

    private static String noticeText(Context context, WidgetPayload payload) {
        if (payload.latestNotice.isEmpty()) {
            return context.getString(R.string.widget_no_notice);
        }
        String title = payload.latestNotice;
        if (title.length() > 14) title = title.substring(0, 14) + "…";
        return context.getString(R.string.widget_latest_notice, title);
    }

    private static String gradeText(Context context, WidgetPayload payload) {
        return payload.gradeLabel.isEmpty()
            ? context.getString(R.string.widget_no_grades)
            : payload.gradeLabel;
    }
}