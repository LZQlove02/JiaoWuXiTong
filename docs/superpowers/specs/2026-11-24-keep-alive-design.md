# 保持登录（会话保活）方案

- 日期：2026-11-24
- 状态：已实现。本版取代 [2026-11-02-session-keepalive-design.md](2026-11-02-session-keepalive-design.md)（初版只描述理想链路，已与代码脱节）；会话失效后的降级与静默重登见 [2026-11-09-session-resilience-design.md](2026-11-09-session-resilience-design.md)。
- 适用范围：`android/app/src/main/java/com/lzqlove/jiaowu/keepalive/` 全部、`src/views/AccountSettings.vue`、`src/utils/keepAliveConsent.js`、`src/utils/keepAliveSummary.js`、`SsoLoginPlugin.java` 的 `setKeepAlive / getKeepAliveState / clearSession`、`JwxtHttpPlugin.java` 的 Cookie 落盘入口。
- 最近三处修正（2026-11-24，已合入 master，未发版）：无本地会话时不排程、判定失效后退避 3 小时、设置页与教程文案说明「学校侧 3 小时强制下线」。

---

## 0. 一句话

**保持登录 = 用本机加密保存的教务会话，每 30 分钟在后台向学校教务系统发一次只读请求，把会话「焐热」；它只能降低重新登录频率，不能延长学校设定的会话上限。**

从多轮真机验证来看，三条边界必须写在最前面，否则方案看起来像「应该一直在线」，实际做不到：

1. 保活 **不能** 绕过统一认证、短信验证，也不能改写学校 Cookie；
2. 学校对「连续在线约 3 小时」的会话会强制下线，保活对此无能为力；
3. 后台没有 WebView，**静默重登只能在前台发生**。保活做的是「别让空闲把你踢掉」，不是「被踢了自动爬回来」。

---

## 1. 背景与硬约束

### 1.1 两层会话

登录态由两层会话共同决定，二者寿命不同、归属不同：

| 层 | 谁签发 | 寿命 | 决定什么 |
|---|---|---|---|
| 教务业务会话（zfjw，JSESSIONID 等） | 正方教务系统 | 空闲约 2–3 小时失效 | 多久要重新登录一次 |
| 统一认证 CASTGC | 学校 SSO | 由学校控制，通常更长 | 重新登录时要不要输账号密码与短信验证码 |

保活只作用于第一层（每 30 分钟发一个请求让它不空闲超时），完全触碰不到 CASTGC；CASTGC 还在时，用户被踢后前台可静默换票，无需短信。

### 1.2 学校侧的三个事实

| 事实 | 证据 | 对方案的约束 |
|---|---|---|
| 统一认证页没有「记住登录」选项，CASTGC 有效期由学校控制 | `SsoCookieCleaner.java` 注释；历年真机验证 | 不做「永久登录」承诺，不改写 Cookie |
| 教务会话**空闲**约 2–3 小时失效 | 多轮真机验证（`docs/reports/真机验证报告-20261027.md`） | 30 分钟保活能挡住「每次打开都要重登」 |
| 教务会话**连续在线**约 3 小时也会被强制下线 | 2026-11-24 用户反馈；当日 14:48 本地判定失效，按此推算登录时间约 11:48（推断，未核对服务端日志） | 保活有天花板；失效后必须退避，不能每 30 分钟白请求 |

> 第 3 条是 2026-11-24 才明确的：此前只把保活当「防空闲」，现在明确它同时要「防无意义的消耗」。

### 1.3 应用侧的约束

- **没有自建服务端**：不像微信有 refresh token 与推送续期，只能由客户端发起请求。
- **认证必须在 WebView 里完成**：`SsoLoginActivity + SsoLoginPlugin`，证书指纹固定 + SPKI pin；后台没有 WebView 实例。
- **Android 后台限制**：只用 `setAndAllowWhileIdle`（非精确闹钟），不申请 `SCHEDULE_EXACT_ALARM`；Doze 与厂商省电策略会推迟甚至跳过。
- **隐私合规**：后台网络请求必须有单独说明并可随时关闭（见 §5、§11）。

