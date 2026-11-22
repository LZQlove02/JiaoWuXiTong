# 4×4 小组件：本周/下周切换与更新时间文案优化 设计

日期：2026-11-22

## 背景

4×4「本周课表」现在只画本周：`payload.week[]` 是同步那一刻那一周的课，原生跨周就降级成「打开 App 同步」。周日下午想看一眼下周有什么课，只能打开 App 切周次再回桌面。

标题行右侧的「更新于 19:58」只有时分，超过一天就分不清是哪天的数据；用户希望它更接近人话。

## 目标与非目标

目标：

1. 4×4 能在组件内直接看下周，且不破坏「只显示课表」的硬约束（§7.3：课名与教室必须同时出现、不显示未阅/通知/GPA）。
2. 「更新于」改成相对时间文案，三个尺寸口径一致。

非目标：

- 不做后台联网轮询（§7.3 第 22 条「不做后台轮询」保持不变）；组件仍然只读 App 写入的快照。
- 不做「下周 N 门」摘要、不做双周同屏。
- 不改 2×2 / 4×2 的「下一节」推导与周次逻辑。
- 不改样式档位（玻璃/透明/不透明）、文字色开关与点击进 App 的深链。

## 设计

### 1. 数据契约（JS 侧 `src/widget/payload.js`）

`buildWidgetPayload` 的返回值新增两个字段，老版本组件读到新 payload 会忽略它们，新组件读到老 payload 也走兼容分支：

```json
{
  "term": { "label": "2026-2027 学年第一学期", "week": 12 },
  "weekAnchor": "2026-11-16",
  "nextWeek": {
    "week": 13,
    "items": [ { "weekday": 1, "start": 1, "end": 2, "startTime": "08:20", "endTime": "09:55", "name": "…", "room": "T2-A507" } ]
  },
  "updatedAtDate": "2026-11-22",
  "updatedAtLabel": "19:58"
}
```

- `nextWeek.week = term.week + 1`；`items` 用现成的 `sessionsInWeek(sessions, week + 1)` 过滤、`classItem()` 映射，排序与 `week[]` 一致（weekday、start）。
- 下周没有课（学期末、课表只到本周、`week + 1 > 20`）时 `items: []`，`week` 仍给出。
- `updatedAtDate` 是 `updatedAt` 的本地日期（`YYYY-MM-DD`），与 `updatedAtLabel`（`HH:mm`）同源，都取 `schedule.fetchedAt`。

### 2. 原生模型与渲染

- `WidgetPayload` 新增 `int nextWeekNo`（0 表示没有下周数据）与 `List<WeekItem> nextWeekItems`；`parse()` 读 `nextWeek.week` 与 `nextWeek.items`。字段缺失时保持 0 / 空表，组件行为与现在完全一致。
- `WidgetBitmapRenderer.render(...)` 的入参从「读 `payload.week`」改为显式传 `List<WidgetPayload.WeekItem> items`，其余参数不变；`todayWeekday` 传 0 时不画今天列底色与蓝点。签名改为：

```java
render(WidgetTheme theme, List<WidgetPayload.WeekItem> items,
       int widthPx, int heightPx, int todayWeekday, String emptyText)
```

- 新增纯逻辑类 `WidgetWeekDisplay`（无 Android 依赖，便于 JUnit）：

```java
static List<WidgetPayload.WeekItem> items(WidgetPayload payload, int offset)   // 0=本周，1=下周
static int todayWeekday(int offset, int realToday)                             // 1..7；offset=1 时返回 0
static int titleRes(int offset)                                                // 本周/下周标题资源
static int emptyRes(int offset)                                                // 本周暂无课程 / 下周暂无课程
static int weekNo(WidgetPayload payload, int offset)                           // 本周取 termWeek；下周取 nextWeekNo，0 表示未知
```

- `WeekWidgetBinder` 只负责组合：标题文案、按钮文案与点击、网格位图、空态。

### 3. 交互与状态

- 标题行在「有变」角标之后、齿轮之前插入一个 `10sp` 文本按钮 `R.id.widget_week_toggle`：本周时文案 `下周 ›`，下周时 `本周 ‹`；`minHeight=24dp`、`gravity=center_vertical`、`paddingStart=6dp / paddingEnd=2dp`，与齿轮同一水平线，保证点击区不小于 24dp。
- 每个实例的显示周次存在 `WidgetStore`，键 `widget_week_offset_<appWidgetId>`，值 `0`/`1`，默认 `0`；`clearInstanceSettings()` 一并清理。
- 点击 → `WidgetDeepLink.switchWeek(context, appWidgetId)` 发广播（`ACTION_SWITCH_WEEK = "com.lzqlove.jiaowu.widget.SWITCH_WEEK"`，复用 `EXTRA_APP_WIDGET_ID`，requestCode `2000 + appWidgetId`）→ 新增 `WidgetWeekSwitchReceiver` 翻转 offset、写回、`WidgetUpdater.refreshAll()`。
- 标题：`本周课表 · 第 %d 周` / `下周课表 · 第 %d 周`（对应新增字符串 `widget_title_next_week_no`，无周次时用 `widget_title_next_week`）。
- 网格点击仍然整块打开 App 课表（`/schedule`，`REQUEST_WEEK_SCHEDULE`），与现在一致。
- 空态：切到下周但没有数据时，网格区域居中显示「下周暂无课程」（`widget_empty_next_week`），按钮仍可切回本周。

### 4. 更新时间文案

