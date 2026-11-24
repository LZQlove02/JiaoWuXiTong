# 更新检查与意见反馈 — 设计文档

- 日期：2026-10-30
- 版本目标：v1.3.0 / versionCode 23
- 状态：已实现并真机验证（2026-10-30，realme RMX2121 / Android 12，见 `../../reports/真机验证报告-20261027.md` §35）
- 关联：`../../plan/方案.md` §17、`../../发布流程.md`

## 0. 摘要

源码仓库保持私有，新增一个**公开分发仓库** `LZQlove02/lzpu-jwxt-releases` 只存放签名 APK 与更新说明。App 通过该仓库的 `releases/latest` 匿名接口实现每日一次的更新检查；同时新增意见反馈入口，用系统邮件 App 把用户描述 + 脱敏诊断信息发送到固定收件人。全部能力不新增权限、不上传账号数据、不在 App 内下载安装包。

> 2026-11-24 变更（v2.2.4）：源码仓库已公开，更新检查与发布合并到 `LZQlove02/JiaoWuXiTong`；§3 与 §7 已按新仓库更新，`lzpu-jwxt-releases` 保留为过渡期仓库，见 §11。

## 1. 背景与目标

1. 让已安装旧版本的同学能收到新版本提示，而不是靠群里发文件。
2. 给使用者一个可用的反馈通道，且反馈内容能定位问题（版本、机型、系统）。
3. 不改变「源码私有」的现状，不引入后端与第三方统计 SDK。

## 2. 范围与非目标

**范围内**

- 自动检查（登录后、回前台、24h 节流）与手动检查（关于页）
- 更新弹窗、打开发布页、`promptedTag` 记忆
- 关于页、反馈页、登录页与设置页入口
- 原生 `SupportBridgePlugin`、`HttpPolicy.GITHUB`、共享 `HttpExecutor`、`UpdateInfo`
- JS / Android 单测与文档、公开发布仓库、发布流程

**非目标**

- App 内下载、校验、安装 APK；增量更新
- 崩溃自动上报、埋点、远程配置
- 后台常驻轮询或推送
- 应用商店上架

## 3. 发布渠道

| 仓库 | 可见性 | 内容 | 用途 |
|---|---|---|---|
| `LZQlove02/JiaoWuXiTong` | 公开（v2.2.4 起） | 源码、设计文档、签名 APK、Release 说明 | 开发、分发与更新检查 |
| `LZQlove02/lzpu-jwxt-releases` | 公开（历史，过渡期） | 签名 APK、Release 说明 | v2.2.3 及更早版本的更新检查 |

- 客户端只读 `https://api.github.com/repos/LZQlove02/JiaoWuXiTong/releases/latest`；v2.2.3 及更早版本读的是 `.../lzpu-jwxt-releases/releases/latest`
- 该接口不返回 draft / prerelease；测试版本要能被检测到，必须按正式 Release 发布
- 仓库尚无 Release 时返回 404，按「无更新」处理
- 匿名调用有速率限制（约 60 次/小时/IP），24h 节流远低于该上限

## 4. 交互流程

```
已同意免责声明且已登录
  ├─ 启动 / 回前台 → stores/update.autoCheck()
  │    ├─ 距上次检查 < 24h → 跳过
  │    └─ checkLatest()
  │         ├─ 无 Release / 未找到 → 静默
  │         ├─ 网络 / 解析失败 → 静默（不打扰）
  │         └─ tag 比当前版本新 → 未提示过则弹 UpdateDialog
  │              ├─ 去更新 → 系统浏览器打开发布页 → 关闭弹窗并记 promptedTag
  │              └─ 以后再说 → 记 promptedTag，不再自动弹
  └─ 设置页「检查更新」→ checkNow()（跳过节流与 promptedTag，失败有提示；v1.3.2 起由关于页移入）
```

反馈：

```
登录页 / 设置 → 意见反馈 → 填写描述（可选联系方式）
  → 附诊断信息（默认开）→ 发送邮件 → ACTION_SENDTO 拉起邮件 App
  → 无邮件 App：提示可复制内容并给出固定收件人
```

