import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { resetAcademicApi, setAcademicApi } from '../api'
import { ZfAcademicApi, clearScheduleSnapshot } from '../api/zf'
import { clearAuthCookies, loadAuthCookies } from '../api/nativeHttp'
import { clearScheduleChange } from '../native/scheduleChangeBridge'

const RESTORE_TIMEOUT_MS = 15000

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

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const loading = ref(false)
  const restoring = ref(false)
  const isLoggedIn = computed(() => Boolean(user.value))
  let bootstrapPromise = null

  async function resetAcademic() {
    const { useAcademicStore } = await import('./academic')
    useAcademicStore().reset()
  }

  async function restoreSession() {
    try {
      const restored = await loadAuthCookies()
      if (restored && restored.restored) {
        const zf = new ZfAcademicApi()
        const cached = zf.applySnapshot()
        if (cached) {
          applyZfSession(zf, cached)
          // 先用快照渲染首屏，实时校验放到后台，避免启动被网络请求卡住
          refreshInBackground(zf)
          return true
        }
        const profile = await restoreWithTimeout(zf)
        applyZfSession(zf, profile)
        return true
      }
    } catch (error) {
      // 仅在服务端明确判定会话失效时清除凭据；网络不可达或解析失败时保留，避免误登出
      if (error?.sessionExpired) await clearAuthCookies()
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
        academic.reset()
        await academic.initTerms()
        await academic.loadSchedule()
      })
      .catch(async (error) => {
        if (!error?.sessionExpired) return
        await clearAuthCookies()
        clearScheduleSnapshot()
        resetAcademicApi()
        user.value = null
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
      const zf = new ZfAcademicApi()
      const profile = await zf.loginWithSso()
      setAcademicApi(zf)
      await resetAcademic()
      user.value = profile
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
    await clearScheduleChange()
    await clearAuthCookies()
    clearScheduleSnapshot()
    resetAcademicApi()
    user.value = null
    await resetAcademic()
  }

  return {
    user,
    loading,
    restoring,
    isLoggedIn,
    bootstrap,
    loginWithSso,
    logout
  }
})
