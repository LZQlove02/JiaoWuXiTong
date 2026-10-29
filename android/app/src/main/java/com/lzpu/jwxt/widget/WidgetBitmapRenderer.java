package com.lzpu.jwxt.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * 4x4 周课表位图：7 天 × 12 节迷你网格。
 * 位图走 RemoteViews Binder，总像素必须受控（ARGB_4444 上限 400,000 px ≈ 780KB，且必须保留 alpha）。
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
            // 必须保留 alpha：卡片是透明的，转 RGB_565 会把空白区压成黑色
            // 用 ARGB_4444（2 字节/像素、带 alpha）把 Binder 开销压在 800KB 以内
            Bitmap compact = bitmap.copy(Bitmap.Config.ARGB_4444, false);
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
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTypeface(Typeface.DEFAULT);

        float unit = Math.min(w, h) / 100f;
        float gutter = w * 0.085f;
        float header = h * 0.10f;
        float gridLeft = gutter;
        float gridTop = header;
        float cellW = (w - gutter) / COLS;
        float cellH = (h - header) / ROWS;

        // 背景样式：不透明铺满卡片色；玻璃卡片本身有渐变底，位图保持透明；全透明同样透明
        if (theme.isOpaque()) {
            canvas.drawColor(theme.background);
        }

        // 今天所在列：玻璃档不铺整列色块（会和课程块叠成一片），改成星期下方的圆点
        boolean glass = theme.isGlass();
        if (!glass && todayWeekday >= 1 && todayWeekday <= COLS) {
            fill.setColor(theme.todayTint());
            canvas.drawRect(
                gridLeft + (todayWeekday - 1) * cellW, gridTop,
                gridLeft + todayWeekday * cellW, gridTop + ROWS * cellH,
                fill);
        }

        // 网格线：横向每节一条但压到很淡，避免 12 条线糊成一片
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1f, unit * 0.1f));
        line.setColor(theme.gridLine());
        for (int row = 1; row < ROWS; row += 1) {
            float y = gridTop + row * cellH;
            canvas.drawLine(gridLeft, y, w, y, line);
        }
        for (int col = 1; col < COLS; col += 1) {
            float x = gridLeft + col * cellW;
            canvas.drawLine(x, gridTop, x, gridTop + ROWS * cellH, line);
        }

        // 星期表头：文字直接用主题深色，不再描白/黑光晕（光晕会把 8px 的细字洗成灰色）
        float headSize = Math.min(header * 0.58f, unit * 4f);
        text.setTextSize(headSize);
        text.setTextAlign(Paint.Align.CENTER);
        text.clearShadowLayer();
        for (int col = 0; col < COLS; col += 1) {
            boolean today = col + 1 == todayWeekday;
            text.setColor(today ? theme.accent : theme.secondary);
            text.setTypeface(today ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            float centerX = gridLeft + (col + 0.5f) * cellW;
            canvas.drawText(WEEKDAYS[col], centerX, header * 0.74f, text);

            // 玻璃档的「今天」标记：表头文字下面一颗小圆点
            if (glass && today) {
                fill.setColor(theme.accent);
                fill.setStyle(Paint.Style.FILL);
                canvas.drawCircle(centerX, header * 0.93f, Math.max(1.5f, unit * 0.62f), fill);
            }
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
        // 玻璃档用更大的圆角（约 6dp）并加一圈内描边，相邻两门课才分得开
        float radius = glass ? Math.max(inset * 2f, unit * 2.2f) : inset * 1.8f;
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

            if (glass) {
                edge.setStyle(Paint.Style.STROKE);
                edge.setStrokeWidth(Math.max(1f, unit * 0.16f));
                edge.setColor(theme.blockEdge());
                RectF rim = new RectF(block);
                float half = edge.getStrokeWidth() / 2f;
                rim.inset(half, half);
                canvas.drawRoundRect(rim, radius, radius, edge);
            }

            drawLabel(canvas, item.name, block, color, unit, text);
        }
    }

    // 课程名自适应：两节以上的块折成两行（每行两个汉字），单节块一行四个字。
    // 字号先按「每行字数」和「行高」取最大值，再压到字号上限，比原来固定 0.36×块宽大 40%~90%。
    private static void drawLabel(Canvas canvas, String name, RectF block, int background,
                                  float unit, Paint text) {
        if (name == null || name.isEmpty()) return;

        float maxW = block.width() - unit * 0.7f;
        float maxH = block.height() - unit * 0.5f;
        if (maxW <= 0 || maxH <= 0) return;

        boolean cjk = isCjk(name.charAt(0));
        boolean tall = block.height() >= unit * 7.2f && name.length() > 2;
        int lines = cjk ? (tall ? 2 : 1) : 1;
        int perLine = cjk ? (tall ? 2 : 4) : 6;

        float size = Math.min(maxW / (perLine + 0.3f), maxH / (lines * 1.2f));
        size = Math.min(size, unit * (tall ? 5.8f : 5.2f));
        if (size < unit * 1.6f) return;

        int capacity = perLine * lines;
        String label = name.length() > capacity ? name.substring(0, capacity) : name;

        text.setTextSize(size);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(isLight(background) ? 0xFF1A1C1F : Color.WHITE);
        // 课程块内的字贴在实色块上，用一层弱黑影就够；别太重，否则小字号会糊
        text.setShadowLayer(Math.max(1f, unit * 0.45f), 0f, unit * 0.14f, 0x59000000);

        int lineCount = Math.min(lines, (int) Math.ceil(label.length() / (double) perLine));
        float lineH = size * 1.18f;
        float top = block.centerY() - lineH * lineCount / 2f;
        float baselineOffset = (text.ascent() + text.descent()) / 2f;

        for (int i = 0; i < lineCount; i += 1) {
            int from = i * perLine;
            int to = Math.min(label.length(), from + perLine);
            String part = label.substring(from, to);
            while (part.length() > 1 && text.measureText(part) > maxW) {
                part = part.substring(0, part.length() - 1);
            }
            if (part.isEmpty()) continue;
            float baseline = top + lineH * (i + 0.5f) - baselineOffset;
            canvas.drawText(part, block.centerX(), baseline, text);
        }
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