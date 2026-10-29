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

            drawLabel(canvas, item.name, item.room, block, color, unit, text);
        }
    }

    // 课程名排版：两节以上折两行（每行 2 字），单节块一行 4 字。
    // 高块（三节以上）把校名贴顶、教室紧跟在下面，不再是「标题浮在块中间、下面一大片空」。
    private static void drawLabel(Canvas canvas, String name, String room, RectF block,
                                  int background, float unit, Paint text) {
        if (name == null || name.isEmpty()) return;

        float maxW = block.width() - unit * 1.0f;
        float maxH = block.height() - unit * 1.2f;
        if (maxW <= 0 || maxH <= 0) return;

        boolean cjk = isCjk(name.charAt(0));
        boolean tall = block.height() >= unit * 7.2f && name.length() > 2;
        int lines = cjk ? (tall ? 2 : 1) : 1;
        int perLine = cjk ? (tall ? 2 : 4) : 6;

        float size = Math.min(maxW / (perLine + 0.3f), maxH / (lines * 1.12f));
        size = Math.min(size, unit * (lines == 2 ? 5.6f : 5.2f));
        if (size < unit * 1.6f) return;

        int capacity = perLine * lines;
        String label = name.length() > capacity ? name.substring(0, capacity) : name;

        int lineCount = Math.min(lines, (int) Math.ceil(label.length() / (double) perLine));
        float lineH = size * 1.12f;
        float nameH = lineH * lineCount;

        // 教室：有空间才画，字号约为课名的 0.6；只保留教室号（去掉「敏行楼」这类中文楼名），
        // 一行放得下才不挤
        String roomLabel = compactRoom(room);
        float roomSize = Math.min(unit * 3.0f, Math.max(unit * 1.8f, size * 0.62f));
        float roomGap = roomSize * 0.5f;
        boolean showRoom = !roomLabel.isEmpty()
            && block.height() - nameH >= roomSize * 1.9f
            && roomSize >= unit * 1.5f;

        float textTop;
        if (showRoom) {
            float need = nameH + roomGap + roomSize;
            textTop = Math.min(block.top + unit * 1.2f, block.bottom - unit * 0.8f - need);
            textTop = Math.max(textTop, block.top + unit * 0.6f);
        } else {
            textTop = block.centerY() - nameH / 2f;
        }

        text.setTextSize(size);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(isLight(background) ? 0xFF1A1C1F : Color.WHITE);
        // 课程块内的字贴在实色块上，用一层弱黑影就够；别太重，否则小字号会糊
        text.setShadowLayer(Math.max(1f, unit * 0.45f), 0f, unit * 0.14f, 0x59000000);

        float baselineOffset = (text.ascent() + text.descent()) / 2f;
        for (int i = 0; i < lineCount; i += 1) {
            int from = i * perLine;
            int to = Math.min(label.length(), from + perLine);
            String part = label.substring(from, to);
            while (part.length() > 1 && text.measureText(part) > maxW) {
                part = part.substring(0, part.length() - 1);
            }
            if (part.isEmpty()) continue;
            float baseline = textTop + lineH * (i + 0.5f) - baselineOffset;
            canvas.drawText(part, block.centerX(), baseline, text);
        }

        if (showRoom) {
            text.setTextSize(roomSize);
            text.setTypeface(Typeface.DEFAULT);
            text.setColor(isLight(background) ? 0xD91A1C1F : 0xD9FFFFFF);
            String part = roomLabel;
            while (part.length() > 1 && text.measureText(part) > maxW) {
                part = part.substring(0, part.length() - 1);
            }
            float centerY = textTop + nameH + roomGap + roomSize * 0.5f;
            float baseline = centerY - (text.ascent() + text.descent()) / 2f;
            canvas.drawText(part, block.centerX(), baseline, text);
        }
    }

    private static boolean isCjk(char c) {
        // 常用汉字区间；不用 Character.UnicodeScript（API 24+）
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    // 「敏行楼T1-D302」→「T1-D302」：楼名在 4x4 的小方块里放不下，
    // 教室号本身已经带了楼栋代号（T1/T2），去掉中文前缀更清楚。
    private static String compactRoom(String room) {
        if (room == null) return "";
        String text = room.trim();
        int cut = 0;
        while (cut < text.length() && isCjk(text.charAt(cut))) cut += 1;
        if (cut > 0 && cut < text.length()) return text.substring(cut).trim();
        return text;
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