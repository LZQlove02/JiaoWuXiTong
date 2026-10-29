<template>
  <div v-if="auth.restoring" class="boot">
    <img class="boot-logo" src="./assets/app-icon.png" alt="" />
    <p class="boot-text">正在恢复登录状态…</p>
  </div>
  <div v-else class="app-shell">
    <router-view :key="$route.fullPath" />
    <TabBar v-if="showTabBar" />
  </div>
</template>

<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Capacitor } from '@capacitor/core'
import { App as CapApp } from '@capacitor/app'
import TabBar from './components/TabBar.vue'
import { useAuthStore } from './stores/auth'
import { useAcademicStore } from './stores/academic'
import { useUiStore } from './stores/ui'
import { clearWidgetNow, syncWidget, syncWidgetTheme } from './widget/sync'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const auth = useAuthStore()
const academic = useAcademicStore()
const showTabBar = computed(() => route.meta.tab === true)

// 桌面小组件深链：lzpu://schedule 等，与 WidgetDeepLink.java 中的路径一一对应
const DEEP_LINK_PATHS = {
  schedule: '/schedule',
  messages: '/messages',
  grades: '/grades',
  notices: '/notices'
}

function deepLinkPath(url) {
  if (!url) return ''
  // 不用 new URL()：自定义 scheme 在 WebView 里解析不可靠，正则更稳
  const text = String(url).trim()
  const match = /^lzpu:\/\/([^/?#]+)/i.exec(text) || /^lzpu:([^/?#]+)/i.exec(text)
  if (!match) return ''
  return DEEP_LINK_PATHS[match[1].toLowerCase()] || ''
}

async function openDeepLink(url) {
  const path = deepLinkPath(url)
  if (!path) return
  // 冷启动时 bootstrap 还没完，此时 isLoggedIn 必为 false，直接返回会把跳转丢掉
  if (auth.restoring) await auth.bootstrap()
  await router.isReady()
  // 未登录时由路由守卫落到登录页，这里不重复跳转，避免守卫循环
  if (!auth.isLoggedIn) return
  if (router.currentRoute.value.path === path) return
  router.push(path)
}

// 组件数据全部来自已加载的 store，不新增网络请求
function installWidgetSync() {
  syncWidgetTheme(ui.theme)
  const context = () => ({
    user: auth.user,
    currentTerm: academic.currentTerm,
    schedule: academic.schedule,
    grades: academic.grades,
    messages: academic.messages,
    portalNotices: academic.portalNotices
  })
  watch(
    [
      () => auth.user,
      () => academic.currentTerm,
      () => academic.schedule,
      () => academic.grades,
      () => academic.messages,
      () => academic.portalNotices
    ],
    // 会话恢复期间先不写盘，否则冷启动会把「请登录」闪一下再改回来
    () => {
      if (!auth.restoring) syncWidget(context)
    },
    { immediate: true }
  )
  // 恢复结束后无论登录与否都写一次，保证组件状态和 App 一致
  auth.bootstrap().finally(() => syncWidget(context))
  watch(() => ui.theme, (theme) => syncWidgetTheme(theme))
  // 4x4 的 GPA 摘要要用成绩；成绩页只在进入时才拉，这里登录后静默补一次
  watch(
    [() => auth.isLoggedIn, () => academic.currentTerm],
    ([loggedIn, term]) => {
      if (loggedIn && term && !academic.grades) academic.loadGrades(term)
    },
    { immediate: true }
  )
  // 退出登录立即清空，避免桌面残留旧课表
  watch(
    () => auth.isLoggedIn,
    (loggedIn) => {
      if (!loggedIn) clearWidgetNow()
    }
  )
}

// 后台校验发现会话失效时会清空登录态，这里统一退回登录页
watch(
  () => auth.isLoggedIn,
  (loggedIn) => {
    if (!loggedIn) router.replace('/login')
  }
)

onMounted(() => {
  ui.initTheme()
  if (!Capacitor.isNativePlatform()) return

  installWidgetSync()
  CapApp.getLaunchUrl()
    .then((result) => openDeepLink(result?.url))
    .catch(() => {})
  CapApp.addListener('appUrlOpen', (event) => {
    openDeepLink(event?.url).catch(() => {})
  })

  CapApp.addListener('backButton', () => {
    const current = router.currentRoute.value
    if (ui.hasOverlay) {
      ui.closeOverlay()
      return
    }
    if (current.meta.tab) {
      if (current.path === '/') CapApp.exitApp()
      else router.replace('/')
      return
    }
    if (current.name === 'login') {
      CapApp.exitApp()
      return
    }
    router.back()
  })
})
</script>

<style scoped>
.boot {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  background: var(--bg);
}

.boot-logo {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  display: block;
}

.boot-text {
  font-size: 13px;
  color: var(--text-secondary);
}
</style>
