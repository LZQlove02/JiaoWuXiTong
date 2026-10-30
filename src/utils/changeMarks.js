// 把本周变更映射到课表格子。纯函数，便于单测。
const MARK_PRIORITY = { move: 1, add: 2 }

export function markKey(snapshot) {
  if (!snapshot) return ''
  return `${snapshot.weekday}-${snapshot.startSection}`
}

export function sessionMarkKey(session) {
  if (!session) return ''
  return `${session.weekday}-${session.startSection}`
}

/**
 * @param {Array} entries 原生返回的变更条目（已按本周过滤）
 * @returns {{ marks: Record<string, string>, ghosts: Array }}
 */
export function buildChangeMarks(entries) {
  /** @type {Record<string, string>} */
  const marks = {}
  const ghosts = []
  const ghostKeys = new Set()
  for (const entry of entries || []) {
    if (!entry || entry.quiet) continue
    if (entry.type === 'cancel') {
      if (!entry.from) continue
      const key = markKey(entry.from)
      if (ghostKeys.has(key)) continue
      ghostKeys.add(key)
      ghosts.push(entry)
      continue
    }
    if (entry.type === 'room') continue
    if (!entry.to) continue
    const key = markKey(entry.to)
    const current = marks[key]
    const priority = MARK_PRIORITY[entry.type] || 9
    if (!current || priority < (MARK_PRIORITY[current] || 9)) {
      marks[key] = entry.type
    }
  }
  return { marks, ghosts }
}

export function markOf(marks, session) {
  if (!marks) return ''
  return marks[sessionMarkKey(session)] || ''
}