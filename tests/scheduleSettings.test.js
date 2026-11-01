import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import {
  DEFAULT_PERIODS,
  MAX_PERIODS,
  MIN_PERIODS,
  isSectionTimeValid,
  normalizeScheduleSettings,
  useScheduleSettingsStore,
  validateScheduleDraft
} from '../src/stores/scheduleSettings'
import { SECTION_TIMES } from '../src/utils/date'

const STORAGE_KEY = 'jiaowu.schedule.settings.v1'

function cloneTimes(source) {
  const result = {}
  for (let section = 1; section <= MAX_PERIODS; section += 1) {
    result[section] = { ...source[section] }
  }
  return result
}

describe('课表时间设置：规范化', () => {
  it('没有存储数据时回退默认 12 节与内置时间', () => {
    const result = normalizeScheduleSettings(null)
    expect(result.periodCount).toBe(DEFAULT_PERIODS)
    expect(result.times[1]).toEqual(SECTION_TIMES[1])
    expect(Object.keys(result.times)).toHaveLength(MAX_PERIODS)
  })

  it('越界节数收敛到 6-15，非整数回退默认', () => {
    expect(normalizeScheduleSettings({ periodCount: 3 }).periodCount).toBe(MIN_PERIODS)
    expect(normalizeScheduleSettings({ periodCount: 20 }).periodCount).toBe(MAX_PERIODS)
    expect(normalizeScheduleSettings({ periodCount: 8.5 }).periodCount).toBe(DEFAULT_PERIODS)
    expect(normalizeScheduleSettings({ periodCount: '10' }).periodCount).toBe(10)
  })

  it('非法时间条目回退默认，合法条目保留', () => {
    const result = normalizeScheduleSettings({
      periodCount: 10,
      times: {
        1: { start: '08:30', end: '09:10' },
        2: { start: '10:00', end: '09:30' },
        3: { start: '25:00', end: '26:00' }
      }
    })
    expect(result.times[1]).toEqual({ start: '08:30', end: '09:10' })
    expect(result.times[2]).toEqual(SECTION_TIMES[2])
    expect(result.times[3]).toEqual(SECTION_TIMES[3])
  })

  it('起止时间校验：格式正确且结束晚于开始', () => {
    expect(isSectionTimeValid({ start: '08:20', end: '09:00' })).toBe(true)
    expect(isSectionTimeValid({ start: '09:00', end: '09:00' })).toBe(false)
    expect(isSectionTimeValid({ start: '9:00', end: '09:40' })).toBe(false)
    expect(isSectionTimeValid(null)).toBe(false)
  })
})

describe('课表时间设置：草稿校验', () => {
  const draft = cloneTimes(SECTION_TIMES)

  it('节数需在 6-15 之间', () => {
    expect(validateScheduleDraft(6, draft)).toBe('')
    expect(validateScheduleDraft(MIN_PERIODS - 1, draft)).toContain('6-15')
    expect(validateScheduleDraft(MAX_PERIODS + 1, draft)).toContain('6-15')
    expect(validateScheduleDraft(8.5, draft)).toContain('6-15')
  })

  it('只校验显示范围内的节次', () => {
    // 第 7 节非法但只显示 6 节时不报错
    draft[7] = { start: '10:00', end: '10:00' }
    expect(validateScheduleDraft(6, draft)).toBe('')
    expect(validateScheduleDraft(7, draft)).toContain('第 7 节')
  })

  it('结束时间早于开始时间时给出具体节次', () => {
    const bad = cloneTimes(SECTION_TIMES)
    bad[3] = { start: '10:05', end: '09:45' }
    expect(validateScheduleDraft(12, bad)).toContain('第 3 节')
  })
})

describe('课表时间设置：store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('保存后更新状态并写入本地存储', () => {
    const store = useScheduleSettingsStore()
    const draft = cloneTimes(store.times)
    draft[1] = { start: '08:00', end: '08:40' }
    expect(store.save(8, draft)).toBe('')
    expect(store.periodCount).toBe(8)
    expect(store.timeFor(1)).toEqual({ start: '08:00', end: '08:40' })
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY))
    expect(stored.periodCount).toBe(8)
    expect(stored.times[1]).toEqual({ start: '08:00', end: '08:40' })
  })

  it('重新创建 store 时读取已保存的设置', () => {
    const store = useScheduleSettingsStore()
    const draft = cloneTimes(store.times)
    draft[2] = { start: '09:10', end: '09:50' }
    store.save(6, draft)
    setActivePinia(createPinia())
    const reloaded = useScheduleSettingsStore()
    expect(reloaded.periodCount).toBe(6)
    expect(reloaded.timeFor(2)).toEqual({ start: '09:10', end: '09:50' })
  })

  it('非法草稿不改变当前状态', () => {
    const store = useScheduleSettingsStore()
    expect(store.save(4, store.times)).toContain('6-15')
    expect(store.periodCount).toBe(DEFAULT_PERIODS)
    const draft = cloneTimes(store.times)
    draft[1] = { start: '08:30', end: '08:00' }
    expect(store.save(12, draft)).toContain('第 1 节')
    expect(store.timeFor(1)).toEqual(SECTION_TIMES[1])
  })

  it('显示行数至少为每日节数，有更晚的课自动扩展', () => {
    const store = useScheduleSettingsStore()
    store.save(6, store.times)
    expect(store.visibleRows(0)).toBe(6)
    expect(store.visibleRows(9)).toBe(9)
    expect(store.visibleRows(4)).toBe(6)
  })

  it('恢复默认回到 12 节与内置时间', () => {
    const store = useScheduleSettingsStore()
    const draft = cloneTimes(store.times)
    draft[1] = { start: '08:00', end: '08:40' }
    store.save(7, draft)
    store.reset()
    expect(store.periodCount).toBe(DEFAULT_PERIODS)
    expect(store.timeFor(1)).toEqual(SECTION_TIMES[1])
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY))).toMatchObject({
      periodCount: DEFAULT_PERIODS
    })
  })
})
