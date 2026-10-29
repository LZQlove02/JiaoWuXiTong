package com.lzpu.jwxt.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

import com.lzpu.jwxt.R;

/**
 * 组件配色：与 App 内 theme.css 的变量保持一致，
 * system 时跟随系统深浅色，light/dark 由 App 主题设置指定。
 */
public final class WidgetTheme {

    public static final String SYSTEM = "system";
    public static final String LIGHT = "light";
    public static final String DARK = "dark";

    public final boolean dark;
    public final int background;
    public final int text;
    public final int secondary;
    public final int tertiary;
    public final int border;
    public final int accent;
    // 背景样式：glass / transparent / opaque
    public final String style;

    private WidgetTheme(boolean dark, String style, int background, int text, int secondary,
                        int tertiary, int border, int accent) {
        this.dark = dark;
        this.style = style;
        this.background = background;
        this.text = text;
        this.secondary = secondary;
        this.tertiary = tertiary;
        this.border = border;
        this.accent = accent;
    }

    public static WidgetTheme resolve(Context context, String storedTheme, String style) {
        boolean isDark;
        if (LIGHT.equals(storedTheme)) {
            isDark = false;
        } else if (DARK.equals(storedTheme)) {
            isDark = true;
        } else {
            int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
            isDark = mode == Configuration.UI_MODE_NIGHT_YES;
        }
        return isDark ? dark(style) : light(style);
    }

    public boolean isOpaque() {
        return WidgetStore.STYLE_OPAQUE.equals(style);
    }

    public boolean isGlass() {
        return WidgetStore.STYLE_GLASS.equals(style);
    }

    // 玻璃与不透明都铺整块卡片，只是玻璃半透明；全透明不铺底
    public int rootBackgroundRes() {
        if (isOpaque()) {
            return dark ? R.drawable.widget_bg_dark : R.drawable.widget_bg_light;
        }
        if (isGlass()) {
            return dark ? R.drawable.widget_glass_dark : R.drawable.widget_glass_light;
        }
        return 0;
    }

    // 布局与不透明一致：文字块不再单独铺底，整块卡片由 root 承担

    private static WidgetTheme light(String style) {
        return new WidgetTheme(
            false,
            style,
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#1A1C1F"),
            Color.parseColor("#5F6368"),
            Color.parseColor("#8A8F98"),
            Color.parseColor("#E6E8EB"),
            Color.parseColor("#1B5FAA")
        );
    }

    private static WidgetTheme dark(String style) {
        // 玻璃卡片会透出背后的壁纸，壁纸偏亮时次级文字容易糊，这两档提亮一些
        boolean glass = WidgetStore.STYLE_GLASS.equals(style);
        return new WidgetTheme(
            true,
            style,
            Color.parseColor("#171B20"),
            Color.parseColor("#ECEDEE"),
            Color.parseColor(glass ? "#C6CCD4" : "#A0A6AD"),
            Color.parseColor(glass ? "#9AA4AE" : "#6E757D"),
            Color.parseColor("#262B31"),
            Color.parseColor("#4A9BE8")
        );
    }

    // 全透明卡片：不再需要底色与衬底，只保留深色判断
    public boolean isDark() {
        return dark;
    }

    public int gridLine() {
        return dark ? 0x59FFFFFF : 0x4D000000;
    }

    public int todayTint() {
        return dark ? 0x1AFFFFFF : 0x1A1B5FAA;
    }
}