---

## 2. 目标与非目标

**目标**

1. 用户长期不回 App 时，尽量让教务会话仍然有效，减少「打开即重登 + 短信」的次数。
2. 会话确实失效时，快速判定、停止无意义请求，并把状态如实显示给用户。
3. 保活的时间与结果可观测（设置页能看见上次/下次），出问题可自查。
4. 用户随时可关；退出登录、撤回同意、清除数据后立即停止。

**非目标**

- 不追求「永不掉线」（做不到，见 §1.2）。
- 不在后台弹出登录界面、不保存账号密码、不模拟登录表单、不引入代理服务端。
- 不绕过学校会话有效期，不篡改 Cookie，不延长 CASTGC。
- 不影响课表变更/成绩/通知等其它后台检查子系统（见 §10）。

---

## 3. 名词与状态

原生侧持久化在 SharedPreferences `jiaowu_keepalive`（`SessionKeepAliveStore.java:16-21`）：

| 键 | 类型 | 含义 | 何时写 |
|---|---|---|---|
| `enabled` | boolean，默认 `false` | 用户是否开启保持登录 | 设置页开关、退出登录 |
| `lastCheckAt` | long，ms | 最近一次检查时间 | 每次检查结束 |
| `lastResult` | string | `ALIVE` / `EXPIRED` / `FAILED`（白名单外一律视为空） | 每次检查结束 |
| `nextCheckAt` | long，ms | 下一次计划时间；`0` 表示无排程 | 排程成功 / 取消 |
| `backoff` | boolean，默认 `false` | 是否进入「失效退避 3 小时」 | 结果为 `EXPIRED` 时置真，其余结果清除 |

对外桥接（`SsoLoginPlugin.keepAliveState()`）返回：

```json
{ "enabled": true, "active": true, "intervalMinutes": 30,
  "lastCheckAt": 1763966880000, "lastResult": "EXPIRED", "nextCheckAt": 1763980080000 }
```

- `active = enabled && AuthCookieStore.load() != null`（本地确有可解密的会话）。
- 观测字段无值时输出 `JSONObject.NULL`（键始终存在），前端不必区分「缺字段」与「从未保活」。

**状态机**

```
            用户开启 / 登录成功
   [已关闭] ───────────────────▶ [正常: 30 分钟]
      ▲                              │  ▲
      │ 关闭开关/退出登录             │  │ 非 EXPIRED 结果
      │ 撤回同意/清除数据             ▼  │ （ALIVE / FAILED）
      └─────────────────────────── [退避: 3 小时]
                                     │
                            无本地会话时：直接取消排程（停在「已停止」）
```

三种终止/降级入口：

| 入口 | 触发点 | 行为 |
|---|---|---|
| 关闭开关 | 设置页 `toggleKeepAlive` → `setKeepAlive(false)` | `SessionKeepAliveStore.setEnabled(false)` + `Scheduler.cancel()` |
| 退出登录 | `auth.logout()` → `setKeepAlive(false)` → `SsoLogin.clearSession()` | 取消闹钟 + 清观测字段 + 失效 zfjw Cookie |
| 本地无会话 | 闹钟触发时 `!hasStoredCookie` | `cancel()`，设置页显示「已停止，等待重新登录」 |

---

## 4. 用户可见行为

### 4.1 开关与首次说明

| 位置 | 内容 | 实现 |
|---|---|---|
| 我的 → 账号与设备 → 登录与会话 | 「保持登录」开关 + 四段说明 + 状态行 | `src/views/AccountSettings.vue:8-33` |
| 首次开启 | `ConsentGate` 弹窗（检查内容 / 频率与限制 / 数据与隐私 / 关闭方式） | `KEEPALIVE_CLAUSES`，`AccountSettings.vue:107-117` |
| 同意记录 | `jiaowu.keepalive.consent = 1`；拒绝记录 `jiaowu.keepalive.declined = 1` | `src/utils/keepAliveConsent.js` |
| 撤回隐私同意 | 同时撤销保活同意并关闭开关 | `AppSettings.vue:215` `revokeKeepAliveConsent()` |

