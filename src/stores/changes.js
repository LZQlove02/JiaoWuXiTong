import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { SECTION_TIMES } from '../utils/date'
import { useAuthStore } from './auth'
import {
  addScheduleChangeListener,
  applyScheduleChange,
  getScheduleChangeState,
  markAllChangesRead,
  maybeRequestChangeNotificationPermission,
  openScheduleChangeNotificationSettings,
  requestChangeNotificationPermission,
  resetCheckTimes,
  saveCheckTimes,
  scheduleChangeAvailable,
  setScheduleChangeEnabled,
  getCheckTimes
} from '../native/scheduleChangeBridge'

// 变更状态全部来自原生引擎；Web 端（无插件）保持空态，不影响课表等主流程。
export const useChangesStore = defineStore('changes', () => {
  const auth = useAuthStore()
  const entries = ref([])
  const unread = ref(0)
  const enabled = ref(true)
  const notificationsEnabled = ref(true)
  const checkTimes = ref({ morning: '07:10', noon: '12:50' })
  const loaded = ref(false)
  const applying = ref(false)
  const error = ref('')

  let listening = false
  let stopListening = null

  const bannerCount = computed(() => (enabled.value ? unread.value : 0))
  const bannerVisible = computed(() => bannerCount.value > 0)
  const badgeText = computed(() => (enabled.value ? unread.value : 0))
  const latest = computed(
    () => entries.value.find((entry) => !entry.read && !entry.quiet && entry.type !== 'room') || null
  )

  function normalizeEntry(entry) {
    return {
      ...entry,
      quiet: Boolean(entry.quiet),
      read: Boolean(entry.read),
      notified: Boolean(entry.notified)
    }
  }

  function applyState(state) {
    if (!state) return
    error.value = ''
    if (Array.isArray(state.entries)) entries.value = state.entries.map(normalizeEntry)
    if (Number.isFinite(state.unread)) {
      unread.value = state.unread
    } else {
      unread.value = entries.value.filter(
        (entry) => !entry.read && !entry.quiet && entry.type !== 'room'
      ).length
    }
    if (typeof state.enabled === 'boolean') enabled.value = state.enabled
    if (typeof state.notificationsEnabled === 'boolean') {
      notificationsEnabled.value = state.notificationsEnabled
    }
    loaded.value = true
  }

  async function init() {
    error.value = ''
    if (!scheduleChangeAvailable()) {
      loaded.value = true
      return
    }
    const state = await getScheduleChangeState()
    if (!state) {
      // 原生桥失败：给出错误态，不能伪装成「最近 30 天没有变更」
      error.value = '变更记录加载失败'
      loaded.value = true
      return
    }
    applyState(state)
    const times = await getCheckTimes()
    if (times && times.morning && times.noon) {
      checkTimes.value = { morning: times.morning, noon: times.noon }
    }
  }

  async function refresh() {
    if (!scheduleChangeAvailable()) return
    const state = await getScheduleChangeState()
    if (state) {
      applyState(state)
      error.value = ''
    }
  }

  /**
   * 实时课表到手后交给原生对账。离线快照、缺少学期信息或 Web 端直接跳过。
   */
  async function applyFromSchedule(schedule) {
    if (!schedule || !schedule.rawPayload) return
    if (schedule.offline) return
    const term = schedule.term
    if (!term || !term.xnm || !term.xqm || !term.startDate) return
    if (!scheduleChangeAvailable() || applying.value) return
    applying.value = true
    try {
      const state = await applyScheduleChange({
        raw: schedule.rawPayload,
        studentId: String(auth.user?.id || ''),
        term: {
          xnm: String(term.xnm),
          xqm: String(term.xqm),
          startDate: String(term.startDate)
        },
        sectionTimes: SECTION_TIMES
      })
      applyState(state)
      if (state) {
        // 首次成功对账后申请一次；是否真正弹框由原生判断（前台 / 未问过 / 未授权）
        maybeRequestChangeNotificationPermission().then((result) => {
          if (result?.asked) refresh()
        })
      }
    } finally {
      applying.value = false
    }
  }

  async function markAllRead() {
    if (!scheduleChangeAvailable()) {
      entries.value = entries.value.map((entry) => ({ ...entry, read: true }))
      unread.value = 0
      return
    }
    applyState(await markAllChangesRead())
  }

  async function setEnabled(value) {
    const next = Boolean(value)
    if (!scheduleChangeAvailable()) {
      enabled.value = next
      if (!next) unread.value = 0
      return
    }
    applyState(await setScheduleChangeEnabled(next))
  }

  async function requestNotificationPermission() {
    if (!scheduleChangeAvailable()) return false
    const result = await requestChangeNotificationPermission()
    if (result && typeof result.granted === 'boolean') {
      notificationsEnabled.value = result.granted
    }
    return notificationsEnabled.value
  }

  function openNotificationSettings() {
    if (!scheduleChangeAvailable()) return
    openScheduleChangeNotificationSettings()
  }

  // 检查时间由课表变更、成绩提醒等后台任务共用；Web 端只保存页面状态
  async function updateCheckTimes(morning, noon) {
    if (!scheduleChangeAvailable()) {
      checkTimes.value = { morning, noon }
      return checkTimes.value
    }
    const result = await saveCheckTimes(morning, noon)
    if (result && result.morning && result.noon) {
      checkTimes.value = { morning: result.morning, noon: result.noon }
    }
    return checkTimes.value
  }

  async function restoreDefaultCheckTimes() {
    if (!scheduleChangeAvailable()) {
      checkTimes.value = { morning: '07:10', noon: '12:50' }
      return checkTimes.value
    }
    const result = await resetCheckTimes()
    if (result && result.morning && result.noon) {
      checkTimes.value = { morning: result.morning, noon: result.noon }
    }
    return checkTimes.value
  }

  // 后台检查在 App 前台触发时，原生会发 changed 事件；这里刷新一次提醒条
  function listen() {
    if (listening || !scheduleChangeAvailable()) return
    listening = true
    stopListening = addScheduleChangeListener(() => {
      refresh()
    })
  }

  function dispose() {
    if (stopListening) stopListening()
    stopListening = null
    listening = false
  }

  return {
    entries,
    unread,
    enabled,
    notificationsEnabled,
    checkTimes,
    loaded,
    applying,
    error,
    bannerCount,
    bannerVisible,
    badgeText,
    latest,
    init,
    refresh,
    applyFromSchedule,
    markAllRead,
    setEnabled,
    requestNotificationPermission,
    openNotificationSettings,
    updateCheckTimes,
    restoreDefaultCheckTimes,
    listen,
    dispose
  }
})