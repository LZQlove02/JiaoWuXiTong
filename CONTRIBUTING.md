# 贡献指南

本项目为个人非商业项目，提交前请确认改动符合以下约定。

## 环境要求

- Node.js 20 及以上
- JDK 17
- Android SDK 35（`compileSdk` / `targetSdk`）
- Android 构建使用仓库自带 Gradle Wrapper，不要修改 Wrapper 版本

## 本地流程

```bash
npm ci                 # 干净环境安装依赖，不要用 npm install 改锁文件
npm run dev            # Vite 开发服务器
npm run lint           # ESLint
npm run typecheck      # 工具层 JS 类型检查（tsc checkJs）
npm test               # Vitest 单测
npm run build          # 构建 dist/
npm run sync           # 同步到 Android 工程

cd android
./gradlew testDebugUnitTest     # Android 单测
./gradlew assembleDebug         # debug 包
./gradlew assembleRelease       # 正式包；缺少 keystore.properties 会直接失败
```

提交前至少保证 `lint`、`typecheck`、`npm test`、`testDebugUnitTest` 全部通过。

## 分支与提交

- `master` 为发布分支，新工作从 `master` 拉出功能分支，PR 合回 `master`。
- 提交信息使用 Conventional Commits，中文描述，类型与历史保持一致：

```
feat: 新增 xx 功能
fix: 修复 xx 问题
style: 调整 xx 样式
refactor: 重构 xx
test: 补充 xx 单测
docs: 更新 xx 文档
chore: 构建、依赖或配置调整
```

- 一次提交只做一件事；纯格式化与逻辑改动分开提交。

## PR 要求

1. 说明改动目的与影响范围，关联相关 Issue。
2. 列出验证方式：执行的命令、真机机型与 Android 版本、验证过的页面。
3. 通过 `.github/workflows/ci.yml` 的全部检查。
4. 涉及功能设计时，先补 `docs/superpowers/specs/` 规格，再同步 `docs/plan/方案.md` 对应章节。

## 隐私与安全红线

以下内容**任何情况下都不得**进入仓库、提交历史、Issue 或 PR：

- `android/keystore.properties`、`*.jks`、`*.keystore` 等签名材料与密码
- 真实学号、姓名、成绩、课表、Cookie、会话票据、登录截图
- 未脱敏的教务系统响应体或抓包内容
- 含个人信息的截图、日志与设备序列号

示例数据必须使用虚构姓名，学号如需保留形态请自行脱敏；截图请打码或使用演示数据。

安全问题请按 [SECURITY.md](SECURITY.md) 私下上报，不要开公开 Issue。

## 许可证

本项目保留所有权利，详见 [LICENSE](LICENSE)。提交贡献即表示同意作者在项目范围内使用该贡献，且不改变项目的授权条款。