> **现状与文案不一致（待拍板，见 §15 决策 A）**：原生 `enabled` 默认是 `false`，当前实现是「用户在设置页打开开关时才弹说明并开启」，首次登录并不会自动开启；而 `src/utils/tutorial.js:119`、`src/utils/privacy.js:26` 与隐私政策正文都写「默认开启」。本方案以代码为准记录真实行为，文案对齐作为待办。

### 4.2 设置页状态行

`src/utils/keepAliveSummary.js` 把三个字段译成一句话（分隔符统一 ` · `）：

| 原生状态 | 显示 |
|---|---|
| `enabled && active`，有历史 | `上次保活 18:51（会话有效） · 下次约 19:21` |
| `enabled` 但 `active=false` | `上次保活 14:48（会话已失效） · 已停止，等待重新登录` |
| `active=false` 且无历史 | `已停止，等待重新登录` |
| `enabled && active`，从未执行 | `尚未执行过保活 · 下次约 19:09` |
| 开关关闭 | 不显示状态行 |

同日只显示 `HH:mm`，跨日补 `M月D日`。结果词表：`ALIVE 会话有效`、`EXPIRED 会话已失效`、`FAILED 临时失败，会自动重试`。

### 4.3 电池白名单

保持登录 → 若系统仍在限制后台（`batteryExempted === false`）则显示一行入口「允许后台稳定运行」，点击走 `SsoLogin.requestBatteryOptimizationExemption()`（`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）。隐私政策已单独说明该权限只用于让定时任务按时执行（`src/utils/privacy.js:32`）。

---

## 5. 数据与存储

| 数据 | 位置 | 保护 |
|---|---|---|
| 教务 Cookie | SharedPreferences `auth_session` / 键 `cookie` | Android Keystore `AES/GCM/NoPadding`（别名 `jwxt_auth_cookie_key`、12 字节 IV），仅回传 `zfjw.lzzy.net` |
| 保活开关与记账 | SharedPreferences `jiaowu_keepalive` | 仅本机；不含 Cookie、响应内容、学号姓名 |
| 同意记录 | localStorage `jiaowu.keepalive.consent` / `.declined` | 版本号字符串；范围或频率变化时 `KEEPALIVE_CONSENT_VERSION` +1 重新说明 |

- `AuthCookieStore.hasStoredCookie(context)` 只 `prefs.contains(KEY)`，**不解密**——所以「本地有会话」判断不会因 Keystore 暂时不可用而误判（`AuthCookieStore.java:130`）。
- `AuthCookieStore.load()` 解密失败返回 `null`；此时若 `hasStoredCookie` 为真，保活记为 `FAILED` 而不是「已失效」，保留闹钟链等下次重试（Keystore 偶发不可用不该清用户登录态）。
- 不保存：账号密码、短信验证码、教务响应正文、任何上传。

---

## 6. 运行链路

```
① 登录成功 / 静默重登成功
   src/api/zf.js → nativeHttp.setAuthCookies(cookie)
        ▼
② JwxtHttpPlugin.setAuthCookies
   AuthCookieStore.save()（合并 Set-Cookie）→ SessionKeepAliveScheduler.resumeIfEnabled()
        ▼
③ resumeIfEnabled
   enabled? ──否──▶ cancel() 结束
   enabled  ──是──▶ SessionKeepAliveStore.setBackoff(false) → scheduleNext()
        ▼
④ scheduleNext
   shouldSchedule(enabled, hasStoredCookie)? ──否──▶ enabled 时 cancel()，结束
   是 → triggerAt = nextTrigger(now, isBackoff) → setAndAllowWhileIdle(RTC_WAKEUP)
        → setNextCheckAt(triggerAt)
        ▼
