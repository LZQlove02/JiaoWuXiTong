import { Capacitor, registerPlugin } from '@capacitor/core'

const JwxtHttp = registerPlugin('JwxtHttp')
const SsoLogin = registerPlugin('SsoLogin')

export async function httpGet(url, headers = {}) {
  if (!Capacitor.isNativePlatform()) {
    throw new Error('原生 HTTP 插件仅在 App 内可用')
  }
  const result = await JwxtHttp.get({ url, headers })
  return result
}

export async function downloadFile(url, filename) {
  if (!Capacitor.isNativePlatform()) {
    throw new Error('原生下载仅在 App 内可用')
  }
  return JwxtHttp.download({ url, filename })
}

// 带登录态的附件下载：只允许 zfjw，保存到应用私有 cache
export async function authDownload(url, filename) {
  if (!Capacitor.isNativePlatform()) {
    throw new Error('原生附件下载仅在 App 内可用')
  }
  return JwxtHttp.authDownload({ url, filename })
}

export async function clearAuthDownloads() {
  if (!Capacitor.isNativePlatform()) return
  return JwxtHttp.clearAuthDownloads()
}

export async function clearPublicDownloads() {
  if (!Capacitor.isNativePlatform()) return
  return JwxtHttp.clearPublicDownloads()
}

export async function fileExists(path) {
  if (!Capacitor.isNativePlatform()) return { exists: false }
  return JwxtHttp.fileExists({ path })
}

export async function loginWithSso() {
  if (!Capacitor.isNativePlatform()) {
    throw new Error('统一认证登录仅在 App 内可用')
  }
  return SsoLogin.login()
}

// 原生静默登录的总预算 = TLS 预检（最多 8 秒）+ Activity 内的等待（默认 12 秒）+ 收尾。
// Activity 的超时计时从 loadUrl 开始，预检耗时不包含在内，所以 JS 兜底必须额外留出预检与桥接余量；
// 否则会把仍在进行的换票误判为超时，用户反而被当成会话失效。
const SILENT_RELOGIN_PREFLIGHT_BUDGET_MS = 8000
const SILENT_RELOGIN_BRIDGE_MARGIN_MS = 4000

// 会话失效时用统一认证票据静默换票；无界面、不弹对话框
export async function silentRelogin(options = {}) {
  if (!Capacitor.isNativePlatform()) {
    return { ok: false, reason: 'unsupported' }
  }
  const nativeTimeout = Number(options.timeoutMs) || 12000
  // 后台启动 Activity 可能被系统拦截且不给回调，必须在 JS 侧兜底，
  // 否则静默 Promise 永不结束，会把整条会话失效处理链卡死。
  const guarded = SsoLogin.silentRelogin({ timeoutMs: nativeTimeout }).catch(() => ({
    ok: false,
    reason: 'bridge'
  }))
  let timer = null
  const timeout = new Promise((resolve) => {
    timer = setTimeout(
      () => resolve({ ok: false, reason: 'timeout' }),
      nativeTimeout + SILENT_RELOGIN_PREFLIGHT_BUDGET_MS + SILENT_RELOGIN_BRIDGE_MARGIN_MS
    )
  })
  try {
    return await Promise.race([guarded, timeout])
  } finally {
    if (timer) clearTimeout(timer)
  }
}

// 只读探测是否仍持有 CASTGC，不返回票据值
export async function hasSsoTicket() {
  if (!Capacitor.isNativePlatform()) return { hasTicket: false }
  return SsoLogin.hasSsoTicket()
}

// 明确退出登录时清 CASTGC 会话；会话失效时不调用，保留 WebView 中的登录票据
export async function clearSsoSession() {
  if (!Capacitor.isNativePlatform()) return
  return SsoLogin.clearSession()
}

// 「保持登录」：开启后由原生约每 30 分钟在后台维持一次教务会话
export async function setKeepAlive(enabled) {
  if (!Capacitor.isNativePlatform()) return { enabled: false, active: false }
  return SsoLogin.setKeepAlive({ enabled: Boolean(enabled) })
}

export async function getKeepAliveState() {
  if (!Capacitor.isNativePlatform()) {
    return { enabled: false, active: false, intervalMinutes: 30 }
  }
  return SsoLogin.getKeepAliveState()
}

// 回前台补一次保活：原生按「距上次检查超过 25 分钟」节流，未到点会直接返回 SKIPPED
export async function topUpKeepAlive() {
  if (!Capacitor.isNativePlatform()) return { result: 'SKIPPED', reason: 'inactive' }
  return SsoLogin.checkKeepAliveNow()
}

// 系统是否已允许本应用在后台稳定运行（电池优化白名单）
export async function getBatteryOptimizationState() {
  if (!Capacitor.isNativePlatform()) return { ignoring: true }
  return SsoLogin.getBatteryOptimizationState()
}

// 打开系统电池优化白名单申请页；部分 ROM 深睡时会冻结后台闹钟
export async function requestBatteryOptimizationExemption() {
  if (!Capacitor.isNativePlatform()) return { ignoring: true }
  return SsoLogin.requestBatteryOptimizationExemption()
}

export async function setAuthCookies(cookie) {
  if (!Capacitor.isNativePlatform()) {
    throw new Error('原生 HTTP 插件仅在 App 内可用')
  }
  return JwxtHttp.setAuthCookies({ cookie })
}

export async function loadAuthCookies() {
  if (!Capacitor.isNativePlatform()) return { restored: false }
  return JwxtHttp.loadAuthCookies()
}

export async function clearAuthCookies() {
  if (!Capacitor.isNativePlatform()) return
  return JwxtHttp.clearAuthCookies()
}

export async function authGet(url) {
  if (!Capacitor.isNativePlatform()) {
    throw new Error('原生 HTTP 插件仅在 App 内可用')
  }
  return JwxtHttp.authGet({ url })
}

export async function authPost(url, body = '') {
  if (!Capacitor.isNativePlatform()) {
    throw new Error('原生 HTTP 插件仅在 App 内可用')
  }
  return JwxtHttp.authPost({ url, body })
}

export async function isDebugBuild() {
  if (!Capacitor.isNativePlatform()) return false
  const result = await JwxtHttp.isDebugBuild()
  return Boolean(result?.debug)
}
