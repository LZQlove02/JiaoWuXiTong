<template>
  <div class="page page--plain">
    <BackBar title="提醒与通知" />

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
        每天自动检查两次（默认 07:10、12:50，可在下方「提醒时间」修改；系统省电策略下可能略有延迟）；
        只提醒本周的停课、调课与新增。默认关闭，开启前会说明后台检查范围；系统清理后台后，重新打开
        App 会自动恢复检查。
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

    <transition name="fade">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>

    <ConsentGate
      v-if="consentOpen"
      :clauses="BACKGROUND_CLAUSES"
      title="开启前请阅读"
      subtitle="教务助手 · 后台检查说明"
      :version-text="`说明版本 v${BACKGROUND_CONSENT_VERSION}`"
      primary-text="同意并开启"
      decline-text="取消"
      @accept="acceptConsent"
      @decline="declineConsent"
    />
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useChangesStore } from '../stores/changes'
import { useGradeAlertStore } from '../stores/gradeAlert'
import { usePortalAlertStore } from '../stores/portalAlert'
import { useScheduleSettingsStore } from '../stores/scheduleSettings'
import {
  BACKGROUND_CLAUSES,
  BACKGROUND_CONSENT_VERSION
} from '../utils/backgroundConsent'
import { useBackgroundGate } from '../composables/useBackgroundGate'
import AppIcon from '../components/AppIcon.vue'
import BackBar from '../components/BackBar.vue'
import ConsentGate from '../components/ConsentGate.vue'

const changes = useChangesStore()
const gradeAlert = useGradeAlertStore()
const portalAlert = usePortalAlertStore()
const scheduleSettings = useScheduleSettingsStore()
const { consentOpen, requestConsent, acceptConsent, declineConsent } = useBackgroundGate()

const toast = ref('')
const morningTime = ref(changes.checkTimes.morning)
const noonTime = ref(changes.checkTimes.noon)
const savingTimes = ref(false)

function showToast(message) {
  toast.value = message
  setTimeout(() => {
    toast.value = ''
  }, 1600)
}

watch(
  () => [changes.checkTimes.morning, changes.checkTimes.noon],
  ([morning, noon]) => {
    morningTime.value = morning
    noonTime.value = noon
  },
  { immediate: true }
)

async function toggleChanges() {
  if (changes.enabled) {
    await changes.setEnabled(false)
    showToast('已关闭课表变更提醒')
    return
  }
  requestConsent(enableChanges)
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
  if (gradeAlert.enabled) {
    try {
      await gradeAlert.setEnabled(false)
      showToast('已关闭成绩提醒')
    } catch (error) {
      showToast(error?.message || '成绩提醒设置失败')
    }
    return
  }
  requestConsent(enableGradeAlert)
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

function togglePortalAlert(kind) {
  const next = kind === 'notices' ? !portalAlert.noticesEnabled : !portalAlert.messagesEnabled
  if (next) {
    requestConsent(() => enablePortalAlert(kind))
    return
  }
  disablePortalAlert(kind)
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

async function disablePortalAlert(kind) {
  try {
    await portalAlert.setEnabled(kind, false)
    showToast('已关闭提醒')
  } catch (error) {
    showToast(error?.message || '提醒设置失败')
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

onMounted(() => {
  changes.init()
  gradeAlert.init()
  portalAlert.init()
})
</script>

<style scoped>
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

.setting-note {
  padding: 0 0 12px;
  border-bottom: 1px solid var(--border);
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-tertiary);
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
