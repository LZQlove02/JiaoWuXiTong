import { Capacitor, registerPlugin } from '@capacitor/core'
import { buildFeedbackMailto } from '../utils/feedback'

// 原生实现见 android/app/src/main/java/com/lzpu/jwxt/SupportBridgePlugin.java
export const SupportBridge = registerPlugin('SupportBridge')

const REPO = 'LZQlove02/lzpu-jwxt-releases'
const LATEST_URL = `https://api.github.com/repos/${REPO}/releases/latest`

export const RELEASE_PAGE_BASE = `https://github.com/${REPO}/releases/tag/`
export const TAG_PATTERN = /^v?\d+(?:\.\d+){1,3}(?:[-+][0-9A-Za-z.-]+)?$/

export function supportAvailable() {
  return Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android'
}

export function isValidTag(tag) {
  return typeof tag === 'string' && tag.length > 0 && tag.length <= 40 && TAG_PATTERN.test(tag)
}

function normalizeError(error) {
  const message = error?.message || String(error || '')
  return message.trim() || 'UNKNOWN'
}

/**
 * 读取发布仓库最新正式 Release。
 * 原生走 SupportBridge；Web 预览用 GitHub API（允许跨域）兜底。
 * 返回 { found, tag, name, notes, url, publishedAt } 或 { found: false, reason }。
 */
export async function checkLatest() {
  if (supportAvailable()) {
    return SupportBridge.checkLatest()
  }
  const response = await fetch(LATEST_URL, {
    headers: { Accept: 'application/vnd.github+json' }
  })
  if (response.status === 404) return { found: false, reason: 'no-release' }
  if (!response.ok) throw new Error(`HTTP_${response.status}`)
  const data = await response.json()
  return {
    found: true,
    tag: data.tag_name || '',
    name: data.name || '',
    notes: data.body || '',
    url: data.html_url || '',
    publishedAt: data.published_at || ''
  }
}

export async function openReleasePage(tag) {
  if (!isValidTag(tag)) throw new Error('TAG_INVALID')
  if (supportAvailable()) {
    return SupportBridge.openReleasePage({ tag })
  }
  window.open(RELEASE_PAGE_BASE + tag, '_blank', 'noopener')
}

export async function composeFeedback(subject, body) {
  if (supportAvailable()) {
    return SupportBridge.composeFeedback({ subject, body })
  }
  window.location.href = buildFeedbackMailto(subject, body)
}

// 打开已下载到应用私有目录的附件；原生侧校验路径与扩展名
export async function openLocalFile(path) {
  if (!supportAvailable()) throw new Error('原生文件打开仅在 App 内可用')
  return SupportBridge.openLocalFile({ path })
}

// 打开公开官网链接；原生侧只允许 https://www.lzpu.edu.cn
export async function openExternal(url) {
  if (!supportAvailable()) {
    window.open(url, '_blank', 'noopener')
    return
  }
  return SupportBridge.openExternal({ url })
}

export async function getStorageInfo() {
  if (!supportAvailable()) return { cacheBytes: 0, filesBytes: 0, totalBytes: 0 }
  return SupportBridge.getStorageInfo()
}

export async function getDeviceInfo() {
  if (supportAvailable()) {
    return SupportBridge.getDeviceInfo()
  }
  return { manufacturer: 'Web', model: '', android: '', sdk: 0 }
}

// 读取本地崩溃摘要；只在用户勾选「附上诊断信息」时进入邮件正文
export async function getLastCrash() {
  if (supportAvailable()) {
    return SupportBridge.getLastCrash()
  }
  return { found: false, summary: '' }
}

export async function clearCrashLog() {
  if (supportAvailable()) {
    return SupportBridge.clearCrashLog()
  }
}

export async function copyText(text) {
  if (supportAvailable()) {
    return SupportBridge.copyText({ text })
  }
  if (!navigator.clipboard) throw new Error('CLIPBOARD_UNAVAILABLE')
  await navigator.clipboard.writeText(text)
}

export function errorCode(error) {
  return normalizeError(error)
}
