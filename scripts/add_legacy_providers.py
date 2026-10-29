"""兼容旧组件实例：v1.1.0 之前的 3 个 receiver 继续存在，按玻璃样式渲染。"""
import io
import os

BASE = "android/app/src/main/java/com/lzpu/jwxt/widget"
XML_DIR = "android/app/src/main/res/xml"
MANIFEST = "android/app/src/main/AndroidManifest.xml"

LEGACY = [
    ("NextClassWidgetProvider", "next", "下一节课", 110, 110, 2, 2, "widget_next"),
    ("TodayWidgetProvider", "today", "今日课程", 250, 110, 4, 2, "widget_today"),
    ("WeekWidgetProvider", "week", "本周课表", 250, 250, 4, 4, "widget_week"),
]

for cls, prefix, label, min_w, min_h, cell_w, cell_h, layout in LEGACY:
    java = f"""package com.lzpu.jwxt.widget;

/**
 * 兼容旧入口：v1.1.0 之前添加的「{label}」实例仍走这里，统一按液态玻璃样式渲染。
 * 新增组件请用 {prefix.capitalize()}GlassWidgetProvider / Transparent / Opaque。
 */
public class {cls} extends BaseWidgetProvider {{
}}
"""
    with io.open(os.path.join(BASE, cls + ".java"), "w", encoding="utf-8", newline="") as handle:
        handle.write(java)

    info = f"""<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_{prefix}_desc"
    android:initialLayout="@layout/{layout}"
    android:minHeight="{min_h}dp"
    android:minWidth="{min_w}dp"
    android:previewLayout="@layout/{layout}_preview_glass"
    android:resizeMode="none"
    android:targetCellHeight="{cell_h}"
    android:targetCellWidth="{cell_w}"
    android:updatePeriodMillis="0"
    android:widgetCategory="home_screen" />
"""
    with io.open(os.path.join(XML_DIR, f"widget_{prefix}_info.xml"), "w", encoding="utf-8", newline="") as handle:
        handle.write(info)

blocks = []
for cls, prefix, label, *_ in LEGACY:
    blocks.append(
        f"""        <!-- 旧入口兼容：{label}（等同液态玻璃） -->
        <receiver
            android:name=".widget.{cls}"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_{prefix}_info" />
        </receiver>
"""
    )

with io.open(MANIFEST, encoding="utf-8") as handle:
    text = handle.read()
anchor = "        <activity\n            android:name=\".SsoLoginActivity\""
text = text.replace(anchor, "\n".join(blocks) + "\n" + anchor, 1)
with io.open(MANIFEST, "w", encoding="utf-8", newline="") as handle:
    handle.write(text)
print("receivers:", text.count("APPWIDGET_UPDATE"))