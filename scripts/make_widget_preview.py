"""生成 4x4 小组件添加面板预览图（与 WidgetBitmapRenderer 保持同一套比例与透明度）"""
from PIL import Image, ImageDraw, ImageFont

W, H = 700, 560
LINE = (0, 0, 0, 64)              # 网格线：25% 黑
TODAY_TINT = (27, 95, 170, 26)    # 今天列：10% 品牌蓝
FONT_PATH = "C:/Windows/Fonts/msyh.ttc"

# 卡片全透明，整张图只有网格线、星期/节次文字与课程块
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

gutter = W * 0.085
header = H * 0.10
cell_w = (W - gutter) / 7
cell_h = (H - header) / 12


def font(size):
    return ImageFont.truetype(FONT_PATH, max(9, int(size)))


# 今天（周四 = 第 4 列）淡高亮
draw.rectangle([gutter + 3 * cell_w, header, gutter + 4 * cell_w, H], fill=TODAY_TINT)

for r in range(1, 12):
    y = header + r * cell_h
    draw.line([(gutter, y), (W, y)], fill=LINE)
for c in range(1, 7):
    x = gutter + c * cell_w
    draw.line([(x, header), (x, H)], fill=LINE)

# 星期表头
f_head = font(header * 0.5)
for i, label in enumerate(["一", "二", "三", "四", "五", "六", "日"]):
    tw = draw.textlength(label, font=f_head)
    color = "#1B5FAA" if i == 3 else "#5F6368"
    draw.text((gutter + (i + 0.5) * cell_w - tw / 2, header * 0.22), label, font=f_head, fill=color)

# 左侧节次刻度
f_num = font(cell_h * 0.46)
for r in range(1, 13):
    text = str(r)
    tw = draw.textlength(text, font=f_num)
    draw.text((gutter * 0.5 - tw / 2, header + (r - 0.5) * cell_h - cell_h * 0.30), text,
              font=f_num, fill="#8A8F98")

# (weekday, start, end, name, color)
blocks = [
    (1, 1, 2, "鸿蒙开发", "#2F855A"),
    (1, 6, 7, "Spring", "#B7791F"),
    (2, 3, 6, "毛泽东思", "#C05621"),
    (2, 6, 7, "Spring", "#B7791F"),
    (3, 3, 5, "形势政策", "#B7791F"),
    (3, 6, 7, "机器学习", "#C0392B"),
    (4, 3, 5, "国家安全", "#1B5FAA"),
    (4, 6, 8, "国产达梦", "#B7791F"),
]

for weekday, start, end, name, color in blocks:
    x0 = gutter + (weekday - 1) * cell_w + 2
    x1 = gutter + weekday * cell_w - 2
    y0 = header + (start - 1) * cell_h + 2
    y1 = header + end * cell_h - 2
    draw.rounded_rectangle([x0, y0, x1, y1], radius=4, fill=color)

    size = min((y1 - y0) * 0.40, (x1 - x0) * 0.40)
    label = name
    while True:
        f_label = font(size)
        if draw.textlength(label, font=f_label) <= (x1 - x0) - 6 or len(label) <= 1:
            break
        label = label[:-1]
    tw = draw.textlength(label, font=f_label)
    th = f_label.getbbox("字")[3]
    draw.text(((x0 + x1) / 2 - tw / 2, (y0 + y1) / 2 - th / 2 - th * 0.12), label,
              font=f_label, fill="#FFFFFF")

out = "android/app/src/main/res/drawable-nodpi/widget_week_preview.png"
img.save(out, "PNG", optimize=True)
print("saved", out, img.size)