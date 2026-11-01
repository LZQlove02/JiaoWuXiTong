// 桌面小组件/通知深链：jiaowu://schedule、jiaowu://notice/<id> 等。
// 单独抽出来是为了可单测 —— App.vue 里只剩调用。
export const DEEP_LINK_PATHS = {
  schedule: '/schedule',
  messages: '/messages',
  grades: '/grades',
  notices: '/notices',
  changes: '/changes',
  login: '/login'
}

const ID_PATTERN = /^[A-Za-z0-9_-]{1,64}$/

function safeId(value) {
  if (!value) return ''
  let decoded = ''
  try {
    decoded = decodeURIComponent(value)
  } catch (error) {
    return ''
  }
  return ID_PATTERN.test(decoded) ? decoded : ''
}

// 不用 new URL()：自定义 scheme 在 WebView 里解析不可靠，正则更稳
export function deepLinkPath(url) {
  if (!url) return ''
  const text = String(url).trim()

  const dynamic = /^jiaowu:(?:\/\/)?(notice|course|message)\/([^/?#]+)/i.exec(text)
  if (dynamic) {
    const id = safeId(dynamic[2])
    if (!id) return ''
    const kind = dynamic[1].toLowerCase()
    if (kind === 'notice') return `/notices/${id}`
    if (kind === 'course') return `/course/${id}`
    return `/messages?id=${encodeURIComponent(id)}`
  }

  const match = /^jiaowu:(?:\/\/)?([^/?#]+)(?:[/?#].*)?$/i.exec(text)
  if (!match) return ''
  return DEEP_LINK_PATHS[match[1].toLowerCase()] || ''
}
