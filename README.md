# 柳职教务（Android 第三方客户端）

柳州职业技术大学教务系统的个人只读客户端。前端使用 Vue 3 + Vite，Android 壳使用 Capacitor 6，数据直连学校统一认证后的教务系统，不依赖自建服务器。

> 非官方应用，未登录不可进入业务页面；仅支持学校统一认证登录，不保存账号密码。
> 当前版本：v1.3.5 / versionCode 28；minSdk 23（Android 6.0），targetSdk 35。

## 功能

- 统一认证登录、冷启动会话恢复（Cookie 使用 Android Keystore AES-GCM 加密）
- 课表、成绩（单学期 / 全部学期）、评教记录、选课状态、教务通知、消息、校历、办事指南
- 课表变更提醒：停课 / 调课 / 新增 / 换教室，前台对账 + 每天两次后台检查 + 系统通知
- 桌面小组件：今日课程、下一节课、本周课表
- 首页下拉刷新：学期、课表、通知与消息，带下拉 / 松开 / 刷新中 / 完成 / 失败 / 离线状态提示
- 课表周次快速跳转，文字大小跟随系统无障碍设置
- 成绩提醒：仅在新出现不合格成绩时通知；检查时间可配置（默认 07:10 / 12:50）
- 通知附件与正文图片、办事指南图片与附件均可在 App 内查看/打开
- 消息支持未阅/全部只读；可选开启新通知、新消息摘要提醒
- 设置页显示缓存占用、管理桌面组件样式，支持课表/成绩/通知快捷方式
- 更新检查（公开发布仓库，每天最多一次，可手动重查）与意见反馈（系统邮件 + 脱敏诊断信息）
- 深浅主题、底部导航固定布局与系统导航栏同步配色、平板限宽、离线缓存与过期提示

## 目录

```
src/                 Vue 前端（views / stores / api / components / utils）
android/             Android 壳、原生插件、课表变更引擎与桌面小组件
tests/               Vitest 单测与共享 fixture
docs/                方案、验证报告与设计规格（索引见 docs/README.md）
```

## 开发

```bash
npm ci          # 干净环境安装依赖（不要用 npm install 更新锁文件）
npm run dev     # Vite 开发服务器
npm test        # Vitest 单测
npm run lint    # ESLint
npm run typecheck  # 工具层 JavaScript 类型检查（tsc checkJs）
npm run build   # 构建 dist/
npm run sync    # 把 dist/ 同步到 Android 工程
```

## Android 构建

1. 安装 JDK 17 与 Android SDK 35。
2. 在 `android/keystore.properties` 配置正式签名（该文件已被 gitignore）。
3. 构建：

```bash
npm run build
npm run sync
cd android
./gradlew assembleDebug          # debug 包
./gradlew assembleRelease        # 正式包；缺少签名配置会直接失败
./gradlew testDebugUnitTest      # Android 单测
```

release 已开启 R8 混淆与资源裁剪；Capacitor 插件通过反射调用，`proguard-rules.pro` 已保留相关注解成员。

## 关键约束

- 网络请求强制 HTTPS，公开内容与教务认证使用独立主机白名单，重定向逐跳校验。
- 统一认证 WebView 登录完成后立即销毁；统一认证证书使用内置 SHA-256 指纹固定（同时配置 network_security_config 的 SPKI pin），指纹不一致直接阻止登录。证书轮换时需同步更新两处指纹并重新发版。
- 登录 Cookie 只回传 `zfjw.lzzy.net` 域；不保存密码，不向 WebView 暴露 JS 接口。
- 个人数据缓存写入应用私有 localStorage，退出登录或会话失效时统一清空；锁屏通知只展示脱敏文案。
- 更新检查只向 `api.github.com` 发匿名 GET（不带 Cookie、学号或查询参数），外链与反馈收件人写死；App 内不下载、不安装 APK。
- 缓存过期规则：通知 / 办事指南列表 6 小时，详情 7 天；成绩、消息、评教、选课状态有独立 TTL，断网时保留缓存并标注更新时间与「已过期」。

## CI

`.github/workflows/ci.yml` 在 push / PR 时运行前端 lint、类型检查、单测、构建，以及 Android `testDebugUnitTest`。

## 贡献与许可

- 贡献流程与编码约定见 [CONTRIBUTING.md](CONTRIBUTING.md)，安全问题请按 [SECURITY.md](SECURITY.md) 私下上报。
- 版本变更见 [CHANGELOG.md](CHANGELOG.md)，文档索引见 [docs/README.md](docs/README.md)。
- 发版步骤见 [docs/发布流程.md](docs/发布流程.md)，APK 发布在公开仓库 [lzpu-jwxt-releases](https://github.com/LZQlove02/lzpu-jwxt-releases/releases)。
- 本项目保留所有权利，详见 [LICENSE](LICENSE)。
