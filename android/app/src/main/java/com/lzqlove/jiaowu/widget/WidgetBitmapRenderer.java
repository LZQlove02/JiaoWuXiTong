package com.lzqlove.jiaowu.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;

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
                                int widthPx, int heightPx, int todayWeekday, String emptyText) {
        int[] size = fit(widthPx, heightPx);
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError error) {
            return null;
        }
        try {
            draw(bitmap, payload, theme, todayWeekday, emptyText);
            // 透明 / 液态玻璃档必须保留 alpha（空网格要透出壁纸），只能用 ARGB_4444；
            // 不透明档的画布已经铺满卡片色（没有透明像素），改用 RGB_565：
            // ARGB_4444 每通道只有 16 级，20% 的浅色叠在白卡上会被量化成中性灰（真机已踩）。
            Bitmap.Config config = theme.isOpaque()
                ? Bitmap.Config.RGB_565
                : Bitmap.Config.ARGB_4444;
            Bitmap compact = bitmap.copy(config, false);
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

    private static void draw(Bitmap bitmap, WidgetPayload payload, WidgetTheme theme,
                             int todayWeekday, String emptyText) {
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
            text.setColor(today ? theme.todayAccent() : theme.secondary);
            text.setTypeface(today ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            float centerX = gridLeft + (col + 0.5f) * cellW;
            canvas.drawText(WEEKDAYS[col], centerX, header * 0.72f, text);

            // 「今天」标记：表头下面一颗圆点，三档样式统一给（原来只有玻璃档有）
            if (today) {
                fill.setColor(theme.todayAccent());
                fill.setStyle(Paint.Style.FILL);
                canvas.drawCircle(centerX, header * 0.92f, Math.max(1.5f, unit * 0.55f), fill);
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
        // 相邻两门课之间要留出看得见的缝：0.32% 在 480px 宽下只有 1.15px，几乎贴在一起
        float inset = Math.max(1.5f, unit * 0.5f);
        float radius = Math.max(inset * 1.8f, unit * 1.1f);
        boolean liquidGlass = theme.isTransparent();
        for (WidgetPayload.WeekItem item : mergeBlocks(payload.week)) {
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

            if (liquidGlass) {
                // 全透明档：液态玻璃块（中性白，模拟壁纸透过磨砂玻璃）
                fill.setColor(theme.glassFill());
                canvas.drawRoundRect(block, radius, radius, fill);
                // 顶边高光：一条亮线，两端让开圆角
                edge.setStyle(Paint.Style.STROKE);
                edge.setStrokeWidth(Math.max(1f, unit * 0.18f));
                edge.setColor(theme.glassHighlight());
                float lineY = block.top + edge.getStrokeWidth() / 2f;
                canvas.drawLine(block.left + radius, lineY, block.right - radius, lineY, edge);
            } else {
                // 不透明 / 液态玻璃档：冷色三档，无描边，仅 20% / 40% 填充
                fill.setColor(theme.courseFill(tierOf(item.name)));
                canvas.drawRoundRect(block, radius, radius, fill);
            }

            drawLabel(canvas, item.name, item.room, block, theme.courseInk(), unit, text);
        }

        // 本周没有课：网格照画（表头与节次留着，版面不塌），中央补一句占位
        if (payload.week.isEmpty() && emptyText != null && !emptyText.isEmpty()) {
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(Math.max(unit * 3.2f, 11f));
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(theme.tertiary);
            text.clearShadowLayer();
            float centerX = (gridLeft + w) / 2f;
            float centerY = gridTop + ROWS * cellH / 2f;
            canvas.drawText(emptyText, centerX, centerY - (text.ascent() + text.descent()) / 2f, text);
        }
    }

    // 同一天里首尾相接、名字相同的课合并成一整块：连堂（2 节 + 2 节）不再被切成两段，
    // 视觉上和课表里的「一次课」对得上，也少画一次课名
    private static List<WidgetPayload.WeekItem> mergeBlocks(List<WidgetPayload.WeekItem> items) {
        List<WidgetPayload.WeekItem> merged = new ArrayList<>();
        for (WidgetPayload.WeekItem item : items) {
            if (item == null) continue;
            WidgetPayload.WeekItem hit = null;
            for (WidgetPayload.WeekItem candidate : merged) {
                if (candidate.weekday == item.weekday
                    && candidate.name.equals(item.name)
                    && candidate.room.equals(item.room)
                    && item.start <= candidate.end + 1
                    && item.start >= candidate.start) {
                    hit = candidate;
                    break;
                }
            }
            if (hit == null) {
                WidgetPayload.WeekItem copy = new WidgetPayload.WeekItem();
                copy.weekday = item.weekday;
                copy.start = item.start;
                copy.end = item.end;
                copy.name = item.name;
                copy.room = item.room;
                merged.add(copy);
            } else {
                hit.end = Math.max(hit.end, item.end);
            }
        }
        return merged;
    }

    // 课程名排版：两节以上折两行（每行 2 字），单节块一行 4 字。
    // 高块（三节以上）把校名贴顶、教室紧跟在下面，不再是「标题浮在块中间、下面一大片空」。
    private static void drawLabel(Canvas canvas, String name, String room, RectF block,
                                  int ink, float unit, Paint text) {
        if (name == null || name.isEmpty()) return;

        float maxW = block.width() - unit * 1.0f;
        float maxH = block.height() - unit * 1.2f;
        if (maxW <= 0 || maxH <= 0) return;

        boolean cjk = isCjk(name.charAt(0));
        float size;
        String[] parts;
        if (cjk) {
            boolean tall = block.height() >= unit * 7.2f && name.length() > 2;
            int maxLines = tall ? 2 : 1;
            int perLine = tall ? 2 : 4;
            size = Math.min(maxW / (perLine + 0.3f), maxH / (maxLines * 1.12f));
            size = Math.min(size, unit * (maxLines == 2 ? 5.6f : 5.2f));
            if (size < unit * 1.6f) return;
            int capacity = perLine * maxLines;
            String label = name.length() > capacity ? name.substring(0, capacity) : name;
            int count = Math.min(maxLines, (int) Math.ceil(label.length() / (double) perLine));
            parts = new String[count];
            for (int i = 0; i < count; i += 1) {
                int from = i * perLine;
                parts[i] = label.substring(from, Math.min(label.length(), from + perLine));
            }
        } else {
            // 英文（或中英混排）按真实字宽算字号：拉丁字符约 0.55em，
            // 套汉字「每行几个字」的算法会把字压到一半。
            // 1~3 行里选字号最大的折法：长名字多折一行，字号就能拉回来。
            String trimmed = name.trim();
            String[] words = trimmed.split("\\s+");
            float ref = 100f;
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(ref);
            String[] best = new String[]{trimmed};
            float bestSize = 0f;
            int maxLines = Math.min(3, Math.max(1, words.length));
            for (int lineCount = 1; lineCount <= maxLines; lineCount += 1) {
                if (lineCount * unit * 1.9f > maxH) break;
                String[] candidate = partitionWords(words, lineCount, text);
                float widest = 0f;
                for (String line : candidate) widest = Math.max(widest, text.measureText(line));
                if (widest <= 0f) continue;
                float candidateSize = Math.min(
                    maxW * 0.96f * ref / widest,
                    Math.min(maxH / (lineCount * 1.12f), unit * 5.2f));
                if (candidateSize > bestSize) {
                    bestSize = candidateSize;
                    best = candidate;
                }
            }
            if (bestSize <= 0f) return;
            size = bestSize;
            parts = best;
            text.setTextSize(size);
            float target = maxW * 0.96f;
            for (int i = 0; i < parts.length; i += 1) {
                String part = parts[i];
                while (part.length() > 1 && text.measureText(part) > target) {
                    part = part.substring(0, part.length() - 1);
                }
                parts[i] = part;
            }
            if (size < unit * 1.4f) return;
        }

        // 课名 + 教室必须同时显示（《小组件执行方案》§7.3 硬约束）：
        // 只剩很窄的块时逐档缩字号，直到两行都装得下，绝不省略教室
        String roomLabel = compactRoom(room);
        boolean hasRoom = !roomLabel.isEmpty();
        // 教室字号只由「块宽」决定，不跟课名字号走：否则大块里教室会被放大到宽度放不下
        float roomSize = unit * 2.6f;
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(roomSize);
        int guard = 0;
        while (hasRoom && roomSize > unit * 1.1f && text.measureText(roomLabel) > maxW * 0.98f && guard < 40) {
            roomSize *= 0.9f;
            text.setTextSize(roomSize);
            guard += 1;
        }
        float roomGap = roomSize * 0.45f;
        int lineCount = parts.length;

        for (int attempt = 0; attempt < 12 && hasRoom; attempt += 1) {
            float needH = size * 1.12f * lineCount + roomGap + roomSize;
            if (needH <= maxH) break;
            size *= 0.9f;
            if (size < unit * 1.2f) break;
        }

        // 教室字号再跟课名收一档：单节块里课名被压小后，教室不能反而比课名还大
        if (hasRoom) {
            roomSize = Math.min(roomSize, Math.max(unit * 1.1f, size * 0.62f));
            roomGap = roomSize * 0.45f;
        }

        float lineH = size * 1.12f;
        float nameH = lineH * lineCount;
        float roomH = hasRoom ? roomGap + roomSize : 0f;
        float totalH = nameH + roomH;
        float textTop = block.top + Math.max(0f, (block.height() - totalH) / 2f);

        text.setTextSize(size);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(ink);
        // 块是半透明的，字压在浅色块上，弱阴影足够；别太重，小字号会糊
        text.setShadowLayer(Math.max(1f, unit * 0.45f), 0f, unit * 0.14f, 0x40000000);

        float baselineOffset = (text.ascent() + text.descent()) / 2f;
        for (int i = 0; i < parts.length; i += 1) {
            String part = parts[i];
            if (part == null || part.isEmpty()) continue;
            while (part.length() > 1 && text.measureText(part) > maxW) {
                part = part.substring(0, part.length() - 1);
            }
            float baseline = textTop + lineH * (i + 0.5f) - baselineOffset;
            canvas.drawText(part, block.centerX(), baseline, text);
        }

        if (hasRoom) {
            text.setTextSize(roomSize);
            text.setTypeface(Typeface.DEFAULT);
            text.setColor((ink & 0x00FFFFFF) | 0xB3000000);   // 教室行：同一字色的 70%
            String part = roomLabel;
            while (part.length() > 1 && text.measureText(part) > maxW) {
                part = part.substring(0, part.length() - 1);
            }
            float centerY = textTop + nameH + roomGap + roomSize * 0.5f;
            float baseline = centerY - (text.ascent() + text.descent()) / 2f;
            canvas.drawText(part, block.centerX(), baseline, text);
        }
    }

    // 冷色三档按课程名哈希固定分配：同一门课每次刷新、每个尺寸都落在同一档
    private static int tierOf(String name) {
        if (name == null || name.isEmpty()) return 0;
        return Math.floorMod(name.hashCode(), 3);
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

    // 按词把名字均分成 lines 行，保持词序；尽量让每行宽度接近
    private static String[] partitionWords(String[] words, int lines, Paint paint) {
        if (lines <= 1) {
            StringBuilder all = new StringBuilder();
            for (String word : words) {
                if (all.length() > 0) all.append(' ');
                all.append(word);
            }
            return new String[]{all.toString()};
        }
        float total = 0f;
        for (String word : words) {
            total += paint.measureText(word) + paint.measureText(" ");
        }
        float target = total / lines;
        String[] out = new String[lines];
        int index = 0;
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String next = current.length() == 0 ? word : current + " " + word;
            if (index < lines - 1 && current.length() > 0 && paint.measureText(next) > target) {
                out[index] = current.toString();
                index += 1;
                current.setLength(0);
                current.append(word);
            } else {
                current.setLength(0);
                current.append(next);
            }
        }
        if (current.length() > 0) {
            out[Math.min(index, lines - 1)] = current.toString();
        }
        int count = 0;
        for (String line : out) if (line != null && !line.isEmpty()) count += 1;
        String[] result = new String[count];
        int at = 0;
        for (String line : out) {
            if (line != null && !line.isEmpty()) result[at++] = line;
        }
        return result;
    }
}