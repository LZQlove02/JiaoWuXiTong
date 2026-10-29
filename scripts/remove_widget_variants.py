"""收回 9 个样式变体：删除类、资源、字符串与 Manifest 条目。"""
import io
import os
import re

BASE = "android/app/src/main/java/com/lzpu/jwxt/widget"
XML_DIR = "android/app/src/main/res/xml"
LAYOUT_DIR = "android/app/src/main/res/layout"
MANIFEST = "android/app/src/main/AndroidManifest.xml"
STRINGS = "android/app/src/main/res/values/strings.xml"

PREFIXES = ["Next", "Today", "Week"]
STYLES = ["Glass", "Transparent", "Opaque"]
XML_PREFIXES = ["next", "today", "week"]
XML_STYLES = ["glass", "transparent", "opaque"]

removed = []
for prefix in PREFIXES:
    for style in STYLES:
        path = os.path.join(BASE, f"{prefix}{style}WidgetProvider.java")
        if os.path.exists(path):
            os.remove(path)
            removed.append(path)

for prefix in XML_PREFIXES:
    for style in XML_STYLES:
        for path in (
            os.path.join(XML_DIR, f"widget_{prefix}_{style}_info.xml"),
            os.path.join(LAYOUT_DIR, f"widget_{prefix}_preview_{style}.xml"),
        ):
            if os.path.exists(path):
                os.remove(path)
                removed.append(path)

# Manifest：删掉 .widget.XxxGlassWidgetProvider 这类 receiver 块
with io.open(MANIFEST, encoding="utf-8") as handle:
    text = handle.read()

for prefix in PREFIXES:
    for style in STYLES:
        pattern = re.compile(
            r"\n        <!-- 桌面小组件：[^>]*-->\n        <receiver\n            android:name=\"\.widget\."
            + prefix + style + r"WidgetProvider\"[\s\S]*?</receiver>\n"
        )
        text = pattern.sub("\n", text)

with io.open(MANIFEST, "w", encoding="utf-8", newline="") as handle:
    handle.write(text)

# strings：删掉变体名称
with io.open(STRINGS, encoding="utf-8") as handle:
    lines = handle.readlines()
kept = [
    line for line in lines
    if not re.search(r'name="widget_(next|today|week)_(glass|transparent|opaque)(_desc)?"', line)
]
with io.open(STRINGS, "w", encoding="utf-8", newline="") as handle:
    handle.writelines(kept)

print("删除文件:", len(removed))
print("receivers 剩余:", text.count("APPWIDGET_UPDATE"))
print("strings 删掉:", len(lines) - len(kept))