⑤ 闹钟触发 → SessionKeepAliveReceiver
   action 校验 → enabled 校验 ──否──▶ cancel()
   先 scheduleNext()（保证单次失败不断链）→ goAsync() → 起 session-keepalive 线程
        ▼
⑥ SessionKeepAliveService.checkNow()
   load cookie 为空 && !hasStoredCookie ──▶ cancel() + EXPIRED
   否则 POST 轻量接口 → 判定 → recordCheckResult(checkAt, result)
        ▼
⑦ result == EXPIRED
   Receiver 再次 scheduleNext()（此时 backoff=true → 3 小时后）
        ▼
⑧ 用户前台回到 App，业务请求 401
   attemptSilentRelogin()（12s 超时）──成功──▶ 回到 ①，退避被清除、恢复 30 分钟
```

关键文件与位置：

| 环节 | 文件 |
|---|---|
| 排程 | `android/app/src/main/java/com/lzqlove/jiaowu/keepalive/SessionKeepAliveScheduler.java:29-90` |
| 闹钟入口 | `.../keepalive/SessionKeepAliveReceiver.java:18-47` |
| 探测请求与判定 | `.../keepalive/SessionKeepAliveService.java`（`checkNow` / `runCheck` / `request`） |
| 记账 | `.../keepalive/SessionKeepAliveStore.java:53-58`、`:120-127` |
| 桥接 | `SsoLoginPlugin.java`（`setKeepAlive` / `getKeepAliveState` / `clearSession`） |
| 开机与覆盖安装补排 | `.../change/ScheduleBootReceiver.java` |
| App 启动补排 | `MainActivity.onCreate()` 末尾 `SessionKeepAliveScheduler.resumeIfEnabled()` |

**探测请求定义**（`SessionKeepAliveService`）

```
POST https://zfjw.lzzy.net/xtgl/index_cxDbsy.html?doType=query
Content-Type: application/x-www-form-urlencoded
body: flag=0&sfyy=1&queryModel.showCount=1&queryModel.currentPage=1
```

- 走 `HttpPolicy.AUTH` 主机白名单；手动处理重定向（`MAX_REDIRECTS=4`，`followRedirects=false`）。
- 超时：OkHttp connect 4s / read 6s / call 8s，整条链路 `DEADLINE_MS=9000`。
- 只读首部最多 4 KB 判断响应类型，不解析、不落盘正文。
- 用 JSON 接口而不是 HTML 首页，是为了避免正常页面里出现「统一身份认证」或 authserver 链接时被误判为失效。

---

## 7. 判定规则

| 观察到的现象 | 结果 | 后续 |
|---|---|---|
| 本地无 Cookie 且 `hasStoredCookie == false` | `EXPIRED` | `cancel()`，设置页「已停止，等待重新登录」 |
| 本地有记录但解密失败 | `FAILED` | 保留闹钟，30 分钟后重试 |
| 重定向目标为 `http://` 或 `authserver.lzzy.net` | `EXPIRED` | 3 小时退避 |
| HTTP 401 / 403 | `EXPIRED` | 3 小时退避 |
| HTTP 非 200（5xx、网关错误等） | `FAILED` | 30 分钟后重试 |
| 200 但正文以 `<` 开头且命中登录页特征（`login_slogin` 或 `name="yhm"`） | `EXPIRED` | 3 小时退避 |
| 200 且非登录页 | `ALIVE` | 合并 Set-Cookie，30 分钟后重试 |

补充：

- 判定只看「会话还在不在」，**不解析业务数据**；请求体固定，与用户课表/成绩无关。
- `ALIVE` 时把响应里的 `Set-Cookie` 合并回 `AuthCookieStore`，避免服务端轮换会话标识后前台还在用旧值。
- 单次网络失败（`FAILED`）绝不清登录态、不取消闹钟——这是 2.2.1「弱网不清登录态」的延续。

---

## 8. 排程策略

