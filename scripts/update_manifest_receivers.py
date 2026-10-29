"""把 Manifest 里的 3 个组件 receiver 替换成 9 个（3 尺寸 × 3 样式）。"""
import io

PATH = "android/app/src/main/AndroidManifest.xml"

SIZES = [
    ("Next", "next", "2x2 下一节课"),
    ("Today", "today", "4x2 今日课程"),
    ("Week", "week", "4x4 本周课表"),
]
STYLES = [("Glass", "glass", "液态玻璃"), ("Transparent", "transparent", "全透明"), ("Opaque", "opaque", "不透明")]

blocks = []
for prefix, xml_prefix, size_label in SIZES:
    blocks.append("")
    for style, xml_style, zh in STYLES:
        blocks.append(
            f"""        <!-- 桌面小组件：{size_label} · {zh} -->
        <receiver
            android:name=".widget.{prefix}{style}WidgetProvider"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_{xml_prefix}_{xml_style}_info" />
        </receiver>
"""
        )
new_block = "\n".join(blocks)

with io.open(PATH, encoding="utf-8") as handle:
    text = handle.read()

start = text.index("        <!-- 桌面小组件：2x2 下一节课 -->")
end = text.index("        <activity\n            android:name=\".SsoLoginActivity\"")
text = text[:start] + new_block + "\n" + text[end:]

with io.open(PATH, "w", encoding="utf-8", newline="") as handle:
    handle.write(text)
print("receivers:", text.count("appwidget.action.APPWIDGET_UPDATE"))