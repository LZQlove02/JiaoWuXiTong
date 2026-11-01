<template>
  <DisclaimerGate
    v-if="!consented"
    :declined="declined"
    @accept="onAccept"
    @decline="onDecline"
  />
  <div v-else-if="auth.restoring" class="boot">
    <img class="boot-logo" src="./assets/app-icon.png" alt="" />
    <p class="boot-text">正在恢复登录状态…</p>
  </div>
  <div v-else class="app-shell">
    <main ref="mainRef" class="app-main">
      <router-view :key="$route.fullPath" />
    </main>
    <TabBar v-if="showTabBar" />
    <UpdateDialog v-if="update.dialogOpen" />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Capacitor } from '@capacitor/core'
import { App as CapApp } from '@capacitor/app'
import TabBar from './components/TabBar.vue'
import DisclaimerGate from './components/DisclaimerGate.vue'
import UpdateDialog from './components/UpdateDialog.vue'
import { useAuthStore } from './stores/auth'
import { useAcademicStore } from './stores/academic'
import { useChangesStore } from './stores/changes'
import { useScheduleSettingsStore } from './stores/scheduleSettings'
import { useUiStore } from './stores/ui'
import { useUpdateStore } from './stores/update'
import { acceptDisclaimer, isDisclaimerAccepted } from './utils/disclaimer'
import { clearWidgetNow, resyncWidget, syncWidget, syncWidgetTheme } from './widget/sync'
import { deepLinkPath } from './utils/deepLink'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const auth = useAuthStore()
const academic = useAcademicStore()
const changes = useChangesStore()
const scheduleSettings = useScheduleSettingsStore()
const update = useUpdateStore()
const showTabBar = computed(() => route.meta.tab === true)
const mainRef = ref(null)

const consented = ref(isDisclaimerAccepted())
const declined = ref(false)
let nativeReady = false

// 内容区独立滚动：记下离开页面的位置，返回时恢复；新页面从顶部开始
const scrollPositions = new Map()
watch(
  () => route.fullPath,
  (to, from) => {
    const main = mainRef.value
    if (main && from) scrollPositions.set(from, main.scrollTop)
    nextTick(() => {
      if (mainRef.value) mainRef.value.scrollTop = scrollPositions.get(to) || 0
    })
  },
  { flush: 'pre' }
)

// 系统底部导航栏颜色跟随是否有 TabBar（白色/深色卡片底色）
watch(
  () => route.meta.tab,
  (tab) => ui.setTabBarVisible(tab === true),
  { immediate: true }
)

// 同意免责声明后才恢复会话：未同意前不读 Cookie、不发请求
async function onAccept() {
  acceptDisclaimer()
  consented.value = true
  const booting = auth.bootstrap()
  setupNative()
  await router.isReady()
  await booting
  // 初始导航按「未登录」落到了登录页，恢复出会话后补一次跳转
  if (auth.isLoggedIn && router.currentRoute.value.name === 'login') {
    router.replace('/')
  }
}

function onDecline() {
  if (Capacitor.isNativePlatform()) {
    CapApp.exitApp()
    return
  }
  // Web 预览无法退出进程，给一句静态提示
  declined.value = true
}

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
    sectionTimes: scheduleSettings.times,
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
      () => scheduleSettings.times,
      () => scheduleSettings.periodCount,
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
  // 改完节次时间重新对账一次：原生基线里存的是上次传入的时段，
  // 不重传的话后台检查仍会按旧时间判断「课程是否已结束」
  watch(
    () => scheduleSettings.times,
    () => {
      if (auth.isLoggedIn) changes.applyFromSchedule(academic.schedule)
    }
  )
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

// 原生能力延迟到「已同意」之后初始化：未同意时不写组件、不注册深链与返回键监听
async function setupNative() {
  if (nativeReady) return
  nativeReady = true
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
    // 更新检查按 24h 节流，内部自行判断是否需要请求
    update.autoCheck()
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
}

// 登录态建立后（含冷启动会话恢复）检查一次更新；免责声明同意与 24h 节流在 store 内判断
watch(
  () => auth.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) update.autoCheck()
  },
  { immediate: true }
)

onMounted(() => {
  ui.initTheme()
  // 已同意（老用户）时立即初始化；首次启动等门禁页同意后再走 setupNative
  if (consented.value) setupNative()
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
