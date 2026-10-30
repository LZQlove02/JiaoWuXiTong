import { describe, expect, it } from 'vitest'
import {
  buildWidgetPayload,
  currentWeekOf,
  gradeView,
  pickNextClass,
  sessionsInWeek,
  shortRoom,
  weekAnchorOf
} from '../src/widget/payload'

const term = {
  xnm: '2026',
  xqm: '3',
  label: '2026-2027 学年第一学期',
  startDate: '2026-08-31',
  isCurrent: true
}

const courses = [
  { id: 'c1', name: '形势与政策（三）', color: '#B7791F' },
  { id: 'c2', name: '机器学习与深度学习', color: '#C0392B' },
  { id: 'c3', name: '鸿蒙开发技术', color: '#2E8B57' }
]

// 2026-10-29 是周四，学期起始 2026-08-31，因此是第 9 周
const schedule = {
  term,
  courses,
  sessions: [
    { courseId: 'c1', weekday: 4, startSection: 5, endSection: 5, weeks: [9], room: 'T1-D302' },
    { courseId: 'c2', weekday: 4, startSection: 6, endSection: 7, weeks: [9], room: 'T2-A412' },
    { courseId: 'c3', weekday: 6, startSection: 1, endSection: 2, weeks: [9], room: 'T2-A507' },
    // 下一周的课，不应出现在本周网格
    { courseId: 'c3', weekday: 5, startSection: 1, endSection: 2, weeks: [10], room: 'T2-A507' }
  ]
}

const morning = new Date(2026, 9, 29, 9, 0, 0)
const evening = new Date(2026, 9, 29, 21, 0, 0)

function build(overrides = {}) {
  return buildWidgetPayload({
    user: { name: '卢再发' },
    currentTerm: term,
    schedule,
    now: morning,
    ...overrides
  })
}

describe('小组件 payload：登录态', () => {
  it('未登录只返回登录标记与更新时间', () => {
    const payload = buildWidgetPayload({ user: null, currentTerm: term, schedule, now: morning })
    expect(payload.loggedIn).toBe(false)
    expect(payload.schema).toBe(1)
    expect(payload.updatedAtLabel).toMatch(/^\d{2}:\d{2}$/)
    expect(payload).not.toHaveProperty('today')
    expect(payload).not.toHaveProperty('grades')
  })

  it('未登录时不携带账号与学期信息', () => {
    const payload = buildWidgetPayload({ user: null, now: morning })
    expect(payload.account).toBeUndefined()
    expect(payload.term).toBeUndefined()
  })
})

