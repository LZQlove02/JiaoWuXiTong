<template>
  <div class="page page--plain">
    <BackBar title="设置" />

    <h2 class="section-title">外观</h2>
    <section class="card">
      <div class="theme-row">
        <button
          v-for="option in themeOptions"
          :key="option.value"
          class="theme-btn"
          :class="{ 'is-active': ui.theme === option.value }"
          @click="ui.setTheme(option.value)"
        >
          {{ option.label }}
        </button>
      </div>
    </section>

    <h2 class="section-title">课表变更提醒</h2>
    <section class="card settings">
      <div class="setting-row">
        <span class="setting-left"><AppIcon name="notice" />课表变更提醒</span>
        <button
          class="switch"
          :class="{ 'is-on': changes.enabled }"
          role="switch"
          :aria-checked="changes.enabled"
          aria-label="课表变更提醒"
          @click="toggleChanges"
        >
          <span class="switch-dot"></span>
        </button>
      </div>
      <p class="setting-note">
        每天自动检查两次（默认 07:10、12:50，可在下方修改；系统省电策略下可能略有延迟）；只提醒本周的停课、调课与新增。默认关闭，开启前会说明后台检查范围；系统清理后台后，重新打开 App 会自动恢复检查。
      </p>
      <button class="setting-row" @click="openNotificationSettings">
        <span class="setting-left">通知权限</span>
        <span class="tiny">{{ changes.notificationsEnabled ? '已开启' : '系统已关闭' }}</span>
      </button>
      <router-link class="setting-row" to="/changes">
        <span class="setting-left">查看变更记录</span>
        <AppIcon name="chevron-right" />
      </router-link>
    </section>

    <h2 class="section-title">课表时间</h2>
    <section class="card settings">
      <router-link class="setting-row" to="/schedule-settings">
        <span class="setting-left"><AppIcon name="calendar" />节次时间与每日节数</span>
        <span class="tiny">{{ scheduleSettings.periodCount }} 节</span>
        <AppIcon name="chevron-right" />
      </router-link>
      <p class="setting-note">
        逐节修改起止时间；课表、桌面组件与变更提醒同步使用。有更晚的课会自动扩展显示。
      </p>
    </section>

    <h2 class="section-title">成绩提醒</h2>
    <section class="card settings">
      <div class="setting-row">
        <span class="setting-left"><AppIcon name="book" />成绩发布提醒</span>
        <button
          class="switch"
          :class="{ 'is-on': gradeAlert.enabled }"
          role="switch"
          :aria-checked="gradeAlert.enabled"
          aria-label="成绩发布提醒"
          @click="toggleGradeAlert"
        >
          <span class="switch-dot"></span>
        </button>
      </div>
      <p class="setting-note">
        仅在新出现不合格成绩时提醒；新成绩全部合格不提醒。首次开启只建立基线，不补发历史成绩。
      </p>
    </section>

    <h2 class="section-title">通知与消息提醒</h2>
    <section class="card settings">
      <div class="setting-row">
        <span class="setting-left"><AppIcon name="notice" />新通知提醒</span>
        <button
          class="switch"
          :class="{ 'is-on': portalAlert.noticesEnabled }"
          role="switch"
          :aria-checked="portalAlert.noticesEnabled"
          aria-label="新通知提醒"
          @click="togglePortalAlert('notices')"
        >
          <span class="switch-dot"></span>
        </button>
      </div>
      <div class="setting-row">
        <span class="setting-left"><AppIcon name="message" />新消息提醒</span>
        <button
          class="switch"
          :class="{ 'is-on': portalAlert.messagesEnabled }"
          role="switch"
          :aria-checked="portalAlert.messagesEnabled"
          aria-label="新消息提醒"
          @click="togglePortalAlert('messages')"
        >
          <span class="switch-dot"></span>
        </button>
      </div>
      <p class="setting-note">
        只提醒检查时出现的新内容；首次开启静默建立基线，锁屏只显示「教务有更新」。
      </p>
    </section>

    <h2 class="section-title">提醒时间</h2>
    <section class="card settings">
      <div class="time-row">
        <label class="time-field">
          <span>上午</span>
          <input v-model="morningTime" type="time" />
        </label>
        <label class="time-field">
          <span>中午</span>
          <input v-model="noonTime" type="time" />
        </label>
      </div>
      <div class="time-actions">
        <button class="time-save" type="button" :disabled="savingTimes" @click="saveTimes">
          保存
        </button>
        <button class="time-reset" type="button" :disabled="savingTimes" @click="resetTimes">
          恢复默认
        </button>
      </div>
      <p class="setting-note">
        两个时段间隔至少 1 小时，课表变更与成绩提醒共用；保存后立即重排后台检查。
      </p>
    </section>

    <h2 class="section-title">桌面组件</h2>
    <section class="card settings">
      <template v-if="widgets.length">
        <div v-for="widget in widgets" :key="widget.id" class="setting-row">
          <span class="setting-left"><AppIcon name="calendar" />{{ widget.label }}</span>
          <button class="widget-style" type="button" @click="cycleWidgetStyle(widget)">
            {{ styleLabel(widget.style) }}
          </button>
        </div>
        <button class="setting-row" type="button" @click="onRefreshWidgets">
          <span class="setting-left">刷新全部组件</span>
          <AppIcon name="refresh" />
        </button>
        <p class="setting-note">样式只影响对应组件实例；添加或删除请长按桌面空白处操作。</p>
      </template>
      <p v-else class="setting-note">暂无桌面组件。长按桌面空白处添加「柳职教务」组件后，可在这里统一调整样式和刷新。</p>
    </section>

    <h2 class="section-title">隐私与数据</h2>
    <section class="card settings">
      <router-link class="setting-row" to="/privacy">
        <span class="setting-left"><AppIcon name="shield" />隐私政策</span>
        <span class="setting-right">
          <span class="tiny">v{{ privacyVersion }}</span>
          <AppIcon name="chevron-right" />
        </span>
      </router-link>
      <router-link class="setting-row" to="/licenses">
        <span class="setting-left"><AppIcon name="info" />开源许可</span>
        <AppIcon name="chevron-right" />
      </router-link>
      <button class="setting-row" @click="openDisclaimer">
        <span class="setting-left"><AppIcon name="notice" />免责声明</span>
        <span class="tiny">v{{ disclaimerVersion }}</span>
      </button>
      <button class="setting-row" @click="askClearData">
        <span class="setting-left"><AppIcon name="trash" />清除本机个人数据</span>
        <AppIcon name="chevron-right" />
      </button>
      <button class="setting-row" @click="askWithdrawPrivacy">
        <span class="setting-left"><AppIcon name="shield" />撤回隐私同意</span>
        <AppIcon name="chevron-right" />
      </button>
      <p class="setting-note">
        清除本机个人数据会退出登录并删除本机缓存；撤回隐私同意还会停止处理，下次启动需重新阅读并同意。
      </p>
    </section>

    <h2 class="section-title">通用</h2>
    <section class="card settings">
      <div class="setting-row">
        <span class="setting-left"><AppIcon name="info" />本地缓存</span>
        <span class="tiny">{{ storageLabel }}</span>
      </div>
      <button class="setting-row" @click="clearCache">
        <span class="setting-left"><AppIcon name="trash" />清除缓存</span>
        <AppIcon name="chevron-right" />
      </button>
      <button class="setting-row" :disabled="update.checking" @click="checkUpdate">
        <span class="setting-left"><AppIcon name="refresh" />检查更新</span>
        <span class="tiny">{{ updateLabel }}</span>
      </button>
      <router-link class="setting-row" to="/tutorial">
        <span class="setting-left"><AppIcon name="guide" />使用教程</span>
        <AppIcon name="chevron-right" />
      </router-link>
      <button class="setting-row" @click="showAbout">
        <span class="setting-left"><AppIcon name="info" />关于</span>
        <span v-if="update.currentVersion" class="tiny">v{{ update.currentVersion }}</span>
      </button>
      <router-link class="setting-row" to="/feedback">
        <span class="setting-left"><AppIcon name="mail" />意见反馈</span>
        <AppIcon name="chevron-right" />
      </router-link>
    </section>

    <transition name="fade">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>

    <ConsentGate
      v-if="disclaimerOpen"
      mode="reader"
      :clauses="DISCLAIMER_CLAUSES"
      title="使用前请阅读"
      subtitle="柳职教务 · 免责声明"
      :version-text="`声明版本 v${DISCLAIMER_VERSION} · ${DISCLAIMER_DATE}`"
      @close="closeDisclaimer"
    />
    <ConsentGate
      v-if="backgroundOpen"
      :clauses="BACKGROUND_CLAUSES"
      title="开启前请阅读"
      subtitle="柳职教务 · 后台检查说明"
      :version-text="`说明版本 v${BACKGROUND_CONSENT_VERSION}`"
      primary-text="同意并开启"
      decline-text="取消"
      @accept="onBackgroundAccept"
      @decline="onBackgroundDecline"
    />
    <ConfirmDialog
      v-if="confirm"
      :title="confirm.title"
      :message="confirm.message"
      :confirm-text="confirm.confirmText"
      :danger="confirm.danger"
      @confirm="runConfirm"
      @cancel="confirm = null"
    />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { App as CapApp } from '@capacitor/app'
