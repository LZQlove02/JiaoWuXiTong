package com.lzqlove.jiaowu.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import com.lzqlove.jiaowu.R;

/**
 * 4x4「本周课表」：标题行（周次 + 「下周 ›」+ 齿轮 + 更新于）+ 位图网格。
 * 结构硬约束见《小组件执行方案》§7.3——不显示未阅/通知/GPA；
 * 每个课程块必须同时有课名与教室（由 WidgetBitmapRenderer 保证）。
 * 标题行的「下周 › / 本周 ‹」按实例翻页，只画那一周的课，不额外联网。
 */
final class WeekWidgetBinder {

    // 与 widget_week.xml 对应：根布局上下内边距 + 标题行 + 网格上方间距（dp）。
    // 标题行加了「下周 ›」后从 16dp 长到 24dp（minHeight 保证点击区），46 → 54；
    // 改布局必须同步这里，否则位图会被 fitCenter 缩出细边。
    private static final int CHROME_DP = 54;
    // 根布局左右各 10dp，位图宽度要扣掉
    private static final int H_PADDING_DP = 20;

    static RemoteViews build(Context context, WidgetPayload payload, WidgetTheme theme,
                             int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_week);
        // 未登录 / 未同步 / 跨周时不允许翻页：这些分支整块降级成状态文案，看下周没有意义
        boolean canSwitch = WidgetWeekDisplay.canSwitch(payload);
        int offset = WidgetWeekDisplay.THIS_WEEK;
        if (canSwitch) {
            offset = WidgetWeekDisplay.normalize(WidgetStore.readWeekOffset(context, appWidgetId));
        }

        views.setInt(R.id.widget_root, "setBackgroundResource", theme.rootBackgroundRes());
        views.setTextColor(R.id.widget_week_title, theme.text);
        views.setTextColor(R.id.widget_week_updated, theme.tertiary);
        views.setTextColor(R.id.widget_week_state, theme.tertiary);
        views.setTextColor(R.id.widget_week_toggle, theme.secondary);
        // 「有变」角标：只读 payload，不碰第二份存储；RemoteViews 复用视图，每次显式设置
        views.setViewVisibility(R.id.widget_week_change,
            payload.loggedIn && !payload.stale && payload.changeBadge
                ? View.VISIBLE : View.GONE);
        // 齿轮图标跟随主体文字色，不额外加深色底
        views.setInt(R.id.widget_style_chip, "setColorFilter", theme.secondary);
        views.setOnClickPendingIntent(R.id.widget_style_chip,
            WidgetDeepLink.cycleStyle(context, appWidgetId));

        // 周次翻页：按钮一直存在（除降级分支），文案随当前页变；不翻页时也没有别的入口
        views.setViewVisibility(R.id.widget_week_toggle,
            canSwitch ? View.VISIBLE : View.GONE);
        views.setTextViewText(R.id.widget_week_toggle,
            context.getString(WidgetWeekDisplay.toggleRes(offset)));
        views.setOnClickPendingIntent(R.id.widget_week_toggle,
            WidgetDeepLink.switchWeek(context, appWidgetId));

        // 只剩一个热区：整块网格 → 课表
        views.setOnClickPendingIntent(R.id.widget_week_frame,
            WidgetDeepLink.open(context, "schedule", WidgetDeepLink.REQUEST_WEEK_SCHEDULE));

        views.setTextViewText(R.id.widget_week_updated,
            WidgetUpdatedLabel.text(context, payload.updatedDate, payload.updatedLabel));
        // 标题必须每次都显式设置：RemoteViews 在布局不变时只 apply 动作，
        // 没设置的字会沿用上一次的值（退出登录后会残留「第 9 周」）
        int weekNo = WidgetWeekDisplay.weekNo(payload, offset);
        views.setTextViewText(R.id.widget_week_title,
            weekNo > 0
                ? context.getString(WidgetWeekDisplay.titleRes(offset), weekNo)
                : context.getString(WidgetWeekDisplay.plainTitleRes(offset)));

