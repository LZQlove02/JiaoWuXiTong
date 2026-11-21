<template>
  <ConsentGate
    v-if="stage === 'disclaimer'"
    :clauses="DISCLAIMER_CLAUSES"
    title="使用前请阅读"
    subtitle="教务助手 · 免责声明"
    :version-text="`声明版本 v${DISCLAIMER_VERSION} · ${DISCLAIMER_DATE}`"
    :declined="declined"
    @accept="onAcceptDisclaimer"
    @decline="onDecline"
  />
  <ConsentGate
    v-else-if="stage === 'privacy'"
    :clauses="PRIVACY_SECTIONS"
    title="隐私政策"
    subtitle="教务助手 · 个人信息处理规则"
    :version-text="`v${PRIVACY_VERSION} · ${PRIVACY_DATE}`"
    @accept="onAcceptPrivacy"
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
    <!-- 会话暂时不可用：数据仍在，提示用户可以用一次点击重试，而不是被踢回登录页 -->
    <div
      v-if="auth.sessionSuspended && auth.isLoggedIn"
      class="session-bar"
      role="status"
      aria-live="polite"
    >
      <span class="session-bar-text">网络不稳定，会话暂时不可用，已保留本地数据</span>
      <button
        class="session-bar-action"
        type="button"
        :disabled="sessionRetrying"
        @click="onRetrySession"
      >
        {{ sessionRetrying ? '重试中…' : '重试' }}
      </button>
    </div>
    <ConsentGate
      v-if="keepAlivePromptOpen"
      :clauses="KEEPALIVE_CLAUSES"
      title="保持登录"
      subtitle="教务助手 · 后台会话保活说明"
      :version-text="`说明版本 v${KEEPALIVE_CONSENT_VERSION}`"
      primary-text="保持开启"
      decline-text="关闭"
      @accept="onKeepAliveAccept"
      @decline="onKeepAliveDecline"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Capacitor } from '@capacitor/core'
import { App as CapApp } from '@capacitor/app'
import TabBar from './components/TabBar.vue'
import ConsentGate from './components/ConsentGate.vue'
import UpdateDialog from './components/UpdateDialog.vue'
import { useAuthStore } from './stores/auth'
import { useAcademicStore } from './stores/academic'
import { useChangesStore } from './stores/changes'
import { useGradeAlertStore } from './stores/gradeAlert'
import { usePortalAlertStore } from './stores/portalAlert'
import { useScheduleSettingsStore } from './stores/scheduleSettings'
import { useUiStore } from './stores/ui'
import { useUpdateStore } from './stores/update'
import { setKeepAlive } from './api/nativeHttp'
import { isBackgroundConsentAccepted } from './utils/backgroundConsent'
import {
  KEEPALIVE_CLAUSES,
  KEEPALIVE_CONSENT_VERSION,
  acceptKeepAliveConsent,
  declineKeepAliveConsent,
  isKeepAliveConsentAccepted,
  isKeepAliveConsentDeclined
} from './utils/keepAliveConsent'
import {
  DISCLAIMER_CLAUSES,
  DISCLAIMER_DATE,
  DISCLAIMER_VERSION,
  acceptDisclaimer,
  isDisclaimerAccepted
} from './utils/disclaimer'
import {
  PRIVACY_DATE,
  PRIVACY_SECTIONS,
  PRIVACY_VERSION,
  acceptPrivacy,
  isPrivacyAccepted
} from './utils/privacy'
import { pruneLocalData } from './utils/retention'
import { clearWidgetNow, resyncWidget, syncWidget, syncWidgetTheme } from './widget/sync'
import { deepLinkPath } from './utils/deepLink'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const auth = useAuthStore()
const academic = useAcademicStore()
const changes = useChangesStore()
const gradeAlert = useGradeAlertStore()
const portalAlert = usePortalAlertStore()
const scheduleSettings = useScheduleSettingsStore()
const update = useUpdateStore()
const showTabBar = computed(() => route.meta.tab === true)
const mainRef = ref(null)

function initialStage() {
  if (!isDisclaimerAccepted()) return 'disclaimer'
  if (!isPrivacyAccepted()) return 'privacy'
  return 'ready'
}

// 首启顺序：免责声明 → 隐私政策 → 恢复会话；每一步单独同意
const stage = ref(initialStage())
const consented = computed(() => stage.value === 'ready')
const declined = ref(false)
const keepAlivePromptOpen = ref(false)
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

// 两份文本都同意后才恢复会话：未同意前不读 Cookie、不发请求
async function startApp() {
  pruneLocalData()
  const booting = auth.bootstrap()
  setupNative()
  await router.isReady()
  await booting
  // 初始导航按「未登录」落到了登录页，恢复出会话后补一次跳转
  if (auth.isLoggedIn && router.currentRoute.value.name === 'login') {
    router.replace('/')
  }
}

