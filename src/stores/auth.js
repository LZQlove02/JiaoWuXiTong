import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api, resetAcademicApi, setAcademicApi } from '../api'
import { ZfAcademicApi } from '../api/zf'
import { clearAuthCookies, loadAuthCookies } from '../api/nativeHttp'

const STORAGE_KEY = 'lzpu.session'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const loading = ref(false)
  const sessionMode = ref('mock')
  const isLoggedIn = computed(() => Boolean(user.value))

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
      const restored = await loadAuthCookies()
      if (restored && restored.restored) {
        const zf = new ZfAcademicApi()
        const profile = await zf.restoreSession()
        setAcademicApi(zf)
        const { useAcademicStore } = await import('./academic')
        useAcademicStore().reset()
        user.value = profile
        sessionMode.value = 'sso'
        return true
      }
    } catch (error) {
      await clearAuthCookies()
    }
    restore()
    return isLoggedIn.value
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
    resetAcademicApi()
    sessionMode.value = 'mock'
    user.value = null
    localStorage.removeItem(STORAGE_KEY)
  }

  return {
    user,
    loading,
    sessionMode,
    isLoggedIn,
    restore,
    restoreSession,
    login,
    loginWithSso,
    logout
  }
})