import { useRouter } from 'vue-router'
import { useUiStore } from '../stores/ui'
import { useAuthStore } from '../stores/auth'
import { useChangesStore } from '../stores/changes'
import { useGradeAlertStore } from '../stores/gradeAlert'
import { usePortalAlertStore } from '../stores/portalAlert'
import { useScheduleSettingsStore } from '../stores/scheduleSettings'
import { useUpdateStore } from '../stores/update'
import { clearAuthDownloads, clearPublicDownloads } from '../api/nativeHttp'
import { getStorageInfo } from '../native/supportBridge'
import {
  listWidgets,
  refreshWidgets as refreshWidgetsNative,
  setWidgetStyle
} from '../native/widgetBridge'
import { DISCLAIMER_CLAUSES, DISCLAIMER_DATE, DISCLAIMER_VERSION } from '../utils/disclaimer'
import { PRIVACY_VERSION, revokePrivacy } from '../utils/privacy'
import {
  BACKGROUND_CLAUSES,
  BACKGROUND_CONSENT_VERSION,
  acceptBackgroundConsent,
  isBackgroundConsentAccepted,
  revokeBackgroundConsent
} from '../utils/backgroundConsent'
import { estimateLocalStorageBytes, formatBytes } from '../utils/storage'
import AppIcon from '../components/AppIcon.vue'
import BackBar from '../components/BackBar.vue'
import ConsentGate from '../components/ConsentGate.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'