describe('小组件 payload：课表', () => {
  it('今日课程按时段排序，并补上课程名与颜色', () => {
    const payload = build()
    expect(payload.term).toEqual({ label: term.label, week: 9 })
    expect(payload.today.map((item) => item.name)).toEqual(['形势与政策（三）', '机器学习与深度学习'])
    expect(payload.today[1]).toEqual({
      start: 6,
      end: 7,
      weekday: 4,
      startTime: '14:30',
      endTime: '15:55',
      name: '机器学习与深度学习',
      room: 'T2-A412',
      color: '#C0392B'
    })
  })

  it('下一节课取今天最近一节还没开始的课', () => {
    const payload = build()
    expect(payload.next).toMatchObject({
      name: '形势与政策（三）',
      dayLabel: '今天',
      startTime: '11:35',
      endTime: '12:15'
    })
    expect(payload.remainingToday).toBe(2)
  })

  it('进行中的课不算「下一节」，取下一门没开始的', () => {
    // 12:00：形势与政策（三）11:35-12:15 正在进行，下一节应是 14:30 的机器学习
    const payload = build({ now: new Date(2026, 9, 29, 12, 0) })
    expect(payload.next).toMatchObject({
      name: '机器学习与深度学习',
      dayLabel: '今天',
      startTime: '14:30'
    })
    // 剩余节数仍把进行中的算进去
    expect(payload.remainingToday).toBe(2)
  })

  it('今天的课都在进行中或已结束，才顺延到下一有课的一天', () => {
    // 15:00：机器学习 14:30-15:55 正在进行，今天没有未开始的课 → 顺延到周六
    const payload = build({ now: new Date(2026, 9, 29, 15, 0) })
    expect(payload.next).toMatchObject({ name: '鸿蒙开发技术', dayLabel: '周六' })
    expect(payload.remainingToday).toBe(1)
  })

  it('今天课上完后顺延到本周下一有课的一天', () => {
    const payload = build({ now: evening })
    expect(payload.next).toMatchObject({ name: '鸿蒙开发技术', dayLabel: '周六', weekday: 6 })
    expect(payload.remainingToday).toBe(0)
  })

  it('startTime 缺失的课算「还没开始」，不会被跳过', () => {
    // startSection 超出 SECTION_TIMES 范围 → startTime 为空
    const sessions = [
      { courseId: 'c1', weekday: 4, startSection: 99, endSection: 99, weeks: [9], room: 'T1-101' }
    ]
    const next = pickNextClass(sessions, new Map([['c1', { name: '高等数学' }]]), 4, 600)
    expect(next).toMatchObject({ name: '高等数学', dayLabel: '今天', startTime: '' })
  })

  it('本周网格只含当前周，且包含周末', () => {
    const payload = build()
    expect(payload.week).toHaveLength(3)
    expect(payload.week.map((item) => item.weekday)).toEqual([4, 4, 6])
    expect(payload.week.some((item) => item.weekday === 5)).toBe(false)
    expect(payload.week.every((item) => item.name)).toBe(true)
  })

  it('weekAnchor 记的是 week[] 所属那一周的周一', () => {
    // 2026-10-29 是周四，本周一是 2026-10-26
    expect(build().weekAnchor).toBe('2026-10-26')
    // 同一周的周日仍然指向同一个周一
    expect(build({ now: new Date(2026, 10, 1, 23, 30) }).weekAnchor).toBe('2026-10-26')
  })

  it('跨周后 weekAnchor 变化，原生据此判断快照过期', () => {
    // 2026-11-01 是周日 → 周一仍是 10-26；11-02 周一 → 11-02
    expect(weekAnchorOf(new Date(2026, 10, 1, 20, 0))).toBe('2026-10-26')
    expect(weekAnchorOf(new Date(2026, 10, 2, 0, 5))).toBe('2026-11-02')
    expect(weekAnchorOf(new Date(2026, 11, 31, 8, 0))).toBe('2026-12-28')
  })

  it('未登录时没有 weekAnchor', () => {
    const payload = buildWidgetPayload({ user: null, now: morning })
    expect(payload).not.toHaveProperty('weekAnchor')
  })

  it('周次不在 weeks 数组里的课被过滤', () => {
    expect(sessionsInWeek(schedule.sessions, 9)).toHaveLength(3)
    expect(sessionsInWeek(schedule.sessions, 10)).toHaveLength(1)
    expect(sessionsInWeek(schedule.sessions, 10)[0].weekday).toBe(5)
  })

  it('周次未知（weeks 为空数组）时按每周都有处理，避免组件空白', () => {
    const loose = { ...schedule, sessions: [{ courseId: 'c1', weekday: 4, startSection: 1, endSection: 2, weeks: [], room: 'R' }] }
    const payload = build({ schedule: loose })
    expect(payload.today).toHaveLength(1)
  })

  it('课表未加载时不报错，字段为空', () => {
    const payload = build({ schedule: null })
    expect(payload.scheduleLoaded).toBe(false)
    expect(payload.today).toEqual([])
    expect(payload.week).toEqual([])
    expect(payload.next).toBeNull()
    expect(payload.remainingToday).toBe(0)
  })

  it('地点去掉校区前缀，保留楼栋与教室号', () => {
    expect(shortRoom('官塘校区敏行楼T1-D302')).toBe('敏行楼T1-D302')
    expect(shortRoom('官塘校区笃行楼T2-A412')).toBe('笃行楼T2-A412')
    expect(shortRoom('T2-A412')).toBe('T2-A412')
    expect(shortRoom('')).toBe('')
    // 只有校区没有楼栋时不要被清空
    expect(shortRoom('官塘校区')).toBe('官塘校区')
  })

  it('课表加载后 scheduleLoaded 为 true', () => {
    expect(build().scheduleLoaded).toBe(true)
  })

  it('今天没有课且本周无课时 next 为 null', () => {
    const empty = { term, courses, sessions: [] }
    const payload = build({ schedule: empty })
    expect(payload.next).toBeNull()
    expect(payload.today).toEqual([])
  })

  it('明天有课时 dayLabel 为「明天」', () => {
    const withFriday = {
      ...schedule,
      sessions: [{ courseId: 'c3', weekday: 5, startSection: 1, endSection: 2, weeks: [9], room: 'R' }]
    }
    const payload = build({ schedule: withFriday, now: evening })
    expect(payload.next).toMatchObject({ name: '鸿蒙开发技术', dayLabel: '明天' })
  })

  it('周次由学期起始日推算并收敛到 1-20', () => {
    expect(currentWeekOf(term, morning)).toBe(9)
    expect(currentWeekOf({ startDate: '2026-10-01' }, morning)).toBe(5)
    // 学期还没开学时按第 1 周处理，避免出现 0 或负数周次
    expect(currentWeekOf({ startDate: '2026-12-01' }, morning)).toBe(1)
    expect(currentWeekOf(null, morning)).toBe(1)
    expect(currentWeekOf({ startDate: '2026-08-31' }, new Date(2027, 5, 1))).toBe(20)
  })
})