        if (!payload.loggedIn) {
            showState(views, context.getString(R.string.widget_state_login));
            return views;
        }
        if (!payload.scheduleLoaded) {
            showState(views, context.getString(R.string.widget_need_sync));
            return views;
        }
        // 跨周：week[] 与 termWeek 都是上一周的，网格和「第 N 周」都不能再信，
        // 整块降级成提示，等 App 打开后重新同步覆盖
        if (payload.stale) {
            views.setTextViewText(R.id.widget_week_title, context.getString(R.string.widget_title_week));
            showState(views, context.getString(R.string.widget_open_app_sync));
            return views;
        }

        int[] size = gridSize(context, appWidgetId);
        android.graphics.Bitmap bitmap = WidgetBitmapRenderer.render(
            theme, WidgetWeekDisplay.items(payload, offset), size[0], size[1],
            WidgetWeekDisplay.todayWeekday(offset, todayWeekday()),
            context.getString(WidgetWeekDisplay.emptyRes(offset)));
        if (bitmap == null) {
            // 位图渲染失败（超限 / OOM / 绘制异常）：只留状态文案，不空白
            showState(views, context.getString(R.string.widget_need_sync));
            return views;
        }

        views.setViewVisibility(R.id.widget_week_state, View.GONE);
        views.setViewVisibility(R.id.widget_week_grid, View.VISIBLE);
        views.setImageViewBitmap(R.id.widget_week_grid, bitmap);
        return views;
    }

    private static void showState(RemoteViews views, String state) {
        views.setViewVisibility(R.id.widget_week_grid, View.GONE);
        views.setViewVisibility(R.id.widget_week_state, View.VISIBLE);
        views.setTextViewText(R.id.widget_week_state, state);
    }

    // 位图只画网格本身，尺寸要和 ImageView 实际占的格子一致。
    // 关键：不能用 MAX_WIDTH/MAX_HEIGHT —— 它们是「横屏上限」，直接用会把位图拉宽一倍多，
    // 再被 fitXY 压扁，字就变形了。优先读 appWidgetSizes（竖屏/横屏各一个），按当前方向挑。
    private static int[] gridSize(Context context, int appWidgetId) {
        int[] fallback = {480, 360};
        Bundle options;
        try {
            options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId);
        } catch (RuntimeException error) {
            return fallback;
        }
        if (options == null) return fallback;

        boolean portrait = context.getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_PORTRAIT;
        int[] cell = widgetCellSize(options, portrait);
        if (cell == null) return fallback;

        float density = context.getResources().getDisplayMetrics().density;
        int widthPx = Math.round((cell[0] - H_PADDING_DP) * density);
        int heightPx = Math.round((cell[1] - CHROME_DP) * density);
        if (widthPx <= 0 || heightPx <= 0) return fallback;
        android.util.Log.d("JiaowuWidget", "gridSize cell=" + cell[0] + "x" + cell[1]
            + " portrait=" + portrait + " -> " + widthPx + "x" + heightPx);
        return new int[]{widthPx, heightPx};
    }

    // 返回组件占的格子尺寸（dp）；取与当前屏幕方向一致的那一份
    private static int[] widgetCellSize(Bundle options, boolean portrait) {
        java.util.ArrayList<android.os.Parcelable> sizes =
            android.os.Build.VERSION.SDK_INT >= 33
                ? options.getParcelableArrayList("appWidgetSizes", android.os.Parcelable.class)
                : options.getParcelableArrayList("appWidgetSizes");
        if (sizes != null) {
            for (android.os.Parcelable item : sizes) {
                if (!(item instanceof android.util.SizeF)) continue;
                android.util.SizeF size = (android.util.SizeF) item;
                boolean sizePortrait = size.getWidth() <= size.getHeight();
                if (sizePortrait == portrait) {
                    return new int[]{Math.round(size.getWidth()), Math.round(size.getHeight())};
                }
            }
        }

        // 旧版本没有 SIZES：MIN 是竖屏尺寸、MAX 是横屏尺寸
        int minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        int maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
        int maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        int width = portrait ? minW : maxW;
        int height = portrait ? maxH : minH;
        if (width <= 0 || height <= 0) return null;
        return new int[]{width, height};
    }

    // 周一=1 ... 周日=7，与 JS 侧 getWeekday 保持一致
    private static int todayWeekday() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int day = calendar.get(java.util.Calendar.DAY_OF_WEEK);
        return day == java.util.Calendar.SUNDAY ? 7 : day - 1;
    }
}