async function onAcceptDisclaimer() {
  acceptDisclaimer()
  // 老用户已同意过隐私政策时直接进入；否则继续展示隐私政策
  if (isPrivacyAccepted()) {
    stage.value = 'ready'
    await startApp()
    return
  }
  stage.value = 'privacy'
}

async function onAcceptPrivacy() {
  acceptPrivacy()
  stage.value = 'ready'
  await startApp()
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
  // 只下发已解析的 light / dark：组件不再按原生 uiMode 自行判断深浅色，
  // 避免 App 关闭期间系统切深色后，组件在闹钟或任意刷新时突然换字色
  syncWidgetTheme(ui.effectiveTheme)
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
  // 跟随系统时，系统深浅色切换会改变 effectiveTheme，组件与 App 同步更新
  watch(() => ui.effectiveTheme, (theme) => syncWidgetTheme(theme))
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

// 会话暂时不可用时的手动重试：成功后刷新页面数据，失败则保留提示条等下一次
const sessionRetrying = ref(false)
async function onRetrySession() {
  if (sessionRetrying.value) return
  sessionRetrying.value = true
  try {
    const recovered = await auth.retrySession()
    if (recovered) changes.refresh()
  } finally {
    sessionRetrying.value = false
  }
}

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
    // 会话暂时不可用时，回到前台立刻再换一次票，弱网恢复后用户无需手动点重试
    if (auth.sessionSuspended) auth.retrySession().catch(() => {})
    // 切回 App 也重新对一次账，不能只读原生旧状态
    if (auth.isLoggedIn) {
      academic.loadSchedule(academic.currentTerm)
      // 上次因断网降级成缓存的板块，回前台补拉一次，否则会一直停在「网络不可用」
      if (academic.portalNoticesOffline) academic.loadPortalNotices()
      if (academic.messagesOffline) academic.loadMessages()
    }
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

// 子同意规则升版后，未重新阅读并同意前先停用后台能力，避免旧同意继续生效
async function enforceSubConsents() {
  if (!isBackgroundConsentAccepted()) {
    await Promise.allSettled([
      changes.setEnabled(false),
      gradeAlert.setEnabled(false),
      portalAlert.setEnabled('notices', false),
      portalAlert.setEnabled('messages', false)
    ])
  }
  if (!isKeepAliveConsentAccepted()) {
    try {
      await setKeepAlive(false)
    } catch (error) {
      // 原生取消失败时由接收器在无会话场景下兜底
    }
  }
}

// 保持登录默认开启：已同意且未手动关闭时登录后自动恢复；未同意且未拒绝过时首次登录展示说明
function syncKeepAliveDefault() {
  if (stage.value !== 'ready' || auth.restoring || !auth.isLoggedIn) return
  if (isKeepAliveConsentDeclined()) return
  if (isKeepAliveConsentAccepted()) {
    setKeepAlive(true).catch(() => {
      // 原生开启失败时保持关闭，用户可在账号与设备页重试
    })
    return
  }
  keepAlivePromptOpen.value = true
}

async function onKeepAliveAccept() {
  acceptKeepAliveConsent()
  keepAlivePromptOpen.value = false
  try {
    await setKeepAlive(true)
  } catch (error) {
    // 原生开启失败时保持关闭，用户可在账号与设备页重试
  }
}

async function onKeepAliveDecline() {
  declineKeepAliveConsent()
  keepAlivePromptOpen.value = false
  try {
    await setKeepAlive(false)
  } catch (error) {
    // 原生取消失败时由接收器在无会话场景下兜底
  }
}

watch(
  [() => auth.isLoggedIn, () => auth.restoring, stage],
  () => syncKeepAliveDefault(),
  { immediate: true }
)

onMounted(() => {
  ui.initTheme()
  // 子同意版本不匹配时先停用后台检查与保活，再进入原有启动流程
  enforceSubConsents()
  // 已同意（老用户）时立即初始化；超期缓存已在 main.js 恢复会话前清理
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

/* 会话挂起提示条：浮在 TabBar 之上，避免遮挡底部导航 */
.session-bar {
  position: fixed;
  left: 12px;
  right: 12px;
  bottom: calc(env(safe-area-inset-bottom) + var(--tab-height) + 10px);
  z-index: 60;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px 10px 14px;
  border-radius: var(--radius);
  border-left: 3px solid var(--warning);
  background: var(--card);
  box-shadow: var(--shadow);
}

.session-bar-text {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 1.4;
  color: var(--text-secondary);
}

.session-bar-action {
  flex: none;
  padding: 4px 8px;
  border: 0;
  border-radius: 8px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 13px;
  font-weight: 600;
}

.session-bar-action:disabled {
  opacity: 0.6;
}
</style>
