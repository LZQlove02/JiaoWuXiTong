// 变更条目的展示文案。原生只给结构化字段，这里统一转成中文，页面与通知口径一致。
import { WEEKDAY_LABELS, diffDays, formatDateTime, toDateOnly } from './date'

export const CHANGE_TYPES = {
  cancel: { label: '停课', tone: 'danger', mark: '停' },
  move: { label: '调课', tone: 'warning', mark: '调' },
  add: { label: '新增', tone: 'success', mark: '新' },
  room: { label: '换教室', tone: 'muted', mark: '' }
}

export function changeMeta(type) {
  return CHANGE_TYPES[type] || { label: '变更', tone: 'muted', mark: '' }
}

export function weekdayText(weekday) {
  const label = WEEKDAY_LABELS[Number(weekday) - 1]
  return label ? `周${label}` : ''
}

export function sectionText(startSection, endSection) {
  if (!startSection) return ''
  if (!endSection || Number(endSection) === Number(startSection)) return `${startSection} 节`
  return `${startSection}-${endSection} 节`
}

// 周一 3-4 节
export function snapshotTime(snapshot) {
  if (!snapshot) return ''
  return `${weekdayText(snapshot.weekday)} ${sectionText(snapshot.startSection, snapshot.endSection)}`.trim()
}

// 周一 3-4 节 · A101
export function snapshotPlace(snapshot) {
  if (!snapshot) return ''
  const time = snapshotTime(snapshot)
  return snapshot.room ? `${time} · ${snapshot.room}` : time
}

// 列表详情：调课给出「原 → 新」，周次调整只给周次
export function changeSummary(entry) {
  if (!entry) return ''
  switch (entry.type) {
    case 'cancel':
      return snapshotPlace(entry.from)
    case 'add':
      return snapshotPlace(entry.to)
    case 'room':
      return `${entry.from?.room || '未写教室'} → ${entry.to?.room || '未写教室'}`
    case 'move': {
      const from = entry.from
      const to = entry.to
      const timeChanged = from?.weekday !== to?.weekday
        || from?.startSection !== to?.startSection
        || from?.endSection !== to?.endSection
      if (!timeChanged) return `${from?.weeksLabel || ''} → ${to?.weeksLabel || ''}`
      return `原：${snapshotPlace(from)} → 新：${snapshotPlace(to)}`
    }
    default:
      return ''
  }
}

// 首页提醒条副文案：高等数学 · 周一 3-4 节 · 停课
export function changeSubtitle(entry) {
  if (!entry) return ''
  if (entry.type === 'room') {
    return `${entry.courseName} · ${entry.from?.room || ''} → ${entry.to?.room || ''}`
  }
  const snapshot = entry.type === 'cancel' ? entry.from : entry.to
  const meta = changeMeta(entry.type)
  return `${entry.courseName} · ${snapshotTime(snapshot)} · ${meta.label}`
}

export function dateGroupLabel(iso, now = new Date()) {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '更早'
  const days = diffDays(toDateOnly(date), toDateOnly(now))
  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  return `${date.getMonth() + 1} 月 ${date.getDate()} 日`
}

export function detectedText(iso) {
  const text = formatDateTime(iso)
  return text ? `${text} 发现` : ''
}

export function unreadBadgeText(count) {
  const value = Number(count) || 0
  return value > 9 ? '9+' : String(value)
}