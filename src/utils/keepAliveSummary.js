// 原生保活记账的展示口径：只把 lastCheckAt / lastResult / nextCheckAt 翻译成人话。
// 放在工具层是为了能单测，尤其是「开关开着但没有会话」这条容易说错的路径。

export const KEEP_ALIVE_RESULT_LABELS = {
  ALIVE: '会话有效',
  EXPIRED: '会话已失效',
  FAILED: '临时失败，会自动重试'
}

export function formatKeepAliveStamp(value, now = new Date()) {
  const stamp = Number(value)
  if (!stamp) return ''
  const date = new Date(stamp)
  if (Number.isNaN(date.getTime())) return ''
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  // 同一天只显示时分，跨天补上月日，避免每次都读一长串
  if (date.toDateString() === now.toDateString()) return `${hh}:${mm}`
  return `${date.getMonth() + 1}月${date.getDate()}日 ${hh}:${mm}`
}

export function describeKeepAlive(state, now = new Date()) {
  if (!state) return ''
  const result = KEEP_ALIVE_RESULT_LABELS[state.lastResult] || ''
  const lastCheck = state.lastCheckAt
    ? `上次保活 ${formatKeepAliveStamp(state.lastCheckAt, now)}${result ? `（${result}）` : ''}`
    : ''
  // 开关还开着但本地会话已被清掉（学校统一认证到期后进入只读模式）：不会再排程，
  // 继续显示「下次约」会让人误以为保活仍在后台跑
  if (state.active === false) {
    return lastCheck ? `${lastCheck} · 已停止，等待重新登录` : '已停止，等待重新登录'
  }
  const parts = [lastCheck || '尚未执行过保活']
  if (state.nextCheckAt) parts.push(`下次约 ${formatKeepAliveStamp(state.nextCheckAt, now)}`)
  return parts.join(' · ')
}