const ui = useUiStore()
const auth = useAuthStore()
const changes = useChangesStore()
const gradeAlert = useGradeAlertStore()
const portalAlert = usePortalAlertStore()
const scheduleSettings = useScheduleSettingsStore()
const update = useUpdateStore()
const router = useRouter()

const themeOptions = [
  { value: 'system', label: '跟随系统' },
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' }
]

const toast = ref('')
const disclaimerOpen = ref(false)
const disclaimerVersion = DISCLAIMER_VERSION
const privacyVersion = PRIVACY_VERSION
const backgroundOpen = ref(false)
const pendingAlert = ref('')
const confirm = ref(null)
const morningTime = ref(changes.checkTimes.morning)
const noonTime = ref(changes.checkTimes.noon)
const savingTimes = ref(false)
const localBytes = ref(0)
const nativeBytes = ref(0)
const widgets = ref([])
let widgetRetryTimer = null
let appStateListener = null
let disposed = false

const storageLabel = computed(() => formatBytes(localBytes.value + nativeBytes.value))
const widgetStyleLabels = {
  glass: '玻璃',
  transparent: '透明',
  opaque: '不透明'
}

function styleLabel(style) {
  return widgetStyleLabels[style] || '玻璃'
}

async function refreshStorage() {
  localBytes.value = estimateLocalStorageBytes()
  try {
    const info = await getStorageInfo()
    nativeBytes.value = Number(info?.totalBytes) || 0
  } catch (error) {
    nativeBytes.value = 0
  }
}

async function loadWidgets() {
  widgets.value = await listWidgets()
}

async function cycleWidgetStyle(widget) {
  const order = ['glass', 'transparent', 'opaque']
  const current = order.indexOf(widget.style)
  const next = order[(current + 1) % order.length]
  try {
    await setWidgetStyle(widget.id, next)
    widget.style = next
  } catch (error) {
    showToast(error?.message || '组件样式修改失败')
  }
}

async function onRefreshWidgets() {
  try {
    await refreshWidgetsNative()
    await loadWidgets()
    showToast('已刷新桌面组件')
  } catch (error) {
    showToast(error?.message || '桌面组件刷新失败')
  }
}

watch(
  () => [changes.checkTimes.morning, changes.checkTimes.noon],
  ([morning, noon]) => {
    morningTime.value = morning
    noonTime.value = noon
  },
  { immediate: true }
)

const updateLabel = computed(() => {
  if (update.checking) return '检查中…'
  if (update.latest?.tag) return `最新 ${update.latest.tag}`
  return ''
})

function showToast(message) {
  toast.value = message
  setTimeout(() => {
    toast.value = ''
  }, 1600)
}

