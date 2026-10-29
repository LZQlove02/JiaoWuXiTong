"""把三个 Provider 改造成纯渲染器（Binder）：去掉 AppWidgetProvider 继承与回调。"""
import io
import os
import re

BASE = "android/app/src/main/java/com/lzpu/jwxt/widget"

PAIRS = [
    ("NextClassWidgetProvider", "NextClassWidgetBinder"),
    ("TodayWidgetProvider", "TodayWidgetBinder"),
    ("WeekWidgetProvider", "WeekWidgetBinder"),
]

for old, new in PAIRS:
    src = os.path.join(BASE, old + ".java")
    dst = os.path.join(BASE, new + ".java")
    with io.open(src, encoding="utf-8") as handle:
        text = handle.read()

    text = text.replace(
        "public class %s extends AppWidgetProvider {" % old,
        "final class %s {" % new,
    )
    text = re.sub(
        r"\n    @Override\n    public void onUpdate\([^)]*\) \{\n        WidgetUpdater\.refreshAll\(context\);\n    \}\n",
        "\n",
        text,
    )
    text = re.sub(
        r"\n    @Override\n    public void onEnabled\([^)]*\) \{\n        WidgetUpdater\.refreshAll\(context\);\n    \}\n",
        "\n",
        text,
    )
    text = text.replace("import android.appwidget.AppWidgetProvider;\n", "")

    with io.open(dst, "w", encoding="utf-8", newline="") as handle:
        handle.write(text)
    os.remove(src)
    print(f"{old} -> {new}, onUpdate left={text.count('onUpdate')}")