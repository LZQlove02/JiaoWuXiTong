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
  12: { start: '21:00', end: '21:40' },
  // 13-15 节默认补齐，用户可在「课表时间」页自行修改
  13: { start: '21:45', end: '22:25' },
  14: { start: '22:30', end: '23:10' },
  15: { start: '23:15', end: '23:55' }
}

export const WEEKDAY_LABELS = ['一', '二', '三', '四', '五', '六', '日']

export function sectionRange(start, end, sectionTimes = SECTION_TIMES) {
  const first = sectionTimes[start]
  const last = sectionTimes[end]
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

// 开学锚点只是启发式，学校实际开学日多为周一：按「最近的周一」归一，
// 避免锚点落在周五~周日时，周次与课表网格（首列为周一）互相错位。
function termMonday(termStart) {
  const start = toDateOnly(termStart)
  const offset = (start.getDay() + 6) % 7 // 周一=0 … 周日=6
  start.setDate(start.getDate() + (offset <= 3 ? -offset : 7 - offset))
  return start
}

export function getWeekNumber(termStart, date = new Date()) {
  const days = diffDays(termMonday(termStart), date)
  if (days < 0) return 1
  return Math.floor(days / 7) + 1
}

// 第 week 周的周一，供课表网格与日期标签复用，避免两处各算一套
export function termWeekStart(termStart, week) {
  const start = termMonday(termStart)
  start.setDate(start.getDate() + (week - 1) * 7)
  return start
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
