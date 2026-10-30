// 版本比较与自动检查节流。全部为纯函数，便于单测。

export const AUTO_CHECK_INTERVAL = 24 * 60 * 60 * 1000
export const RELEASE_NOTE_LIMIT = 1200

/** 解析 "1.2.3" / "v1.2" / "1.2.3-beta.1"；非法值返回 null。 */
export function parseVersion(value) {
  if (typeof value !== 'string') return null
  const text = value.trim().replace(/^v/i, '')
  const match = /^(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:\.(\d+))?(?:[-+][0-9A-Za-z.-]+)?$/.exec(text)
  if (!match) return null
  const parts = [match[1], match[2] || '0', match[3] || '0', match[4] || '0'].map(Number)
  return parts
}

/** 任一版本号非法时返回 0，表示无法比较。 */
export function compareVersions(left, right) {
  const a = parseVersion(left)
  const b = parseVersion(right)
  if (!a || !b) return 0
  for (let index = 0; index < 4; index += 1) {
    if (a[index] > b[index]) return 1
    if (a[index] < b[index]) return -1
  }
  return 0
}

export function isNewer(latest, current) {
  if (!parseVersion(latest) || !parseVersion(current)) return false
  return compareVersions(latest, current) > 0
}

/** 距离上次自动检查不足 24 小时则跳过；时间戳异常时按需要检查处理。 */
export function shouldAutoCheck(lastCheckAt, now = Date.now(), interval = AUTO_CHECK_INTERVAL) {
  const last = Number(lastCheckAt)
  if (!Number.isFinite(last) || last <= 0) return true
  if (last > now) return true
  return now - last >= interval
}

/** 把 GitHub Release 的 Markdown 说明压成可显示的纯文本。 */
export function formatReleaseNotes(notes, limit = RELEASE_NOTE_LIMIT) {
  if (typeof notes !== 'string') return ''
  let text = notes
    .replace(/\r\n/g, '\n')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/```[\s\S]*?```/g, '')
    .replace(/^#{1,6}\s*/gm, '')
    .replace(/^\s*[-*+]\s+/gm, '· ')
    .replace(/\*\*|__|\*|`/g, '')
    .replace(/^\s*>\s?/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
  if (limit > 0 && text.length > limit) {
    text = `${text.slice(0, limit).trimEnd()}…`
  }
  return text
}
