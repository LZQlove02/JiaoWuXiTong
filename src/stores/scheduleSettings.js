import { defineStore } from 'pinia'
import { ref } from 'vue'
import { SECTION_TIMES } from '../utils/date'

export const MIN_PERIODS = 6
export const MAX_PERIODS = 15
export const DEFAULT_PERIODS = 12

const STORAGE_KEY = 'jiaowu.schedule.settings.v1'
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/

export function isSectionTimeValid(entry) {
  if (!entry || typeof entry !== 'object') return false
  const start = String(entry.start || '')
  const end = String(entry.end || '')
  if (!TIME_PATTERN.test(start) || !TIME_PATTERN.test(end)) return false
  return toMinutes(start) < toMinutes(end)
}

function toMinutes(value) {
  const [hours, minutes] = String(value).split(':').map(Number)
  return hours * 60 + minutes
}

function defaultTimes() {
  const result = {}
  Object.keys(SECTION_TIMES).forEach((key) => {
    result[key] = { ...SECTION_TIMES[key] }
  })
  return result
}

function clampCount(value) {
  const count = Number(value)
  if (!Number.isInteger(count)) return DEFAULT_PERIODS
  return Math.min(Math.max(count, MIN_PERIODS), MAX_PERIODS)
}

/**
 * 合并默认值与本地存储；非法条目回退默认，避免坏数据把课表时间清空。
 */
export function normalizeScheduleSettings(raw) {
  const times = defaultTimes()
  const source = raw && typeof raw === 'object' && raw.times && typeof raw.times === 'object'
    ? raw.times
    : {}
  for (let section = 1; section <= MAX_PERIODS; section += 1) {
    const item = source[section] || source[String(section)]
    if (isSectionTimeValid(item)) {
      times[section] = { start: item.start, end: item.end }
    }
  }
  return { periodCount: clampCount(raw?.periodCount), times }
}

export function validateScheduleDraft(periodCount, times) {
  const count = Number(periodCount)
  if (!Number.isInteger(count) || count < MIN_PERIODS || count > MAX_PERIODS) {
    return `每日节数需为 ${MIN_PERIODS}-${MAX_PERIODS} 的整数`
  }
  for (let section = 1; section <= count; section += 1) {
    if (!isSectionTimeValid(times?.[section] || times?.[String(section)])) {
      return `第 ${section} 节时间无效：结束时间需晚于开始时间`
    }
  }
  return ''
}

function readStored() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch (error) {
    return null
  }
}

function persist(periodCount, times) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ periodCount, times }))
  } catch (error) {
    // 存储不可用时保留内存状态
  }
}

export const useScheduleSettingsStore = defineStore('scheduleSettings', () => {
  const initial = normalizeScheduleSettings(readStored())
  const periodCount = ref(initial.periodCount)
  const times = ref(initial.times)

  function timeFor(section) {
    return times.value[section] || { start: '', end: '' }
  }

  /** 显示行数至少为每日节数；有更晚的课则自动扩展，保证不隐藏课程。 */
  function visibleRows(maxSection = 0) {
    return Math.max(periodCount.value, Number(maxSection) || 0)
  }

  function save(nextCount, nextTimes) {
    const error = validateScheduleDraft(nextCount, nextTimes)
    if (error) return error
    const normalized = normalizeScheduleSettings({
      periodCount: nextCount,
      times: nextTimes
    })
    periodCount.value = normalized.periodCount
    times.value = normalized.times
    persist(periodCount.value, times.value)
    return ''
  }

  function reset() {
    const defaults = defaultTimes()
    periodCount.value = DEFAULT_PERIODS
    times.value = defaults
    persist(periodCount.value, times.value)
  }

  return {
    periodCount,
    times,
    timeFor,
    visibleRows,
    save,
    reset
  }
})
