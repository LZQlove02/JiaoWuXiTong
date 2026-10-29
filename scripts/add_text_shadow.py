"""给小组件里的每个 TextView 加一层轻阴影，全透明背景下拉开与壁纸的对比。"""
import io
import re

LAYOUTS = [
    "android/app/src/main/res/layout/widget_next.xml",
    "android/app/src/main/res/layout/widget_next_preview.xml",
    "android/app/src/main/res/layout/widget_today.xml",
    "android/app/src/main/res/layout/widget_today_preview.xml",
    "android/app/src/main/res/layout/widget_week.xml",
    "android/app/src/main/res/layout/widget_week_preview.xml",
]

SHADOW = (
    '            android:shadowColor="#66000000"\n'
    '            android:shadowDx="0"\n'
    '            android:shadowDy="1"\n'
    '            android:shadowRadius="2"\n'
)

for path in LAYOUTS:
    with io.open(path, encoding="utf-8") as handle:
        text = handle.read()
    if "android:shadowColor" in text:
        print(f"{path}: skip (already has shadow)")
        continue
    updated, count = re.subn(r"(?m)^(\s*)<TextView\n", lambda m: m.group(0) + SHADOW, text)
    with io.open(path, "w", encoding="utf-8", newline="") as handle:
        handle.write(updated)
    print(f"{path}: {count} TextView(s)")