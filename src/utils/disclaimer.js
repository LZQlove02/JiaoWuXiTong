const STORAGE_KEY = 'lzpu.disclaimer.accepted'

// 声明文案修订时同步 +1，老用户下次启动会重新看到声明
export const DISCLAIMER_VERSION = '1'
export const DISCLAIMER_DATE = '2026-10-30'

export function acceptedDisclaimerVersion() {
  try {
    return localStorage.getItem(STORAGE_KEY) || ''
  } catch (error) {
    // 存储不可用时按未同意处理
    return ''
  }
}

export function isDisclaimerAccepted() {
  return acceptedDisclaimerVersion() === DISCLAIMER_VERSION
}

export function acceptDisclaimer() {
  try {
    localStorage.setItem(STORAGE_KEY, DISCLAIMER_VERSION)
    return true
  } catch (error) {
    // 写不进去也放行本次会话，下次启动会重新提示
    return false
  }
}