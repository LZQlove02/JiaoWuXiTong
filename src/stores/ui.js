import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { Capacitor, registerPlugin } from '@capacitor/core'
import { StatusBar, Style } from '@capacitor/status-bar'

const AppTheme = registerPlugin('AppTheme')
const STORAGE_KEY = 'lzpu.theme'

export const useUiStore = defineStore('ui', () => {
  const theme = ref('system')
  const overlayCloser = ref(null)
  const hasOverlay = computed(() => Boolean(overlayCloser.value))

  function setOverlayCloser(closer) {
    overlayCloser.value = closer
  }

  function clearOverlayCloser() {
    overlayCloser.value = null
  }

  function closeOverlay() {
    const closer = overlayCloser.value
    overlayCloser.value = null
    if (typeof closer === 'function') closer()
  }

  function resolveTheme(value) {
    if (value === 'light' || value === 'dark') return value
    return window.matchMedia('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light'
  }

  async function syncStatusBar(resolved) {
    if (!Capacitor.isNativePlatform()) return
    try {
      await StatusBar.setOverlaysWebView({ overlay: false })
      await StatusBar.setBackgroundColor({
        color: resolved === 'dark' ? '#0f1216' : '#f5f6f8'
      })
      await StatusBar.setStyle({
        style: resolved === 'dark' ? Style.Dark : Style.Light
      })
    } catch (error) {
      // 状态栏在部分设备上不可控，忽略即可
    }
    try {
      // 系统导航栏不归 StatusBar 插件管理，需单独同步主题色
      await AppTheme.setNavigationBar({ dark: resolved === 'dark' })
    } catch (error) {
      // 导航栏在部分设备上不可控，忽略即可
    }
  }

  function applyTheme() {
    const resolved = resolveTheme(theme.value)
    document.documentElement.dataset.theme = resolved
    syncStatusBar(resolved)
  }

  function initTheme() {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark' || saved === 'system') {
      theme.value = saved
    }
    applyTheme()
    window
      .matchMedia('(prefers-color-scheme: dark)')
      .addEventListener('change', () => {
        if (theme.value === 'system') applyTheme()
      })
  }

  function setTheme(value) {
    theme.value = value
    localStorage.setItem(STORAGE_KEY, value)
    applyTheme()
  }

  return { theme, hasOverlay, setOverlayCloser, clearOverlayCloser, closeOverlay, initTheme, setTheme }
})