| 触发源 | 是否排程 | 间隔 |
|---|---|---|
| 登录成功 / 静默重登成功（`resumeIfEnabled`） | 是，且先清 `backoff` | 30 分钟 |
| App 启动（`MainActivity.onCreate`） | 是 | 30 分钟（若有退避则为 3 小时） |
| 开机、覆盖安装（`ScheduleBootReceiver`） | 是 | 同上 |
| 设置页打开开关 | 是 | 30 分钟 |
| 闹钟触发（Receiver 先排下一次） | 是 | 取决于上一次结果 |
| 上一次结果 `EXPIRED` | 是（Receiver 再排一次） | **3 小时** |
| 上一次结果 `ALIVE` / `FAILED` | 是 | 30 分钟 |
| 开关关闭 / 退出登录 / 撤回同意 / 清除数据 | 否，并取消 | — |
| 开关开但本地无会话 | 否，并取消 | — |

设计要点：

1. **先排下一次再发请求**：单次失败/进程被回收不会让链条永久断掉（`SessionKeepAliveReceiver.java:25-26`）。
2. **退避只在明确失效时启动**（`recordCheckResult` 里 `"EXPIRED".equals(result)`），临时失败仍 30 分钟重试，否则一次网络抖动会让保活停 3 小时。
3. **任何新会话都清零退避**（`resumeIfEnabled`），登录后应当立刻回到 30 分钟节奏。
4. **无会话不排程**（`shouldSchedule(enabled, hasStoredCookie)`）：这是 2026-11-24 修的 bug——此前开关开着但 Cookie 已被清掉时，每次启动仍排一次注定失败的检查，还刷新「上次保活」，设置页显示根本不存在的「下次保活」。
5. 只用非精确闹钟，`window` 由系统决定（真机实测约 +22 分 30 秒）；不申请精确闹钟权限，省电策略推迟属预期。

---

## 9. 失败矩阵与自愈

| 现象 | 应用行为 | 用户可见 |
|---|---|---|
| 网络不可达 / 超时 | `FAILED`，30 分钟后重试 | 状态行「临时失败，会自动重试」 |
| Doze 或厂商省电推迟闹钟 | 闹钟延后触发；无补偿 | 无（属预期） |
| 学校 3 小时强制下线 | 一次 `EXPIRED` → 退避 3 小时 | 「会话已失效」 |
| 会话失效且前台已打开 | 401 → 静默重登（12s） | 成功则无感；失败则只读快照 + 顶部横幅「重新登录」 |
| CASTGC 也失效 | 静默重登返回 `login-form` 等 definitive 失败 | 只读 7 天 + 横幅，需本人走统一认证（含短信） |
| 服务端轮换 Set-Cookie | 合并回存储 | 无感 |
| Keystore 暂时不可用 | `FAILED`，不清 Cookie | 「临时失败，会自动重试」 |
| 用户关开关 | 立即取消闹钟，保留历史 | 开关关闭，状态行隐藏 |
| 退出登录 | 取消闹钟 + 清记账 + 失效 Cookie | 回登录页 |
| 本地数据被清/重装 | 无 Cookie → 下次触发 `cancel` | 「已停止，等待重新登录」 |

---

## 10. 与相邻机制的关系

| 机制 | 关系 |
|---|---|
| 静默重登（`src/stores/auth.js:199-240`） | 保活失效后的唯一「续命」途径：前台 401 时用 CASTGC 换票，成功即 `setAuthCookies` → `resumeIfEnabled` 清退避恢复 30 分钟。超时 12s；definitive 失败（`no-ticket` / `login-form` / `blocked` 等）→ 会话失效；网络类失败 → `sessionSuspended` 保留登录态，冷却 30s；其它冷却 5 分钟。 |
| 只读快照（7 天）+ 失效横幅 | 会话失效后不清数据，首页横幅提示「学校统一认证已到期，当前显示离线缓存」+「重新登录」。 |
| 课表变更 / 成绩 / 通知检查（`change/*`） | 独立闹钟（默认关闭，07:10 / 12:50 两时段），共用同一份 Cookie；失效时发「需要重新登录」通知，**不写保活记账**、不取消保活链。 |
| 桌面小组件 | 只消费本地 payload，与保活无耦合。 |
| 退出登录 | 先 `setKeepAlive(false)` 再 `clearSession()`，避免重新登录后被旧偏好自动恢复（`auth.js:495-518`）。 |

