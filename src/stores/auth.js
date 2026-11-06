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
import { setSessionExpiredHandler } from '../utils/sessionExpiry'
import { clearDataCaches } from '../utils/dataCache'
import { clearPortalCache } from '../utils/portalCache'

const RESTORE_TIMEOUT_MS = 15000
// 本机存有会话但解密暂时失败时，给 Keystore 一次重试机会
const SESSION_PENDING_RETRY_MS = 600

// 冷启动恢复会话最多等待 15 秒，避免弱网时首屏长时间停在启动页
function restoreWithTimeout(api) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
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
  const isLoggedIn = computed(() => Boolean(user.value))
  let bootstrapPromise = null
  let expiringPromise = null

  async function resetAcademic() {
    const { useAcademicStore } = await import('./academic')
    useAcademicStore().reset()
  }

  // 清掉本机所有会话相关数据：加密 Cookie、课表快照、变更记录、个人缓存与附件
  async function clearLocalSession() {
    await clearScheduleChange()
    await clearAuthCookies()
    clearScheduleSnapshot()
    clearDataCaches()
    clearPortalCache()
    try {
      // 附件与正文图缓存可能包含上一账号查看过的材料，退出时必须一并删除
      await Promise.all([clearAuthDownloads(), clearPublicDownloads()])
    } catch (error) {
      // 原生缓存清理失败不阻断会话清理，下次清理仍会尝试
    }
    resetAcademicApi()
    user.value = null
    justLoggedIn.value = false
  }

  // 会话失效统一出口：任一业务请求判定 401/登录页后，只清理一次并回登录页
  async function expireSession() {
    if (expiringPromise) return expiringPromise
    expiringPromise = clearLocalSession()
      .then(() => resetAcademic())
      .finally(() => {
        expiringPromise = null
      })
    return expiringPromise
  }

  setSessionExpiredHandler(expireSession)

  async function restoreSession() {
    try {
      let state = await loadAuthCookies()
      if (state?.pending) {
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
            if (retried) refreshInBackground(snapshotApi)
          })
          return true
        }
        // 没有可用快照时再等一次，仍读不出才判定未登录（不清空本地会话）
        state = await retryPendingSession()
        if (!state) return false
      }
      if (state && state.restored) {
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
      // 仅在服务端明确判定会话失效时清除本机数据；网络不可达或解析失败时保留，避免误登出
      if (error?.sessionExpired) {
        await clearLocalSession()
      }
    }
    return false
  }

  function applyZfSession(zf, profile) {
    setAcademicApi(zf)
    user.value = profile
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
        await expireSession()
      })
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
      clearScheduleSnapshot()
      clearDataCaches()
      const zf = new ZfAcademicApi()
      const profile = await zf.loginWithSso()
      setAcademicApi(zf)
      await resetAcademic()
      user.value = profile
      justLoggedIn.value = true
      return profile
    } catch (error) {
      // 登录后校验未通过时丢弃这次无效会话，避免把坏 Cookie 留到下次启动
      await clearAuthCookies()
      if (error?.sessionExpired) throw new Error('登录未生效，请重试')
      throw error
    } finally {
      loading.value = false
    }
  }

  async function logout() {
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
    bootstrap,
    loginWithSso,
    logout,
    consumeJustLoggedIn
  }
})
