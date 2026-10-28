import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api, resetAcademicApi, setAcademicApi } from '../api'
import { ZfAcademicApi, clearScheduleSnapshot } from '../api/zf'
import { clearAuthCookies, loadAuthCookies } from '../api/nativeHttp'

const STORAGE_KEY = 'lzpu.session'
const RESTORE_TIMEOUT_MS = 15000
const COOKIE_SCOPE_KEY = 'lzpu.auth.cookieScope'
const COOKIE_SCOPE_VERSION = '2'

// 旧版本把统一认证 Cookie 一并加密保存，升级后清空一次，强制重新登录以丢弃跨域票据
async function migrateCookieScope() {
  if (localStorage.getItem(COOKIE_SCOPE_KEY) === COOKIE_SCOPE_VERSION) return
  await clearAuthCookies()
  localStorage.setItem(COOKIE_SCOPE_KEY, COOKIE_SCOPE_VERSION)
}

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
  const sessionMode = ref('mock')
  const restoring = ref(false)
  const isLoggedIn = computed(() => Boolean(user.value))
  let bootstrapPromise = null

  function restore() {
    sessionMode.value = 'mock'
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return
      const saved = JSON.parse(raw)
      if (saved && saved.user) user.value = saved.user
    } catch (error) {
      localStorage.removeItem(STORAGE_KEY)
    }
  }

  async function restoreSession() {
    try {
      await migrateCookieScope()
      const restored = await loadAuthCookies()
      if (restored && restored.restored) {
        const zf = new ZfAcademicApi()
        const profile = await restoreWithTimeout(zf)
        setAcademicApi(zf)
        const { useAcademicStore } = await import('./academic')
        useAcademicStore().reset()
        user.value = profile
        sessionMode.value = 'sso'
        return true
      }
    } catch (error) {
      // 仅在服务端明确判定会话失效时清除凭据；网络不可达或解析失败时保留，避免误登出
      if (error?.sessionExpired) await clearAuthCookies()
    }
    restore()
    return isLoggedIn.value
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

  async function login(username, password) {
    loading.value = true
    try {
      resetAcademicApi()
      const profile = await api.login(username, password)
      sessionMode.value = 'mock'
      user.value = profile
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ user: profile, loginAt: Date.now() })
      )
      return profile
    } finally {
      loading.value = false
    }
  }

  async function loginWithSso() {
    loading.value = true
    try {
      clearScheduleSnapshot()
      const zf = new ZfAcademicApi()
      const profile = await zf.loginWithSso()
      setAcademicApi(zf)
      const { useAcademicStore } = await import('./academic')
      useAcademicStore().reset()
      user.value = profile
      sessionMode.value = 'sso'
      localStorage.removeItem(STORAGE_KEY)
      return profile
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    await clearAuthCookies()
    clearScheduleSnapshot()
    resetAcademicApi()
    sessionMode.value = 'mock'
    user.value = null
    localStorage.removeItem(STORAGE_KEY)
  }

  return {
    user,
    loading,
    sessionMode,
    restoring,
    isLoggedIn,
    restore,
    restoreSession,
    bootstrap,
    login,
    loginWithSso,
    logout
  }
})
