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
import { useChangesStore } from './stores/changes'
import { useUiStore } from './stores/ui'
import { clearWidgetNow, resyncWidget, syncWidget, syncWidgetTheme } from './widget/sync'
import { deepLinkPath } from './utils/deepLink'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const auth = useAuthStore()
const academic = useAcademicStore()
const changes = useChangesStore()
const showTabBar = computed(() => route.meta.tab === true)

// 深链解析在 src/utils/deepLink.js，这里只负责跳转
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
    portalNotices: academic.portalNotices,
    changeUnread: changes.unread,
    changeEnabled: changes.enabled,
    changeKnown: changes.loaded && !changes.error
  })
  watch(
    [
      () => auth.user,
      () => academic.currentTerm,
      () => academic.schedule,
      () => academic.grades,
      () => academic.messages,
      () => academic.portalNotices,
      () => changes.unread,
      () => changes.enabled
    ],
    // 会话恢复期间先不写盘，否则冷启动会把「请登录」闪一下再改回来
    () => {
      if (!auth.restoring) syncWidget(context)
    },
    { immediate: true }
  )
  // 恢复结束后无论登录与否都写一次，保证组件状态和 App 一致
  auth.bootstrap().finally(() => syncWidget(context))
  // 热启动回到前台：原生可能已把 payload 写成 stale（跨周降级），此时 store 没变、
  // 普通同步会跳过，这里强制重写一次，组件才会从「打开 App 同步课表」恢复。
  // 会话恢复期间不能写，否则冷启动会先写一份 loggedIn=false，桌面闪一下「请登录」
  CapApp.addListener('appStateChange', ({ isActive }) => {
    if (isActive && !auth.restoring) resyncWidget(context)
  })
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
      if (!loggedIn) {
        clearWidgetNow()
        changes.refresh()
      } else {
        changes.init()
      }
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

onMounted(async () => {
  ui.initTheme()
  if (!Capacitor.isNativePlatform()) return

  // 先读出变更状态再首次写组件 payload，避免用未加载的 0 覆盖原生角标
  await changes.init()
  installWidgetSync()
  changes.listen()
  CapApp.addListener('appStateChange', ({ isActive }) => {
    if (!isActive) return
    changes.refresh()
    // 切回 App 也重新对一次账，不能只读原生旧状态
    if (auth.isLoggedIn) academic.loadSchedule(academic.currentTerm)
  })
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
