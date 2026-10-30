## 改动说明

<!-- 做了什么，为什么做；关联 Issue：Close #xx -->

## 影响范围

- 涉及模块：
- 是否影响已发布的版本行为：
- 是否需要同步文档（`docs/plan`、`docs/superpowers/specs`、README）：

## 验证

- [ ] `npm run lint`
- [ ] `npm run typecheck`
- [ ] `npm test`
- [ ] `cd android && ./gradlew testDebugUnitTest`
- [ ] `npm run build`（涉及前端或资源配置时）
- [ ] 真机验证：机型 / Android 版本 / 验证页面

## 隐私与安全检查

- [ ] 未提交 `keystore.properties`、`*.jks`、`*.keystore` 或任何密码
- [ ] 未包含真实学号、姓名、成绩、课表、Cookie、会话票据
- [ ] 截图与日志已脱敏
- [ ] 新增网络请求遵守主机白名单与 HTTPS 约束，未降低重定向校验
- [ ] 新增本地存储已定义清理时机（退出登录 / 切换账号 / 缓存清除）