## 5. 技术设计

### 5.1 原生

| 文件 | 作用 |
|---|---|
| `SupportBridgePlugin.java` | 五个 `@PluginMethod`；独立无 Cookie OkHttp client（5s/8s/10s） |
| `UpdateInfo.java` | 纯 Java 解析 tag / name / notes / url / publishedAt；缺字段或非法 JSON 抛错 |
| `HttpExecutor.java` | 与 `JwxtHttpPlugin` 共用的逐跳重定向实现（301/302/303 转 GET，307/308 保留） |
| `HttpPolicy.java` | 新增 `GITHUB`（仅 `api.github.com`、HTTPS、443、拒绝 URL 内凭据） |
| `MainActivity.java` | 注册 `SupportBridgePlugin` |
| `AndroidManifest.xml` | `<queries>`：`SENDTO mailto`、`VIEW https`（Android 11+ 包可见性） |

返回值约定：

- `checkLatest()`：`{found:true, tag, name, notes, url, publishedAt}`；无 Release 时 `{found:false, reason:'no-release'}`；网络 / 解析失败 `reject`
- `openReleasePage({tag})`：`TAG_INVALID` / `NO_BROWSER`
- `composeFeedback({subject, body})`：`BODY_EMPTY` / `SUBJECT_TOO_LONG` / `BODY_TOO_LONG` / `NO_EMAIL_APP`
- `getDeviceInfo()`：`manufacturer` / `model` / `android` / `sdk`
- `copyText({text})`：`TEXT_EMPTY` / `TEXT_TOO_LONG` / `CLIPBOARD_UNAVAILABLE`

### 5.2 前端

| 文件 | 作用 |
|---|---|
| `src/native/supportBridge.js` | Capacitor 封装；Web 预览用 `fetch` / `window.open` / `mailto:` 兜底 |
| `src/utils/update.js` | `parseVersion` / `compareVersions` / `isNewer` / `shouldAutoCheck` / `formatReleaseNotes` |
| `src/utils/diagnostics.js` | 拼诊断文本（版本、设备、系统、主题、平台、在线状态、生成时间） |
| `src/utils/feedback.js` | 主题 / 正文拼装与长度限制、`mailto` 编码 |
| `src/stores/update.js` | 状态、24h 节流、`promptedTag`、失败静默、手动检查 |
| `src/components/UpdateDialog.vue` | 更新弹窗（返回键关闭，`ui.setOverlayCloser`） |
| `src/views/About.vue` | 仅展示版本与非官方说明（v1.3.2 起不再承载检查更新） |
| `src/views/Feedback.vue` | 反馈表单、诊断预览、发送 / 复制 |
| `src/App.vue` | 登录态建立与回前台触发自动检查；渲染弹窗 |
| `src/views/Settings.vue` / `Login.vue` | 设置页的检查更新 / 关于 / 反馈入口，登录页的反馈链接 |
| `src/router/index.js` | `/about`、`/feedback`（`meta.guest`，登录前后都可进） |

本地键：`lzpu.update.lastCheckAt`、`lzpu.update.promptedTag`。退出登录、清除缓存不清除，避免每次登录重复弹窗。

### 5.3 版本来源

- 当前版本统一通过 `@capacitor/app` 的 `getInfo().version` 读取（即原生 `versionName`），不在前端硬编码
- 最新版本取 Release 的 `tag_name`（允许 `v` 前缀），按四段数字比较；后缀（`-beta.1`）不参与比较
- 任一版本号非法时判定「无法比较」，不弹更新

## 6. 边界与异常

