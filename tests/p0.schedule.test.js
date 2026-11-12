import { describe, expect, it } from 'vitest'
import { getWeekNumber, termWeekStart } from '../src/utils/date'

// 学校实际开学日均为周一，而学期锚点（08-31 / 02-23）只是启发式：
// 2024-08-31 周六、2025-08-31 周日、2025-02-23 周日、2027-02-23 周二。
// 统一规则：锚点归一到「最近的周一」（≤3 天回退，>3 天前移到下周一）。
const ANCHORS = ['2024-08-31', '2025-08-31', '2025-02-23', '2026-08-31', '2027-02-23']

function mondayOf(value) {
  const date = new Date(value)
  const monday = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7))
  return monday
}

describe('P0-4 周次锚点对齐周一：自然周内周次不变', () => {
  it.each(ANCHORS)('锚点 %s：同一自然周的周一与周日周次相等', (anchor) => {
    // 取开学后第 7 周附近的自然周，覆盖「锚点在周末」与「锚点在周中」两种情况
    const probe = new Date(anchor)
    probe.setDate(probe.getDate() + 45)
    const monday = mondayOf(probe)
    const sunday = new Date(monday)
    sunday.setDate(sunday.getDate() + 6)

    expect(monday.getDay()).toBe(1)
    expect(sunday.getDay()).toBe(0)
    expect(getWeekNumber(anchor, sunday)).toBe(getWeekNumber(anchor, monday))
  })
})

describe('P0-4 周次锚点对齐周一：已知锚点', () => {
  it('2025-08-31（周日）前移到 2025-09-01，开学首周为 9/1-9/7', () => {
    expect(getWeekNumber('2025-08-31', new Date(2025, 8, 1))).toBe(1)
    expect(getWeekNumber('2025-08-31', new Date(2025, 8, 7))).toBe(1)
    expect(getWeekNumber('2025-08-31', new Date(2025, 8, 8))).toBe(2)
  })

  it('2026-08-31（周一）保持不变，09-07 进入第 2 周', () => {
    // 注意：锚点本身就是周一，09-07 是它的第 8 天，因此属于第 2 周，
    // 与 termWeekStart('2026-08-31', 3) === 2026-09-14 的口径一致。
    expect(getWeekNumber('2026-08-31', new Date(2026, 8, 6))).toBe(1)
    expect(getWeekNumber('2026-08-31', new Date(2026, 8, 7))).toBe(2)
    expect(getWeekNumber('2026-08-31', new Date(2026, 8, 14))).toBe(3)
  })

  it('开学前一律按第 1 周，避免出现 0 或负数', () => {
    expect(getWeekNumber('2027-02-23', new Date(2027, 1, 21))).toBe(1)
  })
})

describe('P0-4 课表网格取周一：termWeekStart', () => {
  it('2025-08-31 的第 1 周从 2025-09-01（周一）开始', () => {
    const monday = termWeekStart('2025-08-31', 1)
    expect(monday.getDay()).toBe(1)
    expect(monday.getFullYear()).toBe(2025)
    expect(monday.getMonth()).toBe(8)
    expect(monday.getDate()).toBe(1)
  })

  it('2026-08-31 的第 3 周是 2026-09-14', () => {
    const monday = termWeekStart('2026-08-31', 3)
    expect(monday.getDay()).toBe(1)
    expect(monday.getFullYear()).toBe(2026)
    expect(monday.getMonth()).toBe(8)
    expect(monday.getDate()).toBe(14)
  })

  it('termWeekStart 与 getWeekNumber 互为逆运算', () => {
    for (const anchor of ANCHORS) {
      for (const week of [1, 2, 9, 20]) {
        const monday = termWeekStart(anchor, week)
        expect(monday.getDay()).toBe(1)
        expect(getWeekNumber(anchor, monday)).toBe(week)
      }
    }
  })
})
