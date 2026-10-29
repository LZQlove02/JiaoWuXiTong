"""把「样式」文字胶囊换成齿轮图标按钮。"""
import io
import re

LAYOUTS = [
    "android/app/src/main/res/layout/widget_next.xml",
    "android/app/src/main/res/layout/widget_today.xml",
    "android/app/src/main/res/layout/widget_week.xml",
]

for path in LAYOUTS:
    with io.open(path, encoding="utf-8") as handle:
        text = handle.read()
    pattern = re.compile(
        r'<TextView\s+android:id="@\+id/widget_style_chip"[\s\S]*?/>'
    )
    replacement = """<ImageView
                android:id="@+id/widget_style_chip"
                android:layout_width="20dp"
                android:layout_height="20dp"
                android:layout_marginStart="4dp"
                android:background="@drawable/widget_chip_bg"
                android:contentDescription="@string/widget_style_action"
                android:padding="4dp"
                android:src="@drawable/ic_widget_style" />"""
    updated, count = pattern.subn(replacement, text, count=1)
    with io.open(path, "w", encoding="utf-8", newline="") as handle:
        handle.write(updated)
    print(f"{path}: 替换 {count} 处")