| 场景 | 行为 |
|---|---|
| GitHub 不可达 / 超时 | 自动检查静默；手动检查提示「检查更新失败」 |
| 发布仓库无 Release（404） | `found:false`，关于页提示「暂未发布版本」，不算失败 |
| 速率限制 / 非 2xx | `HTTP_<code>`，自动静默、手动提示 |
| JSON 缺字段或非法 | `PARSE_FAILED`，不猜测版本 |
| tag 非法或超长 | `TAG_INVALID`，不拼接 URL |
| 无浏览器 / 无邮件 App | `NO_BROWSER` / `NO_EMAIL_APP`，提示复制兜底 |
| 正文超 6000 字 | JS 先截断；原生仍校验并拒绝超限 |
| 未同意免责声明 | 不自动检查（store 内 `isDisclaimerAccepted()` 门控） |
| 未登录 | 不自动检查；登录页仍可使用反馈 |

## 7. 安全与隐私

- 仅向 `api.github.com` 匿名 GET，不带 Cookie、学号、姓名、查询参数；不发 `Authorization`
- 使用独立 OkHttp client（无 CookieJar），与教务会话完全隔离
- 只允许 `https://github.com/LZQlove02/JiaoWuXiTong/releases/tag/<tag>`（v2.2.3 及更早为 `lzpu-jwxt-releases`）；tag 先过正则
- 邮件收件人写死；不接受调用方传入地址
- 不新增权限；不下载、不安装 APK
- 诊断信息不含账号与业务数据；反馈正文由用户自行确认后发送
- 不记录响应正文到日志；debug 日志只记录状态码与主机

## 8. 测试

| 层 | 内容 | 数量 |
|---|---|---|
| JS | 版本解析与比较（含 `v` 前缀、`1.10.0`、非法值）、24h 节流、说明清洗与截断、诊断文本（含/不含设备）、主题长度与编码、空描述 | 119 → **130** |
| Android | `UpdateInfo` 解析（完整 / 缺字段 / 非法 JSON / 非 GitHub URL）、`HttpPolicy.GITHUB` 允许与拒绝、策略隔离 | 48 → **62** |

真机清单（v1.3.1 测试 Release → 安装 v1.3.0）：

1. 冷启动自动弹一次；「以后再说」后不再自动弹
2. 关于页手动检查可再次弹窗；「去更新」打开系统浏览器
3. 断网时手动检查提示失败；自动检查不打扰
4. 登录页反馈入口可进入；邮件正文含诊断且无个人信息
5. 无邮件 App（或用模拟器）时提示复制兜底
6. 登录 / 课表 / 成绩 / 通知 / 小组件回归正常
7. 覆盖安装保留登录态与 `promptedTag`

## 9. 发版与文档同步

- `android/app/build.gradle`：`versionCode 23`、`versionName "1.3.0"`；`package.json` 同步 1.3.0
- 免责声明升到 v2（允许校内同学免费安装）；`LICENSE` 改为允许原样分发 APK
- 发布步骤见 `../../发布流程.md`
- 同步更新 `方案.md` §5.4 / §17 / 合规说明 / 验收标准，`README` 与 `CHANGELOG`

## 10. 已知限制

- GitHub 在部分网络环境不可达，自动检查会静默失败；用户仍可手动打开发布页
- 无法在 App 内校验 APK 完整性或自动安装，需系统浏览器下载后手动安装
- 公开发布仓库匿名可读，APK 可被任意下载；LICENSE 仅约束使用行为，无法技术限制

## 11. 迁移记录（2026-11-24，v2.2.4）

- 源码仓库 `LZQlove02/JiaoWuXiTong` 转为公开，作为唯一仓库：源码、设计文档与 Releases 同仓
- 客户端更新检查地址改为 `https://api.github.com/repos/LZQlove02/JiaoWuXiTong/releases/latest`，「去更新」发布页基址同步改为 `https://github.com/LZQlove02/JiaoWuXiTong/releases/tag/`；`HttpPolicy.GITHUB` 白名单仍只允许 `api.github.com` 与 `github.com`，路径校验不变
- 过渡：v2.2.4 同时发布到旧仓库 `LZQlove02/lzpu-jwxt-releases`，v2.2.3 及更早版本升级到 2.2.4 后自动指向新仓库；确认升级到位后删除旧仓库
- 影响：未升级的旧版本在旧仓库删除后无法自动检查更新，需手动安装一次 2.2.4 或更新版本
