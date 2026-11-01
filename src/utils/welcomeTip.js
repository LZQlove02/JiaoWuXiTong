const STORAGE_KEY = 'lzpu.welcomeTip.v1'

export function isWelcomeTipSeen() {
  try {
    return localStorage.getItem(STORAGE_KEY) === '1'
  } catch (error) {
    // 存储不可用时按未展示处理
    return false
  }
}

export function markWelcomeTipSeen() {
  try {
    localStorage.setItem(STORAGE_KEY, '1')
  } catch (error) {
    // 写不进去时下次启动会再提示一次，不影响使用
  }
}
