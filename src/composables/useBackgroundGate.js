import { ref } from 'vue'
import { acceptBackgroundConsent, isBackgroundConsentAccepted } from '../utils/backgroundConsent'

/**
 * 后台检查同意的共用流程：未同意时先弹出说明，同意后继续执行开关动作。
 * 课表变更、成绩、通知与消息三个设置页共用同一份同意记录。
 */
export function useBackgroundGate() {
  const consentOpen = ref(false)
  let pendingAction = null

  function requestConsent(action) {
    if (isBackgroundConsentAccepted()) {
      action()
      return
    }
    pendingAction = action
    consentOpen.value = true
  }

  function acceptConsent() {
    acceptBackgroundConsent()
    consentOpen.value = false
    const action = pendingAction
    pendingAction = null
    if (action) action()
  }

  function declineConsent() {
    consentOpen.value = false
    pendingAction = null
  }

  return { consentOpen, requestConsent, acceptConsent, declineConsent }
}
