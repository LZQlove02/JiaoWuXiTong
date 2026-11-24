import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { resetAcademicApi, setAcademicApi } from '../api'
import { ZfAcademicApi, clearScheduleSnapshot } from '../api/zf'
import {
  clearAuthCookies,
  clearAuthDownloads,
  clearPublicDownloads,
  clearSsoSession,
  loadAuthCookies,
  setKeepAlive
} from '../api/nativeHttp'
import { clearScheduleChange } from '../native/scheduleChangeBridge'
import { clearCrashLog } from '../native/supportBridge'
import { notifySessionExpired, setSessionExpiredHandler } from '../utils/sessionExpiry'
import { setSessionBlockCheck } from '../utils/sessionState'
import { clearDataCaches } from '../utils/dataCache'
import { clearPortalCache } from '../utils/portalCache'

const RESTORE_TIMEOUT_MS = 15000
// 本机存有会话但解密暂时失败时，给 Keystore 一次重试机会
const SESSION_PENDING_RETRY_MS = 600
// 连续多少次冷启动都读不出会话后，按不可用处理，避免永久停在离线快照
const MAX_PENDING_FAILURES = 3
const PENDING_FAILURES_KEY = 'jiaowu.session.pendingFailures'
// 票据确认失效的时间点：冷启动时据此把快照当作只读缓存续上，而不是每次回登录页
const EXPIRED_AT_KEY = 'jiaowu.session.expiredAt'
const EXPIRED_REASON_KEY = 'jiaowu.session.expiredReason'
// 只说「上次的课表还能看」的话，7 天足够；再久属过期处理，直接丢弃
const READ_ONLY_TTL_MS = 7 * 24 * 60 * 60 * 1000
// 静默换票的超时与失败冷却：避免弱网或票据失效时反复拉起隐藏页面。
// 冷启动时 TLS 预检、CAS 跳转与教务首页加载都需要时间，6 秒容易误判
const SILENT_RELOGIN_TIMEOUT_MS = 12000
const SILENT_RELOGIN_COOLDOWN_MS = 5 * 60 * 1000
// 网络/超时类失败只做短冷却，避免弱网刚恢复时被 5 分钟冷却挡住
const SILENT_RELOGIN_TRANSIENT_COOLDOWN_MS = 30 * 1000
// 只有这些原因代表票据或会话已不可恢复，才清空本机会话与缓存
const DEFINITIVE_SILENT_FAILURES = new Set([
  'no-ticket',
  'login-form',
  'blocked',
  'no-cookie',
  'verify',
  'unsupported'
])

function readPendingFailures() {
  try {
    return Number(localStorage.getItem(PENDING_FAILURES_KEY)) || 0
  } catch (error) {
    return 0
  }
}

function writePendingFailures(value) {
  try {
    if (value <= 0) localStorage.removeItem(PENDING_FAILURES_KEY)
    else localStorage.setItem(PENDING_FAILURES_KEY, String(value))
  } catch (error) {
    // 存储不可用时按无计数处理，不影响本次恢复流程
  }
}

function readExpiredAt() {
  try {
    return Number(localStorage.getItem(EXPIRED_AT_KEY)) || 0
  } catch (error) {
    return 0
  }
}

function readExpiredReason() {
  try {
    return localStorage.getItem(EXPIRED_REASON_KEY) || null
  } catch (error) {
    return null
  }
}

function writeExpiredFlag(reason) {
  try {
    localStorage.setItem(EXPIRED_AT_KEY, String(Date.now()))
    if (reason) localStorage.setItem(EXPIRED_REASON_KEY, reason)
    else localStorage.removeItem(EXPIRED_REASON_KEY)
  } catch (error) {
    // 存储不可用时只是失去跨启动的只读缓存，不影响当前界面的横幅
  }
}

function clearExpiredFlag() {
  try {
    localStorage.removeItem(EXPIRED_AT_KEY)
    localStorage.removeItem(EXPIRED_REASON_KEY)
  } catch (error) {
    // 同上
  }
}

