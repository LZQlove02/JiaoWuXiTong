import { Capacitor, registerPlugin } from '@capacitor/core'

const JwxtHttp = registerPlugin('JwxtHttp')

export async function httpGet(url, headers = {}) {
  if (!Capacitor.isNativePlatform()) {
    throw new Error('原生 HTTP 插件仅在 App 内可用')
  }
  const result = await JwxtHttp.get({ url, headers })
  return result
}
