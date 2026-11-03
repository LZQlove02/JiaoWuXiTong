package com.lzqlove.jiaowu.widget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 自定义文字色解析：只认 #RRGGBB，其它一律当「没设置」走主题默认色。
 */
public class WidgetThemeTextColorTest {

    @Test
    public void parsesHexToOpaqueColor() {
        assertEquals(0xFFFFFFFF, WidgetTheme.parseTextColor("#FFFFFF"));
        assertEquals(0xFF000000, WidgetTheme.parseTextColor("#000000"));
        assertEquals(0xFF1B5FAA, WidgetTheme.parseTextColor("#1b5faa"));
        assertEquals(0xFF1B5FAA, WidgetTheme.parseTextColor(" #1B5FAA "));
    }

    @Test
    public void rejectsInvalidValues() {
        assertEquals(0, WidgetTheme.parseTextColor(null));
        assertEquals(0, WidgetTheme.parseTextColor(""));
        assertEquals(0, WidgetTheme.parseTextColor("white"));
        assertEquals(0, WidgetTheme.parseTextColor("#FFF"));
        assertEquals(0, WidgetTheme.parseTextColor("#GGGGGG"));
        assertEquals(0, WidgetTheme.parseTextColor("#FFFFFF00"));
    }
}
