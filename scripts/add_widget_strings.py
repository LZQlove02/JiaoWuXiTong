"""把 9 套组件的名称与描述追加到 strings.xml。"""
import io

PATH = "android/app/src/main/res/values/strings.xml"

SIZES = [("next", "下一节课"), ("today", "今日课程"), ("week", "本周课表")]
STYLES = [("glass", "玻璃", "半透明玻璃卡片"),
          ("transparent", "透明", "全透明，只有文字"),
          ("opaque", "不透明", "实色卡片")]

with io.open(PATH, encoding="utf-8") as handle:
    text = handle.read()

if "widget_next_glass" in text:
    print("strings 已存在，跳过")
    raise SystemExit

lines = ["", "    <!-- 桌面小组件：3 尺寸 × 3 样式 -->"]
for prefix, label in SIZES:
    for style, zh, desc in STYLES:
        lines.append(f'    <string name="widget_{prefix}_{style}">{label} · {zh}</string>')
        lines.append(f'    <string name="widget_{prefix}_{style}_desc">{label}：{desc}</string>')

text = text.replace("</resources>", "\n".join(lines) + "\n</resources>")
with io.open(PATH, "w", encoding="utf-8", newline="") as handle:
    handle.write(text)
print("已追加", len(SIZES) * len(STYLES) * 2, "条字符串")