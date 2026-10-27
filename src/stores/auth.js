import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '../api'

const STORAGE_KEY = 'lzpu.session'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const loading = ref(false)
  const isLoggedIn = computed(() => Boolean(user.value))

  function restore() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return
      const saved = JSON.parse(raw)
      if (saved && saved.user) user.value = saved.user
    } catch (error) {
      localStorage.removeItem(STORAGE_KEY)
    }
  }

  async function login(username, password) {
    loading.value = true
    try {
      const profile = await api.login(username, password)
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

  async function logout() {
    await api.logout()
    user.value = null
    localStorage.removeItem(STORAGE_KEY)
  }

  return { user, loading, isLoggedIn, restore, login, logout }
})
