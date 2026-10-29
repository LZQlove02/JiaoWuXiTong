"""生成 9 套组件元数据：info XML + 预览布局 + 字符串。"""
import io
import os

RES = "android/app/src/main/res"
XML_DIR = os.path.join(RES, "xml")
LAYOUT_DIR = os.path.join(RES, "layout")

SIZES = [
    # prefix, 中文名, minWidth, minHeight, cellW, cellH, initialLayout, 预览源文件
    ("next", "下一节课", 110, 110, 2, 2, "widget_next", "widget_next_preview"),
    ("today", "今日课程", 250, 110, 4, 2, "widget_today", "widget_today_preview"),
    ("week", "本周课表", 250, 250, 4, 4, "widget_week", "widget_week_preview"),
]

STYLES = [
    ("glass", "玻璃", "半透明玻璃卡片", "@drawable/widget_glass_light"),
    ("transparent", "透明", "全透明，只有文字", None),
    ("opaque", "不透明", "实色卡片", "@drawable/widget_bg_light"),
]

INFO_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/{prefix}_{style}_desc"
    android:initialLayout="@layout/{initial}"
    android:minHeight="{min_h}dp"
    android:minWidth="{min_w}dp"
    android:previewLayout="@layout/{preview}"
    android:resizeMode="none"
    android:targetCellHeight="{cell_h}"
    android:targetCellWidth="{cell_w}"
    android:updatePeriodMillis="0"
    android:widgetCategory="home_screen" />
"""

for prefix, label, min_w, min_h, cell_w, cell_h, initial, preview_src in SIZES:
    with io.open(os.path.join(LAYOUT_DIR, preview_src + ".xml"), encoding="utf-8") as handle:
        base = handle.read()
    # 去掉旧的根布局背景行（如果有）
    lines = [line for line in base.splitlines(True) if "android:background=\"@drawable/widget" not in line]
    base = "".join(lines)

    for style, style_label, desc, background in STYLES:
        preview_name = f"{preview_src}_{style}"
        if background:
            text = base.replace(
                "    android:orientation=\"vertical\"\n    android:padding=\"10dp\">",
                f"    android:background=\"{background}\"\n    android:orientation=\"vertical\"\n    android:padding=\"10dp\">",
                1,
            )
        else:
            text = base
        with io.open(os.path.join(LAYOUT_DIR, preview_name + ".xml"), "w", encoding="utf-8", newline="") as handle:
            handle.write(text)

        info = INFO_TEMPLATE.format(
            prefix=prefix, style=style, initial=initial, preview=preview_name,
            min_w=min_w, min_h=min_h, cell_w=cell_w, cell_h=cell_h,
        )
        with io.open(os.path.join(XML_DIR, f"widget_{prefix}_{style}_info.xml"), "w", encoding="utf-8", newline="") as handle:
            handle.write(info)

    print(f"{prefix}: 预览生成 {len(STYLES)} 套")

# 字符串
strings = []
for prefix, label, *_ in SIZES:
    for style, zh, desc, _ in STYLES:
        strings.append(f'    <string name="widget_{prefix}_{style}">{label} · {zh}</string>')
        strings.append(f'    <string name="widget_{prefix}_{style}_desc">{label}：{desc}</string>')
print("\n".join(strings))