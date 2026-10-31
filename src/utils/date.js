export const SECTION_TIMES = {
  1: { start: '08:20', end: '09:00' },
  2: { start: '09:05', end: '09:45' },
  3: { start: '10:05', end: '10:45' },
  4: { start: '10:50', end: '11:30' },
  5: { start: '11:35', end: '12:15' },
  6: { start: '14:30', end: '15:10' },
  7: { start: '15:15', end: '15:55' },
  8: { start: '16:15', end: '16:55' },
  9: { start: '17:00', end: '17:40' },
  10: { start: '19:30', end: '20:10' },
  11: { start: '20:15', end: '20:55' },
  12: { start: '21:00', end: '21:40' }
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

export function formatDateTime(value) {
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function pad(n) {
  return String(n).padStart(2, '0')
}

export function greeting(hour = new Date().getHours()) {
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
}