describe('小组件 payload：成绩', () => {
  const single = {
    term: { label: '2025-2026 学年第二学期' },
    summary: { gpa: 2.34, totalCredits: 44.7, count: 23 }
  }

  it('单学期成绩直接取 summary', () => {
    expect(gradeView(single, term)).toEqual({
      gpa: 2.34,
      totalCredits: 44.7,
      count: 23,
      termLabel: '2025-2026 学年第二学期'
    })
  })

  it('全部学期成绩按当前学期分组汇总', () => {
    const all = {
      all: true,
      groups: [
        { key: '2025_12', termLabel: '2025-2026 学年第二学期', grades: [{ credit: 4, gradePoint: 2 }] },
        { key: '2026_3', termLabel: term.label, grades: [{ credit: 3, gradePoint: 4 }, { credit: 2, gradePoint: 3 }] }
      ],
      summary: { gpa: 2.5, totalCredits: 9, count: 3 }
    }
    expect(gradeView(all, term)).toEqual({
      gpa: 3.6,
      totalCredits: 5,
      count: 2,
      termLabel: term.label
    })
  })

  it('当前学期没有成绩时返回 null', () => {
    const all = {
      all: true,
      groups: [{ key: '2025_12', termLabel: '2025-2026 学年第二学期', grades: [{ credit: 4, gradePoint: 2 }] }],
      summary: { gpa: 2, totalCredits: 4, count: 1 }
    }
    expect(gradeView(all, term)).toBeNull()
    expect(gradeView(null, term)).toBeNull()
    expect(gradeView({ summary: { gpa: null, totalCredits: 0, count: 0 } }, term)).toBeNull()
  })
})

describe('小组件 payload：消息与通知', () => {
  it('取未阅条数与最新一条标题', () => {
    const payload = build({
      messages: {
        total: 40,
        items: [
          { id: 'm1', title: '调课提醒', time: '2026-10-08 15:56:39' },
          { id: 'm2', title: '评教通知', time: '2026-10-07 10:00:00' }
        ]
      },
      portalNotices: {
        items: [{ id: 'n1', title: '关于做好 2026 年国庆假期教学安排的通知', date: '2026-09-16' }]
      }
    })
    expect(payload.messages.unread).toBe(2)
    expect(payload.messages.latest[0]).toEqual({ title: '调课提醒', time: '2026-10-08 15:56:39' })
    expect(payload.notices.latest[0]).toEqual({
      title: '关于做好 2026 年国庆假期教学安排的通知',
      date: '2026-09-16'
    })
  })

  it('没有消息和通知时给出空数组而不是 null', () => {
    const payload = build()
    expect(payload.messages).toEqual({ unread: 0, latest: [] })
    expect(payload.notices).toEqual({ latest: [] })
  })
})

describe('小组件 payload：整体契约', () => {
  it('包含方案约定的全部字段', () => {
    const payload = build()
    expect(Object.keys(payload).sort()).toEqual([
      'account',
      'changeBadge',
      'grades',
      'loggedIn',
      'messages',
      'next',
      'notices',
      'remainingToday',
      'scheduleLoaded',
      'schema',
      'term',
      'today',
      'updatedAt',
      'updatedAtLabel',
      'week',
      'weekAnchor'
    ])
    expect(payload.account).toEqual({ name: '卢再发' })
    expect(payload.updatedAt).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}[+-]\d{2}:\d{2}$/)
    expect(payload.updatedAtLabel).toBe('09:00')
  })

  it('课表变更未读时置 changeBadge，关闭开关或已读后取消', () => {
    expect(build({ changeUnread: 2 }).changeBadge).toBe(true)
    expect(build({ changeUnread: 0 }).changeBadge).toBe(false)
    expect(build({ changeUnread: 2, changeEnabled: false }).changeBadge).toBe(false)
    expect(build({ changeKnown: false })).not.toHaveProperty('changeBadge')
    expect(buildWidgetPayload({ user: null }).changeBadge).toBe(false)
  })
})