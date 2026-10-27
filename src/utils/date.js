export const SECTION_TIMES = {
  1: { start: '08:00', end: '08:45' },
  2: { start: '08:55', end: '09:40' },
  3: { start: '10:00', end: '10:45' },
  4: { start: '10:55', end: '11:40' },
  5: { start: '14:00', end: '14:45' },
  6: { start: '14:55', end: '15:40' },
  7: { start: '16:00', end: '16:45' },
  8: { start: '16:55', end: '17:40' },
  9: { start: '19:00', end: '19:45' },
  10: { start: '19:55', end: '20:40' }
}

export const WEEKDAY_LABELS = ['一', '二', '三', '四', '五', '六', '日']

export function sectionRange(start, end) {
  const first = SECTION_TIMES[start]
  const last = SECTION_TIMES[end]
  if (!first || !last) return ''
  return `${first.start}-${last.end}`
}

export function toDateOnly(value) {
  const d = new Date(value)
  d.setHours(0, 0, 0, 0)
  return d
}

export function diffDays(from, to) {
  const ms = toDateOnly(to).getTime() - toDateOnly(from).getTime()
  return Math.round(ms / 86400000)
}

export function getWeekNumber(termStart, date = new Date()) {
  const days = diffDays(termStart, date)
  if (days < 0) return 1
  return Math.floor(days / 7) + 1
}

export function getWeekday(date = new Date()) {
  const day = date.getDay()
  return day === 0 ? 7 : day
}

export function formatDate(date = new Date()) {
  const d = new Date(date)
  return `${d.getMonth() + 1} 月 ${d.getDate()} 日`
}

export function formatDateFull(date = new Date()) {
  const d = new Date(date)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function pad(n) {
  return String(n).padStart(2, '0')
}

export function daysUntil(dateStr, now = new Date()) {
  return diffDays(now, dateStr)
}

export function greeting(hour = new Date().getHours()) {
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
}
