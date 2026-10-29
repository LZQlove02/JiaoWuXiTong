"""生成 3 尺寸 × 3 样式的 9 个 Provider 类。"""
import io
import os

BASE = "android/app/src/main/java/com/lzpu/jwxt/widget"

VARIANTS = [
    ("Next", "下一节课", "2x2"),
    ("Today", "今日课程", "4x2"),
    ("Week", "本周课表", "4x4"),
]

STYLES = [
    ("Glass", "液态玻璃"),
    ("Transparent", "全透明"),
    ("Opaque", "不透明"),
]

for prefix, label, size in VARIANTS:
    for style, style_label in STYLES:
        name = f"{prefix}{style}WidgetProvider"
        path = os.path.join(BASE, name + ".java")
        if os.path.exists(path):
            print(f"{name} 已存在，跳过")
            continue
        body = f"""package com.lzpu.jwxt.widget;

/**
 * {size}「{label}」· {style_label}。
 * 样式由类名决定，渲染逻辑见 {prefix}WidgetBinder。
 */
public class {name} extends BaseWidgetProvider {{
}}
"""
        with io.open(path, "w", encoding="utf-8", newline="") as handle:
            handle.write(body)
        print(f"生成 {name}")