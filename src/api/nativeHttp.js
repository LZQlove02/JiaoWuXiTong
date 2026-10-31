import { Capacitor, registerPlugin } from '@capacitor/core'

const JwxtHttp = registerPlugin('JwxtHttp')
const SsoLogin = registerPlugin('SsoLogin')

export async function httpGet(url, headers = {}) {
  if (!Capacitor.isNativePlatform()) {
    throw new Error('原生 HTTP 插件仅在 App 内可用')
  }
  const result = await JwxtHttp.get({ url, headers })
  return result
}

export async function downloadFile(url, filename) {
  if (!Capacitor.isNativePlatform()) {
    throw new Error('原生下载仅在 App 内可用')
  }
  return JwxtHttp.download({ url, filename })
}

// 带登录态的附件下载：只允许 zfjw，保存到应用私有 cache
export async function authDownload(url, filename) {
  if (!Capacitor.isNativePlatform()) {
    throw new Error('原生附件下载仅在 App 内可用')
  }
  return JwxtHttp.authDownload({ url, filename })
}

export async function clearAuthDownloads() {
  if (!Capacitor.isNativePlatform()) return
  return JwxtHttp.clearAuthDownloads()
}

export async function clearPublicDownloads() {
  if (!Capacitor.isNativePlatform()) return
  return JwxtHttp.clearPublicDownloads()
}

export async function fileExists(path) {
  if (!Capacitor.isNativePlatform()) return { exists: false }
  return JwxtHttp.fileExists({ path })
}

export async function loginWithSso() {
  if (!Capacitor.isNativePlatform()) {
    throw new Error('统一认证登录仅在 App 内可用')
  }
  return SsoLogin.login()
}

export async function setAuthCookies(cookie) {
  if (!Capacitor.isNativePlatform()) {
    throw new Error('原生 HTTP 插件仅在 App 内可用')
  }
  return JwxtHttp.setAuthCookies({ cookie })
}

export async function loadAuthCookies() {
  if (!Capacitor.isNativePlatform()) return { restored: false }
  return JwxtHttp.loadAuthCookies()
}

export async function clearAuthCookies() {
  if (!Capacitor.isNativePlatform()) return
  return JwxtHttp.clearAuthCookies()
}

export async function authGet(url) {
  if (!Capacitor.isNativePlatform()) {
    throw new Error('原生 HTTP 插件仅在 App 内可用')
  }
  return JwxtHttp.authGet({ url })
}

export async function authPost(url, body = '') {
  if (!Capacitor.isNativePlatform()) {
    throw new Error('原生 HTTP 插件仅在 App 内可用')
  }
  return JwxtHttp.authPost({ url, body })
}

export async function isDebugBuild() {
  if (!Capacitor.isNativePlatform()) return false
  const result = await JwxtHttp.isDebugBuild()
  return Boolean(result?.debug)
}
