package com.lzpu.jwxt.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * 4x4 周课表位图：7 天 × 12 节迷你网格。
 * 位图走 RemoteViews Binder，总像素必须受控（RGB_565 上限 400,000 px ≈ 780KB）。
 */
public final class WidgetBitmapRenderer {

    private static final int COLS = 7;
    private static final int ROWS = 12;
    private static final int MAX_PIXELS = 400_000;
    private static final int DEFAULT_WIDTH = 480;
    private static final int DEFAULT_HEIGHT = 360;
    private static final String[] WEEKDAYS = {"一", "二", "三", "四", "五", "六", "日"};

    private WidgetBitmapRenderer() {
    }

    public static Bitmap render(WidgetPayload payload, WidgetTheme theme,
                                int widthPx, int heightPx, int todayWeekday) {
        int[] size = fit(widthPx, heightPx);
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError error) {
            return null;
        }
        try {
            draw(bitmap, payload, theme, todayWeekday);
            Bitmap compact = bitmap.copy(Bitmap.Config.RGB_565, false);
            if (compact != null && compact != bitmap) {
                bitmap.recycle();
                return compact;
            }
            return bitmap;
        } catch (RuntimeException error) {
            bitmap.recycle();
            return null;
        }
    }

    private static int[] fit(int width, int height) {
        int w = width > 0 ? width : DEFAULT_WIDTH;
        int h = height > 0 ? height : DEFAULT_HEIGHT;
        long pixels = (long) w * h;
        if (pixels > MAX_PIXELS) {
            double scale = Math.sqrt((double) MAX_PIXELS / pixels);
            w = (int) Math.floor(w * scale);
            h = (int) Math.floor(h * scale);
        }
        return new int[]{Math.max(120, w), Math.max(120, h)};
    }

    private static void draw(Bitmap bitmap, WidgetPayload payload, WidgetTheme theme, int todayWeekday) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        Canvas canvas = new Canvas(bitmap);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTypeface(Typeface.DEFAULT);

        canvas.drawColor(theme.background);

        float unit = Math.min(w, h) / 100f;
        float gutter = w * 0.085f;
        float header = h * 0.10f;
        float gridLeft = gutter;
        float gridTop = header;
        float cellW = (w - gutter) / COLS;
        float cellH = (h - header) / ROWS;

        // 今天所在列淡淡高亮
        if (todayWeekday >= 1 && todayWeekday <= COLS) {
            fill.setColor(theme.dark ? 0x14FFFFFF : 0x0D1B5FAA);
            canvas.drawRect(
                gridLeft + (todayWeekday - 1) * cellW, gridTop,
                gridLeft + todayWeekday * cellW, gridTop + ROWS * cellH,
                fill);
        }

        // 网格线：横向每节一条但压到很淡，避免 12 条线糊成一片
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1f, unit * 0.1f));
        line.setColor((theme.border & 0x00FFFFFF) | 0x33000000);
        for (int row = 1; row < ROWS; row += 1) {
            float y = gridTop + row * cellH;
            canvas.drawLine(gridLeft, y, w, y, line);
        }
        for (int col = 1; col < COLS; col += 1) {
            float x = gridLeft + col * cellW;
            canvas.drawLine(x, gridTop, x, gridTop + ROWS * cellH, line);
        }

        // 星期表头
        float headSize = Math.min(header * 0.52f, unit * 3.2f);
        text.setTextSize(headSize);
        text.setTextAlign(Paint.Align.CENTER);
        for (int col = 0; col < COLS; col += 1) {
            text.setColor(col + 1 == todayWeekday ? theme.accent : theme.secondary);
            text.setTypeface(col + 1 == todayWeekday ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            canvas.drawText(WEEKDAYS[col], gridLeft + (col + 0.5f) * cellW, header * 0.74f, text);
        }

        // 左侧节次刻度：行高够就每节标，太挤就隔一节标
        text.setTypeface(Typeface.DEFAULT);
        float numSize = Math.min(cellH * 0.48f, unit * 2.6f);
        text.setTextSize(numSize);
        text.setColor(theme.tertiary);
        int step = cellH >= numSize * 1.6f ? 1 : 2;
        for (int row = 1; row <= ROWS; row += step) {
            float baseline = gridTop + (row - 0.5f) * cellH
                - (text.descent() + text.ascent()) / 2f;
            canvas.drawText(String.valueOf(row), gutter * 0.5f, baseline, text);
        }

        // 课程块
        float inset = Math.max(1f, unit * 0.32f);
        float radius = inset * 1.8f;
        for (WidgetPayload.WeekItem item : payload.week) {
            int col = item.weekday - 1;
            if (col < 0 || col >= COLS) continue;
            int rowStart = Math.max(0, item.start - 1);
            int rowEnd = Math.min(ROWS - 1, Math.max(rowStart, item.end - 1));
            RectF block = new RectF(
                gridLeft + col * cellW + inset,
                gridTop + rowStart * cellH + inset,
                gridLeft + (col + 1) * cellW - inset,
                gridTop + (rowEnd + 1) * cellH - inset);
            if (block.width() <= 0 || block.height() <= 0) continue;

            int color = parseColor(item.color, theme.accent);
            fill.setColor(color);
            canvas.drawRoundRect(block, radius, radius, fill);
            drawLabel(canvas, item.name, block, color, unit, text);
        }
    }

    // 先按字符数粗限（汉字 4 个、英文 8 个），再按块宽精确截断，保证不压到相邻列
    private static void drawLabel(Canvas canvas, String name, RectF block, int background,
                                  float unit, Paint text) {
        if (name == null || name.isEmpty()) return;
        float textSize = Math.min(block.height() * 0.40f, block.width() * 0.36f);
        textSize = Math.min(textSize, unit * 3.4f);
        if (textSize < unit * 1.35f) return;

        int cap = name.length() > 4 && !isCjk(name.charAt(0)) ? 8 : 4;
        String label = name.length() > cap ? name.substring(0, cap) : name;
        text.setTextSize(textSize);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(isLight(background) ? 0xFF1A1C1F : Color.WHITE);

        float available = block.width() - unit * 0.8f;
        while (label.length() > 1 && text.measureText(label) > available) {
            label = label.substring(0, label.length() - 1);
        }
        if (label.isEmpty()) return;

        float baseline = block.centerY() - (text.descent() + text.ascent()) / 2f;
        canvas.drawText(label, block.centerX(), baseline, text);
    }

    private static boolean isCjk(char c) {
        // 常用汉字区间；不用 Character.UnicodeScript（API 24+）
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    private static boolean isLight(int color) {
        double luminance = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color);
        return luminance > 150;
    }

    private static int parseColor(String value, int fallback) {
        try {
            return Color.parseColor(value);
        } catch (RuntimeException error) {
            return fallback;
        }
    }
}