// 只读重读声明，不改变同意状态；返回键交给 ui store 关闭浮层
function openDisclaimer() {
  disclaimerOpen.value = true
  ui.setOverlayCloser(closeDisclaimer)
}

function closeDisclaimer() {
  disclaimerOpen.value = false
  ui.clearOverlayCloser()
}

onBeforeUnmount(() => {
  disposed = true
  if (disclaimerOpen.value) ui.clearOverlayCloser()
  clearTimeout(widgetRetryTimer)
  widgetRetryTimer = null
  if (appStateListener) {
    appStateListener.remove()
    appStateListener = null
  }
})

// 清理范围：公开内容缓存 + 个人数据缓存 + 已下载附件/图片，不动登录态与主题
async function clearCache() {
  Object.keys(localStorage)
    .filter(
      (key) =>
        key.startsWith('lzpu.cache') ||
        key.startsWith('lzpu.portal.v1.') ||
        key.startsWith('lzpu.data.v1.')
    )
    .forEach((key) => localStorage.removeItem(key))
  try {
    await Promise.all([clearAuthDownloads(), clearPublicDownloads()])
  } catch (error) {
    // 原生缓存清理失败不影响 localStorage 清理结果
  }
  await refreshStorage()
  showToast('已清除本地缓存')
}

function showAbout() {
  router.push('/about')
}

async function checkUpdate() {
  const status = await update.checkNow()
  const messages = {
    latest: '已是最新版本',
    empty: '发布仓库暂未发布版本',
    error: update.error || '检查更新失败',
    busy: '正在检查，请稍候'
  }
  if (status === 'update') return
  showToast(messages[status] || '')
}

async function toggleChanges() {
  const next = !changes.enabled
  if (next) {
    if (!isBackgroundConsentAccepted()) {
      askBackgroundConsent('changes')
      return
    }
    await enableChanges()
    return
  }
  await changes.setEnabled(false)
  showToast('已关闭课表变更提醒')
}

async function enableChanges() {
  try {
    const granted = await changes.requestNotificationPermission()
    await changes.setEnabled(true)
    showToast(granted ? '已开启课表变更提醒' : '已开启（系统通知未授权）')
  } catch (error) {
    showToast(error?.message || '课表变更提醒设置失败')
  }
}

function openNotificationSettings() {
  changes.openNotificationSettings()
}

async function toggleGradeAlert() {
  const next = !gradeAlert.enabled
  if (next) {
    if (!isBackgroundConsentAccepted()) {
      askBackgroundConsent('grades')
      return
    }
    await enableGradeAlert()
    return
  }
  try {
    await gradeAlert.setEnabled(false)
    showToast('已关闭成绩提醒')
  } catch (error) {
    showToast(error?.message || '成绩提醒设置失败')
  }
}

async function enableGradeAlert() {
  try {
    const granted = await changes.requestNotificationPermission()
    await gradeAlert.setEnabled(true)
    showToast(granted ? '已开启成绩提醒' : '已开启（系统通知未授权）')
  } catch (error) {
    showToast(error?.message || '成绩提醒设置失败')
  }
}

async function togglePortalAlert(kind) {
  const next = kind === 'notices' ? !portalAlert.noticesEnabled : !portalAlert.messagesEnabled
  if (next && !isBackgroundConsentAccepted()) {
    askBackgroundConsent(kind)
    return
  }
  try {
    if (next) {
      await enablePortalAlert(kind)
      return
    }
    await portalAlert.setEnabled(kind, false)
    showToast('已关闭提醒')
  } catch (error) {
    showToast(error?.message || '提醒设置失败')
  }
}

async function enablePortalAlert(kind) {
  try {
    await changes.requestNotificationPermission()
    await portalAlert.setEnabled(kind, true)
    showToast('已开启提醒')
  } catch (error) {
    showToast(error?.message || '提醒设置失败')
  }
}

function askBackgroundConsent(action) {
  pendingAlert.value = action
  backgroundOpen.value = true
}

async function onBackgroundAccept() {
  acceptBackgroundConsent()
  backgroundOpen.value = false
  const action = pendingAlert.value
  pendingAlert.value = ''
  if (action === 'changes') await enableChanges()
  else if (action === 'grades') await enableGradeAlert()
  else if (action === 'notices' || action === 'messages') await enablePortalAlert(action)
}

function onBackgroundDecline() {
  backgroundOpen.value = false
  pendingAlert.value = ''
}

