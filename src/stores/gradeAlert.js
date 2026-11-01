import { ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getGradeAlertState,
  scheduleChangeAvailable,
  setGradeAlertEnabled
} from '../native/scheduleChangeBridge'

/**
 * 成绩提醒开关状态。真正的成绩检查、基线比较与通知都在原生后台完成；
 * 这里只负责设置页展示与开关同步。
 */
export const useGradeAlertStore = defineStore('gradeAlert', () => {
  // 后台检查默认关闭，开启前由设置页说明检查范围与数据存放
  const enabled = ref(false)
  const notificationsEnabled = ref(true)
  const loaded = ref(false)

  function applyState(state) {
    if (!state) return
    if (typeof state.enabled === 'boolean') enabled.value = state.enabled
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
    const state = await getGradeAlertState()
    if (state) applyState(state)
    else loaded.value = true
  }

  async function setEnabled(value) {
    const next = Boolean(value)
    if (!scheduleChangeAvailable()) {
      enabled.value = next
      return
    }
    applyState(await setGradeAlertEnabled(next))
  }

  return {
    enabled,
    notificationsEnabled,
    loaded,
    init,
    setEnabled
  }
})
