import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { Capacitor, registerPlugin } from '@capacitor/core'
import { StatusBar, Style } from '@capacitor/status-bar'

const AppTheme = registerPlugin('AppTheme')
const STORAGE_KEY = 'jiaowu.theme'

export const useUiStore = defineStore('ui', () => {
  const theme = ref('system')
  // 跟随系统时，用 WebView 的 prefers-color-scheme 作为唯一深浅色来源；
  // 桌面组件也取这个结果，避免原生 uiMode 与 App 内主题判断不一致。
  const systemDark = ref(window.matchMedia('(prefers-color-scheme: dark)').matches)
  const tabBarVisible = ref(false)
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
    return systemDark.value ? 'dark' : 'light'
  }

  // App 当前实际生效的深浅色（跟随系统时已解析成 light / dark）
  const effectiveTheme = computed(() => resolveTheme(theme.value))

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
      await AppTheme.setNavigationBar({
        dark: resolved === 'dark',
        color: navBarColor(resolved)
      })
    } catch (error) {
      // 导航栏在部分设备上不可控，忽略即可
    }
  }

  // 系统导航栏不归 StatusBar 插件管理：TabBar 通栏时用其卡片底色，
  // 其余情况与页面背景一致，避免底部出现色差条
  function navBarColor(resolved) {
    const fullWidthTab = tabBarVisible.value && window.innerWidth < 768
    if (fullWidthTab) return resolved === 'dark' ? '#171b20' : '#ffffff'
    return resolved === 'dark' ? '#0f1216' : '#f5f6f8'
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
      .addEventListener('change', (event) => {
        systemDark.value = event.matches
        if (theme.value === 'system') applyTheme()
      })
    // 横屏/平板时 TabBar 不再通栏，导航栏颜色需要重新判定
    window.addEventListener('resize', refreshNavBar)
    window
      .matchMedia('(min-width: 768px)')
      .addEventListener('change', refreshNavBar)
    window.addEventListener('orientationchange', () => {
      setTimeout(refreshNavBar, 200)
    })
  }

  function refreshNavBar() {
    if (tabBarVisible.value) syncStatusBar(resolveTheme(theme.value))
  }

  function setTheme(value) {
    theme.value = value
    localStorage.setItem(STORAGE_KEY, value)
    applyTheme()
  }

  function setTabBarVisible(value) {
    const next = Boolean(value)
    if (tabBarVisible.value === next) return
    tabBarVisible.value = next
    syncStatusBar(resolveTheme(theme.value))
  }

  return {
    theme,
    effectiveTheme,
    tabBarVisible,
    setTabBarVisible,
    hasOverlay,
    setOverlayCloser,
    clearOverlayCloser,
    closeOverlay,
    initTheme,
    setTheme
  }
})
