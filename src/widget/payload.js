// 桌面小组件数据契约：把 store 快照压成组件能直接渲染的精简 JSON。
// 纯函数，不依赖 pinia / 网络，便于单测；字段与 store 的映射见《小组件执行方案》§6.1。
import { SECTION_TIMES, WEEKDAY_LABELS, getWeekNumber, getWeekday } from '../utils/date'
import { summarizeGrades } from '../api/zf'

export const WIDGET_SCHEMA = 1

const FALLBACK_COLOR = '#1B5FAA'
const MAX_TERM_WEEK = 20

function pad2(value) {
  return String(value).padStart(2, '0')
}

function clockOf(date) {
  return `${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

// 带本地时区偏移的 ISO 时间，原生侧只展示 updatedAtLabel，不需要再解析时区
function localIso(date) {
  const offset = -date.getTimezoneOffset()
  const sign = offset >= 0 ? '+' : '-'
  const abs = Math.abs(offset)
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
    + `T${clockOf(date)}:${pad2(date.getSeconds())}`
    + `${sign}${pad2(Math.floor(abs / 60))}:${pad2(abs % 60)}`
}

export function toMinutes(value) {
  const parts = String(value || '').split(':')
  if (parts.length !== 2) return null
  const hours = Number(parts[0])
  const minutes = Number(parts[1])
  if (!Number.isFinite(hours) || !Number.isFinite(minutes)) return null
  return hours * 60 + minutes
}

export function currentWeekOf(term, now = new Date()) {
  if (!term?.startDate) return 1
  const week = Number(getWeekNumber(term.startDate, now))
  if (!Number.isFinite(week)) return 1
  return Math.min(Math.max(week, 1), MAX_TERM_WEEK)
}

/**
 * week[] 代表哪一周的锚点：该周周一（本地日期 YYYY-MM-DD）。
 * 原生跨天重算拿「当天的本周一」和它比对，不一致说明已经跨周、
 * 快照里的 week[] 是上一周的，必须提示回 App 重新同步而不是拿旧周顶上。
 */
export function weekAnchorOf(date = new Date()) {
  const monday = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  // getDay()：周日 0、周一 1 … 周六 6；换算成「距本周一的天数」
  monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7))
  return `${monday.getFullYear()}-${pad2(monday.getMonth() + 1)}-${pad2(monday.getDate())}`
}

// session.weeks 是周次数组；为空数组表示周次未知，按「每周都有」处理，避免组件空白
export function sessionsInWeek(sessions, week) {
  if (!Array.isArray(sessions)) return []
  return sessions.filter((session) => {
    if (!session || !(Number(session.weekday) >= 1)) return false
    const weeks = Array.isArray(session.weeks) ? session.weeks : null
    if (weeks && weeks.length && !weeks.includes(week)) return false
    return true
  })
}

function courseIndex(courses) {
  const map = new Map()
  if (Array.isArray(courses)) {
    courses.forEach((course) => {
      if (course?.id) map.set(course.id, course)
    })
  }
  return map
}

// 组件宽度有限，去掉「官塘校区」这类校区前缀，保留楼栋+教室号
export function shortRoom(room) {
  const text = String(room || '').trim()
  if (!text) return ''
  const shortened = text.replace(/^[^\d\s]{1,8}校区/, '').trim()
  return shortened || text
}

// sessions 里没有课程名与颜色，必须按 courseId join 课程表
function classItem(session, courses) {
  const course = courses.get(session.courseId) || {}
  const start = Number(session.startSection) || 0
  const end = Number(session.endSection) || 0
  const first = SECTION_TIMES[start]
  const last = SECTION_TIMES[end]
  return {
    start,
    end,
    // 1=周一 … 7=周日；原生 2x2 靠它算「周六 14:30」，不再按渲染时刻猜「明天」
    weekday: Number(session.weekday) || 0,
    startTime: first ? first.start : '',
    endTime: last ? last.end : '',
    name: course.name || '',
    room: shortRoom(session.room),
    color: course.color || FALLBACK_COLOR
  }
}

function byStart(a, b) {
  return (Number(a.startSection) || 0) - (Number(b.startSection) || 0)
}

// 下一节课：今天还没开始上课的最近一节；进行中的课不算「下一节」，也不越过它顺延到明天。
// 今天没有未开始的课，才顺延到本周内最近有课的一天。
export function pickNextClass(weekSessions, courses, weekday, nowMinutes) {
  const today = weekSessions
    .filter((session) => Number(session.weekday) === weekday)
    .sort(byStart)
  for (const session of today) {
    const item = classItem(session, courses)
    const startAt = toMinutes(item.startTime)
    // 时间缺失时按「还没开始」处理，宁可显示也不空着
    if (startAt === null || startAt > nowMinutes) return { ...item, dayLabel: '今天' }
  }
  for (let offset = 1; offset <= 7; offset += 1) {
    const target = ((weekday - 1 + offset) % 7) + 1
    const sessions = weekSessions
      .filter((session) => Number(session.weekday) === target)
      .sort(byStart)
    if (!sessions.length) continue
    const item = classItem(sessions[0], courses)
    return { ...item, dayLabel: offset === 1 ? '明天' : `周${WEEKDAY_LABELS[target - 1] || ''}` }
  }
  return null
}

function toGradeView(summary, termLabel) {
  if (!summary || !summary.count) return null
  return {
    gpa: summary.gpa === undefined ? null : summary.gpa,
    totalCredits: summary.totalCredits ?? 0,
    count: summary.count,
    termLabel: termLabel || ''
  }
}

// 单学期成绩直接用 summary；全部学期成绩按当前学期 termLabel 取组再汇总
export function gradeView(grades, term) {
  if (!grades) return null
  if (grades.all) {
    const label = term?.label || ''
    const group = (Array.isArray(grades.groups) ? grades.groups : [])
      .find((item) => item?.termLabel === label)
    if (!group || !Array.isArray(group.grades) || !group.grades.length) return null
    return toGradeView(summarizeGrades(group.grades), label)
  }
  return toGradeView(grades.summary, grades.term?.label || term?.label || '')
}

function messageView(messages) {
  const items = Array.isArray(messages?.items) ? messages.items : []
  return {
    unread: items.length,
    latest: items.length ? [{ title: items[0].title || '', time: items[0].time || '' }] : []
  }
}

function noticeView(notices) {
  const items = Array.isArray(notices?.items) ? notices.items : []
  return {
    latest: items.length ? [{ title: items[0].title || '', date: items[0].date || '' }] : []
  }
}

/**
 * @param {object} input  store 快照
 * @param {object|null} input.user  auth.user
 * @param {object|null} input.currentTerm  academic.currentTerm
 * @param {object|null} input.schedule  academic.schedule
 * @param {object|null} input.grades  academic.grades
 * @param {object|null} input.messages  academic.messages
 * @param {object|null} input.portalNotices  academic.portalNotices
 * @param {number} [input.changeUnread]  课表变更未读数
 * @param {boolean} [input.changeEnabled] 课表变更提醒开关
 * @param {Date} [input.now]
 */
export function buildWidgetPayload(input = {}) {
  const now = input.now instanceof Date ? input.now : new Date()
  const updatedAt = localIso(now)
  const updatedAtLabel = clockOf(now)
  const schema = WIDGET_SCHEMA

  if (!input.user) {
    return { schema, loggedIn: false, changeBadge: false, updatedAt, updatedAtLabel }
  }

  const term = input.currentTerm || null
  const week = currentWeekOf(term, now)
  const schedule = input.schedule || null
  const courses = courseIndex(schedule?.courses)
  const weekSessions = sessionsInWeek(schedule?.sessions, week)
  const weekday = getWeekday(now)
  const nowMinutes = now.getHours() * 60 + now.getMinutes()

  const today = weekSessions
    .filter((session) => Number(session.weekday) === weekday)
    .sort(byStart)
    .map((session) => classItem(session, courses))

  const remainingToday = today.filter((item) => {
    const endAt = toMinutes(item.endTime)
    return endAt === null || endAt >= nowMinutes
  }).length

  const weekGrid = weekSessions
    .map((session) => classItem(session, courses))
    .sort((a, b) => a.weekday - b.weekday || a.start - b.start)

  return {
    schema,
    loggedIn: true,
    changeBadge: Boolean(input.changeUnread > 0) && input.changeEnabled !== false,
    updatedAt,
    updatedAtLabel,
    account: { name: input.user.name || '' },
    term: { label: term?.label || '', week },
    weekAnchor: weekAnchorOf(now),
    // 区分「课表还没加载」与「本周确实没课」
    scheduleLoaded: Boolean(schedule && Array.isArray(schedule.sessions)),
    today,
    next: pickNextClass(weekSessions, courses, weekday, nowMinutes),
    remainingToday,
    week: weekGrid,
    messages: messageView(input.messages),
    notices: noticeView(input.portalNotices),
    grades: gradeView(input.grades, term)
  }
}

export default buildWidgetPayload