import { Capacitor, registerPlugin } from '@capacitor/core'

// 原生侧实现见 android/app/src/main/java/com/lzpu/jwxt/change/ScheduleChangeBridge.java
export const ScheduleChange = registerPlugin('ScheduleChange')

export function scheduleChangeAvailable() {
  return Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android'
}

async function call(method, options) {
  if (!scheduleChangeAvailable()) return null
  try {
    return await ScheduleChange[method](options)
  } catch (error) {
    // 变更提醒失败不能影响课表主流程，只记录
    console.warn(`[change] ScheduleChange.${method} 调用失败`, error)
    return null
  }
}

export function applyScheduleChange(options) {
  return call('applySchedule', options)
}

export function getScheduleChangeState() {
  return call('getState')
}

export function markAllChangesRead() {
  return call('markAllRead')
}

export function setScheduleChangeEnabled(enabled) {
  return call('setEnabled', { enabled })
}

export function requestChangeNotificationPermission() {
  return call('requestNotificationPermission')
}

// 首次成功对账后调用；原生只在需要时真正弹一次系统权限框
export function maybeRequestChangeNotificationPermission() {
  return call('maybeRequestNotificationPermission')
}

export function openScheduleChangeNotificationSettings() {
  return call('openNotificationSettings')
}

// 退出登录：清掉基线与变更历史，避免下一个账号看到上一个人的课表信息
export function clearScheduleChange() {
  return call('clear')
}

export function addScheduleChangeListener(handler) {
  if (!scheduleChangeAvailable()) return () => {}
  const pending = ScheduleChange.addListener('changed', handler)
  return () => {
    pending.then((listener) => listener.remove()).catch(() => {})
  }
}