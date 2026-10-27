import { Capacitor, registerPlugin } from '@capacitor/core'

const JwxtHttp = registerPlugin('JwxtHttp')

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

export async function fileExists(path) {
  if (!Capacitor.isNativePlatform()) return { exists: false }
  return JwxtHttp.fileExists({ path })
}