---

## 11. 隐私与合规

- 后台请求**只**发往 `zfjw.lzzy.net`，只携带本机加密保存的会话，不携带学号、姓名或设备标识。
- 不读取、不保存、不上传响应正文；只保留「时间 + 三态结果」。
- 隐私政策「后台检查」章节已写明频率、内容、结果存放与关闭方式（`src/utils/privacy.js:26-32`）。
- 电池优化白名单单独说明用途，用户可拒绝、可随时在系统设置撤销。
- 撤回隐私同意 / 退出登录 / 清除本机个人数据 → 立即停止并清理。

---

## 12. 可观测性与排障

设置页状态行是给用户的，排查时优先看这三处：

```bash
# 1) 保活是否排上（Windows）
adb shell dumpsys alarm | findstr /C:"keepalive.CHECK"
# macOS / Linux
adb shell dumpsys alarm | grep -A4 'keepalive.CHECK'

# 2) 每次检查的结果（只有三态，无敏感信息）
adb logcat -d -s JiaowuKeepAlive:D

# 3) 设置页拿到的原始状态
#    我的 → 账号与设备 → 保持登录 下方状态行
```

2026-11-24 真机证据（第 13 周、v2.2.2 + 当日修正）：

| 时间 | 事件 |
|---|---|
| 约 11:48 | 用户完成统一认证（推断） |
| 14:48:37 | 闹钟触发 → `EXPIRED` → 清 Cookie + `cancel`，链条停摆（旧逻辑每 30 分钟仍会排「注定失败」的检查） |
| 18:09 | 打开 App（无 Cookie），旧版又排了一次 18:39；新版不再排程 |
| 18:19–18:20 | 用户重新登录 → `resumeIfEnabled` → 排到 18:50:59 |
| 18:39 | 装退避版本，设置页显示「上次保活 14:48（会话已失效） · 下次约 19:09」 |

---

## 13. 测试

**Java 单测**（`android/app/src/test/java/com/lzqlove/jiaowu/keepalive/`）

| 用例 | 覆盖 |
|---|---|
| `SessionKeepAliveTest.schedulesOnlyWhenSwitchIsOnAndSessionIsStored` | `shouldSchedule(true,true)=true`，其余三种 `false` |
| `SessionKeepAliveTest.expiryBacksOffToThreeHours` | `nextTrigger(now,true)=now+3h`、`nextTrigger(now,false)=now+30min` |
| `SessionKeepAliveStoreTest.expiryArmsBackoffAndOtherResultsClearIt` | `EXPIRED` 置 `backoff`，`ALIVE`/`FAILED` 清除 |
| `SessionKeepAliveStoreTest.clearResetsBackoff` | 退出登录清空全部记账 |
| `SessionKeepAliveStoreTest`（KEY_BACKOFF 常量） | 键名稳定，避免静默改名 |

**前端单测**（`tests/keepAliveSummary.test.js`，8 条）：同日/跨日、`active=false` 有/无历史、旧版无 `active` 字段按有会话处理、从未执行、`null`、非法时间戳。

**真机验收清单**

1. 开关开启 → `dumpsys alarm` 出现 `keepalive.CHECK`，约 30 分钟后；设置页「下次约」与之一致。
2. 关闭开关 → 闹钟消失，状态行隐藏，无新请求。
3. 退出登录 → 闹钟消失、记账清空；重新登录后自动恢复 30 分钟。
4. 登录后放置超过 2 小时：会话仍有效（`ALIVE`）。
5. 手动把本地 Cookie 清掉（或等服务端判失效）→ 下一次检查为 `EXPIRED`；设置页显示「已停止，等待重新登录」（无 Cookie）或「下次约 +3 小时」（有 Cookie 但失效）。
6. 失效后重新登录 → `backoff` 清零，「下次约」回到 +30 分钟。
7. 弱网/断网：检查为 `FAILED`，登录态不清、闹钟不断。

