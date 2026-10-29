"""把玻璃面板背景加回各段文字容器（布局里先给浅色，运行时按主题替换）。"""
import io
import re

TARGETS = {
    "android/app/src/main/res/layout/widget_next.xml": ["widget_strip_top", "widget_strip_mid", "widget_strip_foot"],
    "android/app/src/main/res/layout/widget_next_preview.xml": [],
    "android/app/src/main/res/layout/widget_today.xml": ["widget_strip_head", "widget_strip_rows", "widget_strip_foot"],
    "android/app/src/main/res/layout/widget_today_preview.xml": [],
    "android/app/src/main/res/layout/widget_week.xml": ["widget_week_strip_head", "widget_week_strip_summary"],
    "android/app/src/main/res/layout/widget_week_preview.xml": [],
}

BG = '        android:background="@drawable/widget_glass_light"\n'

for path, ids in TARGETS.items():
    with io.open(path, encoding="utf-8") as handle:
        text = handle.read()
    if "widget_glass_light" in text:
        print(f"{path}: skip")
        continue
    for view_id in ids:
        pattern = r'(?m)^(\s*)android:id="@\+id/' + re.escape(view_id) + r'"\n'
        if re.search(pattern, text):
            text = re.sub(pattern, lambda m: m.group(0) + m.group(1) + BG.strip() + "\n", text, count=1)
        else:
            print(f"  ! {view_id} 未找到")
    with io.open(path, "w", encoding="utf-8", newline="") as handle:
        handle.write(text)
    print(f"{path}: glass added")