function askClearData() {
  confirm.value = {
    title: '清除本机个人数据',
    message: '将退出登录并删除本机保存的会话、课表、成绩、通知缓存与桌面组件数据。学校服务器上的数据不受影响。',
    confirmText: '清除并退出',
    danger: true,
    action: 'clear'
  }
}

function askWithdrawPrivacy() {
  confirm.value = {
    title: '撤回隐私同意',
    message: '撤回后将停止处理并清除本机个人数据，下次启动需重新阅读并同意隐私政策。',
    confirmText: '撤回并清除',
    danger: true,
    action: 'withdraw'
  }
}

async function runConfirm() {
  const action = confirm.value?.action
  confirm.value = null
  if (!action) return
  try {
    await auth.logout()
    if (action === 'withdraw') {
      revokePrivacy()
      revokeBackgroundConsent()
      window.location.reload()
      return
    }
    router.replace('/login')
    showToast('已清除本机个人数据')
  } catch (error) {
    showToast(error?.message || '操作失败，请重试')
  }
}

async function saveTimes() {
  if (!morningTime.value || !noonTime.value) {
    showToast('请先选择两个检查时间')
    return
  }
  savingTimes.value = true
  try {
    await changes.updateCheckTimes(morningTime.value, noonTime.value)
    showToast('检查时间已更新')
  } catch (error) {
    showToast(error?.message || '检查时间无效')
  } finally {
    savingTimes.value = false
  }
}

async function resetTimes() {
  savingTimes.value = true
  try {
    await changes.restoreDefaultCheckTimes()
    showToast('已恢复默认时间')
  } catch (error) {
    showToast(error?.message || '恢复默认失败')
  } finally {
    savingTimes.value = false
  }
}

onMounted(async () => {
  changes.init()
  gradeAlert.init()
  portalAlert.init()
  refreshStorage()
  loadWidgets()
  // 组件刚添加/系统绑定有延迟时，首屏可能少列；短延时再读一次
  widgetRetryTimer = setTimeout(loadWidgets, 800)
  try {
    const listener = await CapApp.addListener('appStateChange', ({ isActive }) => {
      if (isActive) loadWidgets()
    })
    // 页面在监听注册完成前已卸载时立即移除，避免泄漏
    if (disposed) {
      listener.remove()
    } else {
      appStateListener = listener
    }
  } catch (error) {
    // Web 预览或无原生监听时忽略
  }
  await update.loadVersion()
})
</script>

<style scoped>
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-secondary);
  margin: 20px 0 10px;
}

.theme-row {
  display: flex;
  gap: 8px;
}

.theme-btn {
  flex: 1;
  padding: 10px 0;
  border-radius: 10px;
  background: var(--card-muted);
  color: var(--text-secondary);
  font-size: 14px;
}

.theme-btn.is-active {
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 600;
}

.settings {
  padding: 4px 16px;
}

.setting-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border);
  color: var(--text-tertiary);
}

.setting-row:last-child {
  border-bottom: none;
}

.setting-left {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  color: var(--text);
}

.setting-right {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.setting-note {
  padding: 0 0 12px;
  border-bottom: 1px solid var(--border);
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-tertiary);
}

.widget-style {
  flex: none;
  padding: 6px 10px;
  border-radius: 8px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 12px;
}

.time-row {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}

.time-field {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.time-field input {
  flex: 1;
  min-width: 0;
  padding: 7px 8px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--card-muted);
  color: var(--text);
  font-size: 14px;
}

.time-actions {
  display: flex;
  gap: 10px;
  padding: 12px 0 0;
}

.time-save,
.time-reset {
  flex: 1;
  height: 38px;
  border-radius: 10px;
  font-size: 14px;
}

.time-save {
  background: var(--brand);
  color: #fff;
}

.time-save:disabled,
.time-reset:disabled {
  opacity: 0.6;
}

.time-reset {
  background: var(--card-muted);
  color: var(--text-secondary);
}

.switch {
  position: relative;
  flex: none;
  width: 46px;
  height: 26px;
  border-radius: 13px;
  background: var(--card-muted);
  transition: background 0.15s;
}

.switch.is-on {
  background: var(--brand);
}

.switch-dot {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.18);
  transition: transform 0.15s;
}

.switch.is-on .switch-dot {
  transform: translateX(20px);
}

.toast {
  position: fixed;
  left: 50%;
  bottom: calc(env(safe-area-inset-bottom) + 32px);
  transform: translateX(-50%);
  background: rgba(20, 20, 20, 0.86);
  color: #fff;
  font-size: 13px;
  padding: 9px 16px;
  border-radius: 20px;
  z-index: 50;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>