---

## 14. 已知限制

1. **3 小时天花板**：学校连续在线强制下线，保活无法越过；到期必须人工登录一次。
2. **后台不能静默重登**：没有 WebView，被踢后在后台无解，只能等前台 401 触发。
3. **省电策略**：非精确闹钟可能被推迟数十分钟甚至跳过；部分 ROM 会冻结后台。
4. **退避期间不探活**：判定失效后 3 小时内不再请求，若用户在这期间重新登录，靠 `resumeIfEnabled` 立即恢复。
5. **多设备不同步**：保活只作用于本机 Cookie，另一台设备的会话仍按学校规则失效。
6. **WebView 内部存储不可见**：`/data/data` 未 root 无法读取，真机排障只能用日志与桥接状态（2026-11-24 实践结论）。

---

## 15. 待办与决策点

**决策点（需要拍板）**

| # | 问题 | 选项 | 建议 |
|---|---|---|---|
| A | 「默认开启」的文案与实现不一致（原生默认关闭、需手动开启） | ① 改文案为「默认关闭，可手动开启」；② 实现首次登录自动弹说明并在同意后开启 | ①：改动小、合规最稳；② 需要把 ConsentGate 提到全局，且后台请求要等用户确认 |
| B | 保活间隔是否从 30 分钟放宽到 90 分钟 | 保持 30；放宽到 90 | 保持 30。学校给的是连续 3 小时上限，放宽只会让空闲窗口更易超时 |
| C | 是否实现「前台补保活」（>25 分钟回到前台补一次） | 做 / 不做 | 做，但要与静默重登合并触发，避免两套逻辑打架 |
| D | 退避期间是否给用户「立即检查一次」 | 做 / 不做 | 不做。用户已能通过重新登录恢复，多一个按钮只增加解释成本 |
| E | 电池白名单引导是否并入首启 | 并入 / 维持设置页 | 维持设置页，仅当 `batteryExempted === false` 时露出入口 |

**待办**

- P1：实现决策 C（前台补保活），复用 `SessionKeepAliveService.checkNow` 的结果口径，不新增记账字段。
- P1：文案对齐（决策 A 的落地），同步 `tutorial.js`、`privacy.js`、隐私政策正文与 `docs/plan/方案.md:292`。
- P2：`lastResult` 增加最近一次失败原因（`timeout` / `http-5xx` / `bridge`）以便支持排查，仍不落正文。
- P2：`manualLogout` 的 SharedPreferences 权威副本（防止进程被杀后旧偏好自动恢复保活，见 11-09 §13）。
- P2：把「失效退避 3 小时」补进 `docs/reports/真机验证报告-20261027.md` 的验收记录。

---

## 16. 变更记录

| 日期 | 变更 | 关联 |
|---|---|---|
| 2026-11-02 | 初版：30 分钟单次闹钟链、ConsentGate、`AuthCookieStore` 共享、停止条件 | `2026-11-02-session-keepalive-design.md` |
| 2026-11-09 | 会话韧性：静默重登、失效不清缓存、只读快照 7 天 | `2026-11-09-session-resilience-design.md` |
| 2026-11-22 | 2.2.2 发布：只读快照 + 失效横幅、保活可观测（设置页状态行）、4×4 翻周 | `docs/plan/方案.md` |
| 2026-11-24 | 无本地会话时不排程（`shouldSchedule`）；判定 `EXPIRED` 后退避 3 小时；账号页与教程文案说明「学校 3 小时强制下线」；设置页「已停止，等待重新登录」 | 提交 `ba763d7`、`f9d5679`；本文件 |
| 2026-11-24 | 重新整理本方案（取代 11-02 初版），补充状态机、判定表、失败矩阵、排障与决策点 | 本文件 |