// 冷启动恢复会话最多等待 15 秒，避免弱网时首屏长时间停在启动页
function restoreWithTimeout(api) {
  return new Promise((resolve, reject) => {
    // 超时后调用方已经按「网络故障」处理，此时迟到的 sessionExpired 必须另行上报，
    // 否则会被当成已处理的拒绝丢掉，用户会停在「看起来已登录、实际全部接口失效」的状态。
    let timedOut = false
    const timer = setTimeout(() => {
      timedOut = true
      const failure = new Error('恢复登录超时')
      failure.network = true
      reject(failure)
    }, RESTORE_TIMEOUT_MS)
    api.restoreSession().then(
      (profile) => {
        clearTimeout(timer)
        resolve(profile)
      },
      (error) => {
        clearTimeout(timer)
        if (timedOut) {
          if (error?.sessionExpired) notifySessionExpired()
          return
        }
        reject(error)
      }
    )
  })
}

// 解密失败时延迟重读一次；仍读不出返回 null，由调用方保留原登录态
function retryPendingSession() {
  return new Promise((resolve) => {
    setTimeout(() => {
      loadAuthCookies().then(
        (state) => resolve(state?.restored ? state : null),
        () => resolve(null)
      )
    }, SESSION_PENDING_RETRY_MS)
  })
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const loading = ref(false)
  const restoring = ref(false)
  // 本次会话是否刚完成登录：仅用于一次性欢迎提示，冷启动恢复会话不算
  const justLoggedIn = ref(false)
  // 会话暂时不可用（网络/超时等可恢复原因）：保持登录态与已加载数据，等下一次重试
  const sessionSuspended = ref(false)
  // 登录票据已确认失效（学校统一认证到期）：保留课表快照与缓存做只读展示，
  // 只清凭据与敏感附件，等用户点横幅重新登录
  const sessionExpired = ref(false)
  const expiredReason = ref(null)
  // api 层据此判断「只读模式」：直接查询 store 状态，避免两份副本漂移
  setSessionBlockCheck(() => sessionExpired.value)
  const isLoggedIn = computed(() => Boolean(user.value))
  let bootstrapPromise = null
  let expiringPromise = null
  let reloginPromise = null
  let silentCooldownUntil = 0
  // 主动退出后禁止任何自动重登，直到用户重新登录成功
  let manualLogout = false

  async function resetAcademic() {
    const { useAcademicStore } = await import('./academic')
    useAcademicStore().reset()
  }

  // 清掉本机所有会话相关数据：加密 Cookie、课表快照、变更记录、个人缓存与附件
  async function clearLocalSession() {
    // 每一步都可能是原生调用，任一步失败都不能中断整条清理链：
    // 否则第一步抛错就会留下 Cookie 与 user，导致「已登出」但仍能读到上一账号的数据。
    const steps = [
      () => clearScheduleChange(),
      () => clearAuthCookies(),
      () => clearScheduleSnapshot(),
      () => clearDataCaches(),
      () => clearPortalCache(),
      // 附件与正文图缓存可能包含上一账号查看过的材料，退出时必须一并删除
      () => Promise.all([clearAuthDownloads(), clearPublicDownloads()])
    ]
    for (const step of steps) {
      try {
        await step()
      } catch (error) {
        // 单步失败不阻断后续清理，下次清理仍会尝试
      }
    }
    resetAcademicApi()
    user.value = null
    justLoggedIn.value = false
    sessionSuspended.value = false
    sessionExpired.value = false
    expiredReason.value = null
    clearExpiredFlag()
    writePendingFailures(0)
  }

  // 用统一认证票据静默换票：只返回结果，不在失败时清理本机数据。
  // 失败原因由调用方区分「票据已不可恢复」与「网络/超时暂时失败」。
  function attemptSilentRelogin() {
    if (manualLogout) return Promise.resolve({ ok: false, reason: 'blocked' })
    if (reloginPromise) return reloginPromise
    if (Date.now() < silentCooldownUntil) {
      return Promise.resolve({ ok: false, reason: 'cooldown' })
    }
    const zf = new ZfAcademicApi()
    reloginPromise = zf
      .reloginSilently({ timeoutMs: SILENT_RELOGIN_TIMEOUT_MS })
      .then((result) => {
        if (!result?.ok) {
          const reason = result?.reason || 'verify'
          const transient =
            reason === 'network' ||
            reason === 'timeout' ||
            reason === 'bridge' ||
            reason === 'canceled'
          silentCooldownUntil =
            Date.now() +
            (transient ? SILENT_RELOGIN_TRANSIENT_COOLDOWN_MS : SILENT_RELOGIN_COOLDOWN_MS)
          return { ok: false, reason }
        }
        // 静默过程中用户主动退出，迟到的成功结果必须丢弃
        if (manualLogout) return { ok: false, reason: 'blocked' }
        setAcademicApi(zf)
        user.value = result.profile
        sessionSuspended.value = false
        sessionExpired.value = false
        expiredReason.value = null
        clearExpiredFlag()
        writePendingFailures(0)
        silentCooldownUntil = 0
        return { ok: true, reason: 'ok' }
      })
      .catch(() => {
        silentCooldownUntil = Date.now() + SILENT_RELOGIN_COOLDOWN_MS
        return { ok: false, reason: 'verify' }
      })
      .finally(() => {
        reloginPromise = null
      })
    return reloginPromise
  }

  // 会话暂时不可用但原因可能是网络/超时：只降级为「已挂起」，
  // 保留登录态、加密会话与已加载数据；下次回前台或用户点重试还能接上。
  function markSessionUnavailable() {
    sessionSuspended.value = true
  }

  // 票据确定不可续期（学校统一认证到期）：进入只读模式。
  // 只清凭据与敏感附件（附件里可能有别人材料），课表快照与个人缓存保留，
  // 用户仍能翻上次的课表/消息，再从横幅一键重新登录。
  async function markSessionExpired(reason) {
    if (manualLogout) return
    sessionExpired.value = true
    expiredReason.value = reason || null
    sessionSuspended.value = false
    writeExpiredFlag(reason || null)
    try {
      await clearAuthCookies()
    } catch (error) {
      // 凭据清理失败不阻断只读展示，下次启动仍会再试
    }
    try {
      await Promise.all([clearAuthDownloads(), clearPublicDownloads()])
    } catch (error) {
      // 附件清理失败同样不影响只读模式
    }
    resetAcademicApi()
    writePendingFailures(0)
    if (user.value) return
    // 冷启动恢复失败时 user 还是空的：用快照资料撑起只读界面
    const zf = new ZfAcademicApi()
    const cached = zf.applySnapshot()
    if (!cached) return
    setAcademicApi(zf)
    user.value = cached
    try {
      const { useAcademicStore } = await import('./academic')
      useAcademicStore().applySnapshotSchedule(zf.snapshotSchedule())
    } catch (error) {
      // 快照课表应用失败时保持空态，不影响只读模式本身
    }
  }

  // 只有票据明确失效、用户主动退出或校方拒绝时才清空本机会话；
  // 网络、超时等暂时失败保留 Cookie 与快照，避免一次弱网就逼用户重新登录
  async function clearSessionOrKeepRetry(reason) {
    if (manualLogout) return
    if (DEFINITIVE_SILENT_FAILURES.has(reason)) {
      await markSessionExpired(reason)
      return
    }
    markSessionUnavailable()
  }

  // 会话失效统一出口：先静默续期，续不上再按失败原因决定是否清理本机数据
  async function expireSession() {
    if (expiringPromise) return expiringPromise
    expiringPromise = (async () => {
      const result = await attemptSilentRelogin()
      if (result.ok) return true
      await clearSessionOrKeepRetry(result.reason)
      return false
    })().finally(() => {
      expiringPromise = null
    })
    return expiringPromise
  }

  setSessionExpiredHandler(expireSession)

  async function restoreSession() {
    try {
      let state = await loadAuthCookies()
      // 上次已确认票据失效：用快照续上只读界面（最多保留 7 天），
      // 而不是每次冷启动都直接甩回登录页
      const expiredAt = readExpiredAt()
      if (expiredAt && !state?.restored && !state?.pending) {
        if (Date.now() - expiredAt <= READ_ONLY_TTL_MS) {
          const zf = new ZfAcademicApi()
          const cached = zf.applySnapshot()
          if (cached) {
            setAcademicApi(zf)
            user.value = cached
            sessionExpired.value = true
            expiredReason.value = readExpiredReason()
            const { useAcademicStore } = await import('./academic')
            useAcademicStore().applySnapshotSchedule(zf.snapshotSchedule())
            writePendingFailures(0)
            return true
          }
        } else {
          // 超过只读保留期：丢弃快照，回到全新未登录
          clearExpiredFlag()
          clearScheduleSnapshot()
        }
      }
      if (state?.pending) {
        // 连续多次冷启动都读不出会话时不再沿用快照，交给用户重新登录
        const failures = readPendingFailures() + 1
        writePendingFailures(failures)
        if (failures > MAX_PENDING_FAILURES) return false
        // 本机存有会话但解密暂时失败：先用快照保持登录态，避免冷启动被踢回登录页
        const snapshotApi = new ZfAcademicApi()
        const cached = snapshotApi.applySnapshot()
        if (cached) {
          applyZfSession(snapshotApi, cached)
          // 快照课表同步写入 academic store：首页首帧直接显示缓存，不再等网络
          const { useAcademicStore } = await import('./academic')
          useAcademicStore().applySnapshotSchedule(snapshotApi.snapshotSchedule())
          // 稍后补读一次会话，成功就静默补上实时数据；仍失败保持离线快照
          retryPendingSession().then((retried) => {
            if (!retried) return
            writePendingFailures(0)
            refreshInBackground(snapshotApi)
          })
          return true
        }
        // 没有可用快照时再等一次，仍读不出才判定未登录（不清空本地会话）
        state = await retryPendingSession()
        if (!state) return false
      }
      if (state && state.restored) {
        writePendingFailures(0)
        const zf = new ZfAcademicApi()
        const cached = zf.applySnapshot()
        if (cached) {
          applyZfSession(zf, cached)
          // 快照课表同步写入 academic store：首页首帧直接显示缓存，不再等网络
          const { useAcademicStore } = await import('./academic')
          useAcademicStore().applySnapshotSchedule(zf.snapshotSchedule())
          // 先用快照渲染首屏，实时校验放到后台，避免启动被网络请求卡住
          refreshInBackground(zf)
          return true
        }
        const profile = await restoreWithTimeout(zf)
        applyZfSession(zf, profile)
        return true
      }
    } catch (error) {
      // 服务端明确判定失效时先静默换票；换不到再按原因决定是否清理。
      // 网络不可达或解析失败时保留本机数据，避免误登出。
      if (error?.sessionExpired) {
        const result = await attemptSilentRelogin()
        if (result.ok) return true
        await clearSessionOrKeepRetry(result.reason)
      }
    }
    return false
  }

  function applyZfSession(zf, profile) {
    setAcademicApi(zf)
    user.value = profile
  }

  // 静默换票成功后把快照课表补成实时数据；刷新失败不影响「会话已恢复」这一事实
  async function refreshAfterRecovery() {
    try {
      const { useAcademicStore } = await import('./academic')
      const academic = useAcademicStore()
      await academic.initTerms()
      await academic.loadSchedule(academic.currentTerm, { force: true })
      // 断网期间降级成缓存的板块一并补成实时数据，避免会话恢复后仍停在「网络不可用」
      const stale = []
      if (academic.portalNoticesOffline) stale.push(academic.loadPortalNotices())
      if (academic.messagesOffline) stale.push(academic.loadMessages())
      if (stale.length) await Promise.all(stale)
    } catch (error) {
      // 刷新失败时保留快照，下次进入页面仍会重试
    }
  }

  // 后台静默校验：成功则刷新页面数据，会话失效则清理并退回登录页
  function refreshInBackground(zf) {
    restoreWithTimeout(zf)
      .then(async () => {
        const { useAcademicStore } = await import('./academic')
        const academic = useAcademicStore()
        // 保留快照课表，只把实时数据替换进来；不再 reset，避免首页清空后闪回加载态
        await academic.initTerms()
        await academic.loadSchedule()
      })
      .catch(async (error) => {
        if (!error?.sessionExpired) return
        const recovered = await expireSession()
        if (!recovered) return
        // 静默换票成功：把快照课表补成实时数据，页面无需跳到登录页
        await refreshAfterRecovery()
      })
  }

  // 会话挂起后由界面或回到前台触发：清掉冷却立刻再换一次票
  async function retrySession() {
    if (!user.value) return false
    silentCooldownUntil = 0
    const result = await attemptSilentRelogin()
    if (!result.ok) return false
    sessionSuspended.value = false
    await refreshAfterRecovery()
    return true
  }

  // 原生后台换票成功（回前台补保活）：会话已恢复，清掉只读标记并把缓存板块补成实时数据
  async function markSessionRecovered() {
    if (!user.value) return false
    sessionExpired.value = false
    sessionSuspended.value = false
    clearExpiredFlag()
    await refreshAfterRecovery()
    return true
  }

  // 启动时后台恢复会话，页面先挂载，路由守卫等待该结果后再决定去向
  function bootstrap() {
    if (!bootstrapPromise) {
      restoring.value = true
      bootstrapPromise = restoreSession()
        .catch(() => false)
        .finally(() => {
          restoring.value = false
        })
    }
    return bootstrapPromise
  }

  async function loginWithSso() {
    loading.value = true
    try {
      const zf = new ZfAcademicApi()
      const profile = await zf.loginWithSso()
      // 登录成功后才丢弃旧快照与缓存：失败或取消时保留只读数据，用户不会白跑一趟
      clearScheduleSnapshot()
      clearDataCaches()
      setAcademicApi(zf)
      await resetAcademic()
      user.value = profile
      justLoggedIn.value = true
      sessionSuspended.value = false
      sessionExpired.value = false
      expiredReason.value = null
      clearExpiredFlag()
      writePendingFailures(0)
      // 用户已主动完成登录，恢复自动重登资格
      manualLogout = false
      silentCooldownUntil = 0
      return profile
    } catch (error) {
      // 只有「服务端明确判定无效」或「数据无法解析」才丢弃这次 Cookie。
      // 网络超时、Keystore 暂时不可用时，刚拿到的会话仍然可用，删掉会逼用户重走统一认证。
      if (error?.sessionExpired || error?.parseError) {
        try {
          await clearAuthCookies()
        } catch (cleanupError) {
          // 清理失败不能顶替原始登录错误，否则用户看到的是无关的桥接异常
        }
      }
      if (error?.sessionExpired) throw new Error('登录未生效，请重试')
      throw error
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    // 先禁止自动重登，再清票据：即使静默换票正在途中，迟到的结果也会被丢弃
    manualLogout = true
    silentCooldownUntil = 0
    // 明确退出登录才清 CASTGC；会话失效走 clearLocalSession，保留 WebView 中的登录票据
    try {
      // 先关闭「保持登录」：清除开关状态并取消闹钟，避免重新登录后被旧偏好自动恢复
      await setKeepAlive(false)
    } catch (error) {
      // 原生取消失败不阻断退出登录
    }
    try {
      await clearSsoSession()
    } catch (error) {
      // 原生清理失败不影响退出登录本身
    }
    await clearLocalSession()
    try {
      await clearCrashLog()
    } catch (error) {
      // 崩溃摘要清理失败不阻断退出登录
    }
    await resetAcademic()
  }

  function consumeJustLoggedIn() {
    if (!justLoggedIn.value) return false
    justLoggedIn.value = false
    return true
  }

  return {
    user,
    loading,
    restoring,
    isLoggedIn,
    sessionSuspended,
    sessionExpired,
    expiredReason,
    bootstrap,
    loginWithSso,
    logout,
    retrySession,
    markSessionRecovered,
    consumeJustLoggedIn
  }
})
