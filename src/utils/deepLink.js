// 桌面小组件深链：lzpu://schedule 等，与 WidgetDeepLink.java 中的路径一一对应。
// 单独抽出来是为了可单测 —— App.vue 里只剩调用。
export const DEEP_LINK_PATHS = {
  schedule: '/schedule',
  messages: '/messages',
  grades: '/grades',
  notices: '/notices',
  changes: '/changes',
  login: '/login'
}

// 不用 new URL()：自定义 scheme 在 WebView 里解析不可靠，正则更稳
export function deepLinkPath(url) {
  if (!url) return ''
  const text = String(url).trim()
  const match = /^lzpu:\/\/([^/?#]+)/i.exec(text) || /^lzpu:([^/?#]+)/i.exec(text)
  if (!match) return ''
  return DEEP_LINK_PATHS[match[1].toLowerCase()] || ''
}