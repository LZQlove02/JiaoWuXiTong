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

    private WidgetTheme(boolean dark, int background, int text, int secondary,
                        int tertiary, int border, int accent) {
        this.dark = dark;
        this.background = background;
        this.text = text;
        this.secondary = secondary;
        this.tertiary = tertiary;
        this.border = border;
        this.accent = accent;
    }

    public static WidgetTheme resolve(Context context, String stored) {
        boolean isDark;
        if (LIGHT.equals(stored)) {
            isDark = false;
        } else if (DARK.equals(stored)) {
            isDark = true;
        } else {
            int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
            isDark = mode == Configuration.UI_MODE_NIGHT_YES;
        }
        return isDark ? dark() : light();
    }

    private static WidgetTheme light() {
        return new WidgetTheme(
            false,
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#1A1C1F"),
            Color.parseColor("#5F6368"),
            Color.parseColor("#8A8F98"),
            Color.parseColor("#E6E8EB"),
            Color.parseColor("#1B5FAA")
        );
    }

    private static WidgetTheme dark() {
        return new WidgetTheme(
            true,
            Color.parseColor("#171B20"),
            Color.parseColor("#ECEDEE"),
            Color.parseColor("#A0A6AD"),
            Color.parseColor("#6E757D"),
            Color.parseColor("#262B31"),
            Color.parseColor("#4A9BE8")
        );
    }

    // 卡片本身透明，文字行用 60% 不透明度的衬底保证可读性
    public int stripRes() {
        return dark ? R.drawable.widget_strip_dark : R.drawable.widget_strip_light;
    }

    // 位图里的表头/节次栏衬底：与文字衬底同色（60%），保证观感统一
    public int gridScrim() {
        return dark ? 0x99000000 : 0x99FFFFFF;
    }

    public int gridLine() {
        return dark ? 0x40FFFFFF : 0x33000000;
    }

    public int todayTint() {
        return dark ? 0x1AFFFFFF : 0x1A1B5FAA;
    }
}