新增纯逻辑类 `WidgetUpdatedLabel`：`parse(String date, String clock, Calendar now)` 返回 `{ kind, minutes, month, day, year, clock }`，`text(Context, label)` 负责取字符串。

| 距今 | 文案 | 字符串 |
|---|---|---|
| < 1 分钟 | 刚刚 | `widget_time_just_now` |
| < 60 分钟 | N 分钟前 | `widget_time_minutes_ago` |
| 同一天 | 今天 19:58 | `widget_time_today` |
| 前一天 | 昨天 09:20 | `widget_time_yesterday` |
| 同年更早 | 11月20日 09:20 | `widget_time_date` |
| 更早（跨年） | 2025年11月20日 09:20 | `widget_time_date_year` |
| 解析失败 / 缺 `updatedAtDate` | 19:58（现行为） | `widget_updated_at` 原样拼接 |

三个尺寸共用同一个格式化方法；2×2 的 `widget_updated` 已有 `maxLines=1 + ellipsize=end`，放不下时按原有规则截断。

刷新节拍：新增 `WidgetTimeTickReceiver`（requestCode `1002`，单次闹钟链，目标时刻固定为「下一个整点 + 2 分钟」），到点只调用 `WidgetUpdater.refreshAll()` 重画文案，**不联网、不写 payload**，然后排下一次。排程挂在 `BaseWidgetProvider.onUpdate / onEnabled` 与开机广播上；`onDisabled` 是**按 provider** 触发的，所以取消前要先确认三档组件都没有实例了（`WidgetUpdater.hasAnyWidget()`），否则删掉一个尺寸会把另外两档的闹钟一起掐掉；桌面一个实例都没有时也不排新闹钟。Doze 下闹钟可能被推迟，最坏情况下「12 分钟前」会晚一小时才变成「今天 19:58」——这是省电与准确度之间的取舍，不提高频率。

### 5. 边界与降级

- **跨周**：`WidgetScheduleDeriver.isStale()` 命中时，在 `WidgetUpdater.refreshAll()` 入口（与现有 stale 降级同一处）把该实例的 offset 归一化为 0 并写回，`WeekWidgetBinder` 只按传入的 offset 渲染；组件继续显示「打开 App 同步」，绝不把上一周数据当本周画，下周数据同样不可信。
- **未登录 / 课表未同步**：隐藏切换按钮，保持现有占位文案。
- **下周无课**：见空态；按钮仍可用。
- **老 payload**（没有 `nextWeek`/`updatedAtDate`）：下周按钮行为退化为「切过去显示下周暂无课程」，时间文案退回 `HH:mm`。
- **时钟回拨/未来时间**：`updatedAt` 晚于当前时间时按「刚刚」处理并保持单调，不显示负数分钟。
- **多实例**：offset 按 appWidgetId 独立，互不影响；旋转/改尺寸只重算位图，不改 offset。

### 6. 测试

- JS：`tests/payload.test.js` 增加 `nextWeek` 用例——本周 12 / 下周 13 的 sessions 分别落位、学期末得到空 `items`、`updatedAtDate` 与 `updatedAtLabel` 同源。
- Java（JUnit，纯逻辑）：
  - `WidgetUpdatedLabelTest`：0s / 59s / 60s / 59min / 60min / 当天早间 / 昨天 / 3 天前 / 去年 / 时钟回拨 / 空 date / 空 clock。
  - `WidgetWeekDisplayTest`：offset 0/1 的 items、todayWeekday、weekNo、titleRes；`nextWeekNo=0` 时退回标题。
- 真机（小米 13，`bba41987`）：点两次切换看标题与网格；改系统日期到周日 23:59 验跨周降级；`adb shell am broadcast` 手动触发 tick 看文案变化；三档样式各看一次截图。

### 7. 文档

- `docs/plan/小组件执行方案.md` §7.3 增加「两周切换」与「更新时间格式」两条，并在 §7.1/§7.2 的「更新于」行同步新口径。
- `CHANGELOG.md` 当前版本段（2.2.2）的「### 新增」增记本次新增与变更。
- 不新增 4×4 视觉预览稿（样式档位未变）；实现时顺手把既有 `docs/previews/widget-4x4-glass-preview.html` 的标题行与「更新于」文案同步成新版（只改文案与按钮，不加样式档位）。

## 验收

1. 桌面 4×4 默认显示本周（当前第 12 周），标题右侧出现「下周 ›」。
2. 点击后标题变「下周课表 · 第 13 周」，网格换成下周课程，周六/周日列不再有今天的高亮与蓝点，按钮变「本周 ‹」。
3. 再点一次回到本周，今天列高亮恢复。
4. 下周没有课时，空白区居中显示「下周暂无课程」，按钮仍可切回本周。
5. 跨周后（周一 00:05 闹钟触发或 App 再次同步前）组件强制回到本周并显示「打开 App 同步」，不出现上周课表冒充本周。
6. 未登录或课表未同步时，切换按钮不出现，占位文案与现在一致。
7. 更新时间按第 4 节的表显示；缺 `updatedAtDate` 的老数据仍显示 `HH:mm`。
8. 整点 +2 分钟触发一次文案刷新（`dumpsys alarm` 可见下一条），移除最后一个组件后该闹钟被取消。
9. 2×2、4×2 也使用新文案，宽度不足时按原有 `ellipsize=end` 截断，不换行、不撑破布局。
10. 三个尺寸的样式切换、文字色开关、点击进 App 与本次改动前一致。
