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

    // 玻璃与全透明两档的底不可控（壁纸透出多少不知道），
    // 这两档的文字必须压到主文字级别，否则中灰落在壁纸上是 1.2:1（真机实测）。
    // 不透明档有实色卡片兜底，保留原来的中灰层级。
    private static boolean needsDeepText(String style) {
        return !WidgetStore.STYLE_OPAQUE.equals(style);
    }

    private static WidgetTheme light(String style) {
        boolean deep = needsDeepText(style);
        return new WidgetTheme(
            false,
            style,
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#1A1C1F"),
            Color.parseColor(deep ? "#1A1C1F" : "#5F6368"),
            Color.parseColor(deep ? "#23272D" : "#8A8F98"),
            Color.parseColor("#E6E8EB"),
            Color.parseColor("#1B5FAA")
        );
    }

    private static WidgetTheme dark(String style) {
        boolean deep = needsDeepText(style);
        return new WidgetTheme(
            true,
            style,
            Color.parseColor("#171B20"),
            Color.parseColor("#ECEDEE"),
            Color.parseColor(deep ? "#ECEDEE" : "#A0A6AD"),
            Color.parseColor(deep ? "#C6CCD4" : "#6E757D"),
            Color.parseColor("#262B31"),
            Color.parseColor("#4A9BE8")
        );
    }

    // 全透明卡片：不再需要底色与衬底，只保留深色判断
    public boolean isDark() {
        return dark;
    }

    public int gridLine() {
        // 玻璃档的网格退到 20%，否则和卡片渐变叠在一起显碎
        if (isGlass()) {
            return dark ? 0x33FFFFFF : 0x2E000000;
        }
        return dark ? 0x59FFFFFF : 0x4D000000;
    }

    // 今天列的底色：原 10% 落在壁纸上几乎看不出，提到 14%
    public int todayTint() {
        return dark ? 0x24FFFFFF : 0x241B5FAA;
    }

    // 今天的表头与圆点：透明 / 玻璃档的底是壁纸，主题蓝直接压上去实测 3.6:1，
    // 这两档单独压深一档到 #14508F；不透明档有实色卡片兜底，沿用主题蓝。
    public int todayAccent() {
        if (dark) return 0xFF4A9BE8;
        return isOpaque() ? 0xFF1B5FAA : 0xFF14508F;
    }

    // 课程块内描边：玻璃档给每块加 1px 内描边，相邻课之间才分得开
    public int blockEdge() {
        return dark ? 0x33FFFFFF : 0x38FFFFFF;
    }
}