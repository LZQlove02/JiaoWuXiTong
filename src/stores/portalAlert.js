import { ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getPortalAlertState,
  scheduleChangeAvailable,
  setPortalAlertEnabled
} from '../native/scheduleChangeBridge'

/**
 * 新通知 / 新消息提醒开关。后台检查、ID 基线与通知都在原生侧完成；
 * 这里只负责设置页展示与开关同步。默认关闭，首次开启静默建立基线。
 */
export const usePortalAlertStore = defineStore('portalAlert', () => {
  const noticesEnabled = ref(false)
  const messagesEnabled = ref(false)
  const notificationsEnabled = ref(true)
  const loaded = ref(false)

  function applyState(state) {
    if (!state) return
    if (typeof state.noticesEnabled === 'boolean') noticesEnabled.value = state.noticesEnabled
    if (typeof state.messagesEnabled === 'boolean') messagesEnabled.value = state.messagesEnabled
    if (typeof state.notificationsEnabled === 'boolean') {
      notificationsEnabled.value = state.notificationsEnabled
    }
    loaded.value = true
  }

  async function init() {
    if (!scheduleChangeAvailable()) {
      loaded.value = true
      return
    }
    const state = await getPortalAlertState()
    if (state) applyState(state)
    else loaded.value = true
  }

  async function setEnabled(kind, value) {
    const next = Boolean(value)
    if (kind !== 'notices' && kind !== 'messages') return
    if (!scheduleChangeAvailable()) {
      if (kind === 'notices') noticesEnabled.value = next
      else messagesEnabled.value = next
      return
    }
    const state = await setPortalAlertEnabled(
      kind === 'notices' ? next : null,
      kind === 'messages' ? next : null
    )
    applyState(state)
  }

  return {
    noticesEnabled,
    messagesEnabled,
    notificationsEnabled,
    loaded,
    init,
    setEnabled
  }
})
