"""补样式标签与切换按钮文案。"""
import io

PATH = "android/app/src/main/res/values/strings.xml"
with io.open(PATH, encoding="utf-8") as handle:
    text = handle.read()

if "widget_style_glass" in text:
    print("已存在，跳过")
    raise SystemExit

block = """    <!-- 组件内「样式」按钮 -->
    <string name="widget_style_glass">玻璃</string>
    <string name="widget_style_transparent">透明</string>
    <string name="widget_style_opaque">不透明</string>
    <string name="widget_style_chip">样式 · %1$s</string>
"""
text = text.replace("</resources>", block + "</resources>")
with io.open(PATH, "w", encoding="utf-8", newline="") as handle:
    handle.write(text)
print("已追加样式标签")