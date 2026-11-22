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

    public static Bitmap render(WidgetTheme theme, List<WidgetPayload.WeekItem> items,
                                int widthPx, int heightPx, int todayWeekday, String emptyText) {
        int[] size = fit(widthPx, heightPx);
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError error) {
            return null;
        }
        try {
            draw(bitmap, items, theme, todayWeekday, emptyText);
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

    private static void draw(Bitmap bitmap, List<WidgetPayload.WeekItem> items, WidgetTheme theme,
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
        for (WidgetPayload.WeekItem item : mergeBlocks(items)) {
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

        // 这一周没有课：网格照画（表头与节次留着，版面不塌），中央补一句占位
        if (items.isEmpty() && emptyText != null && !emptyText.isEmpty()) {
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

    // 课程名排版：按课程块的真实宽高枚举 1~3 行 × 每行 2~4 字的折法，
    // 在「字号不低于可读下限」的方案里选显示字数最多的一种（同字数取字号更大的），
    // 放不下的部分截断并补「…」；教室行始终保留（《小组件执行方案》§7.3 硬约束）。
    private static void drawLabel(Canvas canvas, String name, String room, RectF block,
                                  int ink, float unit, Paint text) {
        if (name == null || name.isEmpty()) return;

        float maxW = block.width() - unit * 1.0f;
        float maxH = block.height() - unit * 1.2f;
        if (maxW <= 0 || maxH <= 0) return;

        String roomLabel = compactRoom(room);
        boolean hasRoom = !roomLabel.isEmpty();
        // 可读下限：低于这个字号宁可少显示字，也不把课名压成小字
        float floorSize = unit * 2.8f;

        boolean cjk = isCjk(name.charAt(0));
        float size;
        String[] parts;
        if (cjk) {
            int bestPerLine = Math.min(4, Math.max(1, name.length()));
            int bestLines = 1;
            float bestSize = 0f;
            int bestVisible = -1;
            for (int perLine = 4; perLine >= 2; perLine -= 1) {
                int lines = Math.min(3, Math.max(1,
                    (int) Math.ceil(name.length() / (double) perLine)));
                float candidate = nameSizeFor(lines, perLine, maxW, maxH, unit, hasRoom);
                if (candidate < floorSize) continue;
                int capacity = lines * perLine;
                // 名字超过容量时要给省略号留一格，实际可见字数再少一个
                int visible = name.length() > capacity ? capacity - 1 : name.length();
                if (visible > bestVisible || (visible == bestVisible && candidate > bestSize)) {
                    bestVisible = visible;
                    bestSize = candidate;
                    bestPerLine = perLine;
                    bestLines = lines;
                }
            }
            if (bestSize <= 0f) {
                // 块太矮，所有方案都低于下限：退回单行最小字号，由后面的收缩兜底
                bestSize = floorSize;
                bestPerLine = Math.max(1, Math.min(4, name.length()));
                bestLines = 1;
            }
            size = bestSize;
            int capacity = bestLines * bestPerLine;
            boolean truncated = name.length() > capacity;
            String label = truncated
                ? name.substring(0, Math.max(1, capacity - 1))
                : name;
            // 省略号也算一格；按总格数均分到各行，避免出现「最后一行只剩一个字」
            int glyphs = label.length() + (truncated ? 1 : 0);
            int count = Math.min(bestLines, Math.max(1, (int) Math.ceil(
                glyphs / (double) bestPerLine)));
            parts = new String[count];
            int base = glyphs / count;
            int extra = glyphs % count;
            int at = 0;
            for (int i = 0; i < count; i += 1) {
                int take = base + (i < extra ? 1 : 0);
                if (truncated && i == count - 1) take -= 1;   // 最后一格让给「…」
                int from = Math.min(at, label.length());
                int to = Math.min(label.length(), from + Math.max(0, take));
                parts[i] = label.substring(from, to);
                at = to;
                if (truncated && i == count - 1) parts[i] = parts[i] + "…";
            }
        } else {
            // 英文（或中英混排）按真实字宽算字号：拉丁字符约 0.55em，
            // 套汉字「每行几个字」的算法会把字压到一半。
            // 名字切成「拉丁单词 / 单个汉字」小块后折行，没有空格的长名字
            // （如 Vue.Js前端框架技术）也能在汉字间断开，不会整块留白。
            String trimmed = name.trim();
            List<String> tokens = splitTokens(trimmed);
            float ref = 100f;
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(ref);
            String[] best = new String[]{trimmed};
            float bestSize = 0f;
            int maxLines = Math.min(3, Math.max(1, tokens.size()));
            for (int lineCount = 1; lineCount <= maxLines; lineCount += 1) {
                // 行高至少要装得下下限字号，否则这个折法不可读
                if (lineCount * floorSize * 1.12f > maxH) break;
                String[] candidate = partitionTokens(tokens, lineCount, text);
                float widest = 0f;
                for (String line : candidate) widest = Math.max(widest, text.measureText(line));
                if (widest <= 0f) continue;
                float candidateSize = mixedSize(widest, ref, maxW, maxH, lineCount, unit, hasRoom);
                if (candidateSize > bestSize) {
                    bestSize = candidateSize;
                    best = candidate;
                }
            }
            if (bestSize <= 0f) bestSize = floorSize;
            size = Math.max(bestSize, floorSize);
            parts = best;
        }

        // 课名 + 教室必须同时显示（§7.3 硬约束）：
        // 教室字号跟课名联动，块太矮时先缩课名，直到课名与教室都装得下
        float roomSize = hasRoom ? roomSizeFor(size, unit) : 0f;
        float roomGap = roomSize * 0.45f;
        int lineCount = parts.length;
        if (hasRoom) {
            roomSize = roomSizeForWidth(roomLabel, roomSize, maxW, unit, text);
            // 宽度能容纳的字号是上限：下面缩课名时不能把教室字号放大回去
            float roomMaxByWidth = roomSize;
            roomGap = roomSize * 0.45f;
            for (int attempt = 0; attempt < 12; attempt += 1) {
                float needH = size * 1.12f * lineCount + roomGap + roomSize;
                if (needH <= maxH) break;
                size *= 0.9f;
                roomSize = Math.min(roomSizeFor(size, unit), roomMaxByWidth);
                roomGap = roomSize * 0.45f;
                text.setTextSize(roomSize);
                if (size < unit * 1.2f) break;
            }
            // 缩课名后再校一次宽度，保证教室行始终不出块
            roomSize = roomSizeForWidth(roomLabel, roomSize, maxW, unit, text);
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
        // 不加描边/阴影：小字号下阴影会糊成一圈明显的轮廓，看起来像描边
        text.clearShadowLayer();

        // 超出块宽的行逐字截断；混排名字在最后一行补「…」，提示还有内容
        boolean cut = false;
        for (int i = 0; i < parts.length; i += 1) {
            String part = parts[i];
            if (part == null) continue;
            if (text.measureText(part) > maxW) {
                while (part.length() > 1 && text.measureText(part) > maxW) {
                    part = part.substring(0, part.length() - 1);
                }
                parts[i] = part;
                cut = true;
            }
        }
        if (!cjk && cut) {
            for (int i = parts.length - 1; i >= 0; i -= 1) {
                String part = parts[i] == null ? "" : parts[i];
                if (part.isEmpty() && i > 0) continue;
                String marked = part + "…";
                while (!part.isEmpty() && text.measureText(marked) > maxW) {
                    part = part.substring(0, part.length() - 1);
                    marked = part + "…";
                }
                parts[i] = marked;
                break;
            }
        }

        float baselineOffset = (text.ascent() + text.descent()) / 2f;
        for (int i = 0; i < parts.length; i += 1) {
            String part = parts[i];
            if (part == null || part.isEmpty()) continue;
            float baseline = textTop + lineH * (i + 0.5f) - baselineOffset;
            canvas.drawText(part, block.centerX(), baseline, text);
        }

        if (hasRoom) {
            text.setTextSize(roomSize);
            text.setTypeface(Typeface.DEFAULT);
            text.setColor((ink & 0x00FFFFFF) | 0xB3000000);   // 教室行：同一字色的 70%
            // 教室号仍超出块宽（如带括号说明的长教室名）时逐字截断，保留楼栋号前缀
            String roomText = roomLabel;
            while (roomText.length() > 1 && text.measureText(roomText) > maxW) {
                roomText = roomText.substring(0, roomText.length() - 1);
            }
            float centerY = textTop + nameH + roomGap + roomSize * 0.5f;
            float baseline = centerY - (text.ascent() + text.descent()) / 2f;
            canvas.drawText(roomText, block.centerX(), baseline, text);
        }
    }

    // 汉字课名：给定行数 / 每行字数，算能放下的最大字号（含教室行预留）
    private static float nameSizeFor(int lines, int perLine, float maxW, float maxH,
                                     float unit, boolean hasRoom) {
        float size = Math.min(maxW / (perLine + 0.3f), maxH / (lines * 1.12f));
        size = Math.min(size, unit * (lines > 1 ? 5.6f : 5.2f));
        if (hasRoom) {
            for (int round = 0; round < 3; round += 1) {
                float cap = maxH - roomSizeFor(size, unit) * 1.45f;
                if (cap <= 0f) return 0f;
                size = Math.min(size, cap / (lines * 1.12f));
            }
        }
        return size;
    }

    // 混排名字：按实测最宽一行的字宽算最大字号（含教室行预留）
    private static float mixedSize(float widest, float ref, float maxW, float maxH,
                                   int lines, float unit, boolean hasRoom) {
        float size = Math.min(maxW * 0.96f * ref / widest, unit * 5.2f);
        for (int round = 0; round < 3; round += 1) {
            float cap = maxH;
            if (hasRoom) {
                cap -= roomSizeFor(size, unit) * 1.45f;
            }
            if (cap <= 0f) return 0f;
            size = Math.min(size, cap / (lines * 1.12f));
        }
        return size;
    }

    // 教室字号跟课名字号联动（上限 2.6×unit），保证教室不会比课名还大
    private static float roomSizeFor(float nameSize, float unit) {
        return Math.min(unit * 2.6f, Math.max(unit * 1.1f, nameSize * 0.62f));
    }

    // 按块宽收敛教室字号：文本超宽就逐档缩小，最低到 1.1×unit
    private static float roomSizeForWidth(String label, float size, float maxW, float unit, Paint text) {
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(size);
        int guard = 0;
        while (size > unit * 1.1f && text.measureText(label) > maxW * 0.98f && guard < 40) {
            size *= 0.9f;
            text.setTextSize(size);
            guard += 1;
        }
        return size;
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

    // 名字切成可折行的最小块：拉丁单词整体一块、空白丢弃、汉字逐字一块。
    // 「Vue.Js前端框架技术」没有空格，也能按汉字断成多行。
    private static List<String> splitTokens(String name) {
        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < name.length(); i += 1) {
            char c = name.charAt(i);
            if (isCjk(c)) {
                if (word.length() > 0) {
                    tokens.add(word.toString());
                    word.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else if (Character.isWhitespace(c)) {
                if (word.length() > 0) {
                    tokens.add(word.toString());
                    word.setLength(0);
                }
            } else {
                word.append(c);
            }
        }
        if (word.length() > 0) tokens.add(word.toString());
        return tokens;
    }

    // 汉字之间连写，相邻两个拉丁块之间补回一个空格（原来的空白在切块时已丢弃）
    private static String joinTokens(List<String> tokens, int from, int to) {
        StringBuilder line = new StringBuilder();
        boolean prevLatin = false;
        for (int i = from; i < to; i += 1) {
            String token = tokens.get(i);
            boolean latin = !isCjk(token.charAt(0));
            if (line.length() > 0 && latin && prevLatin) line.append(' ');
            line.append(token);
            prevLatin = latin;
        }
        return line.toString();
    }

    // 按块把名字均分成 lines 行，保持块序；尽量让每行宽度接近 total / lines
    private static String[] partitionTokens(List<String> tokens, int lines, Paint paint) {
        if (tokens.isEmpty()) return new String[0];
        if (lines <= 1) return new String[]{joinTokens(tokens, 0, tokens.size())};
        float[] widths = new float[tokens.size()];
        float total = 0f;
        for (int i = 0; i < tokens.size(); i += 1) {
            widths[i] = paint.measureText(tokens.get(i));
            total += widths[i];
        }
        float space = paint.measureText(" ");
        float target = total / lines;
        List<String> out = new ArrayList<>();
        int start = 0;
        int placed = 0;
        float current = 0f;
        for (int i = 0; i < tokens.size() && placed < lines - 1; i += 1) {
            boolean latin = !isCjk(tokens.get(i).charAt(0));
            boolean gap = i > start && latin && !isCjk(tokens.get(i - 1).charAt(0));
            float next = widths[i] + (gap ? space : 0f);
            if (i > start && current + next > target) {
                out.add(joinTokens(tokens, start, i));
                start = i;
                placed += 1;
                current = 0f;
            }
            current += next;
        }
        out.add(joinTokens(tokens, start, tokens.size()));
        return out.toArray(new String[0]);
    }
}