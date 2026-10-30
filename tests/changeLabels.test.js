import { describe, expect, it } from 'vitest'
import {
  changeMeta,
  changeSubtitle,
  changeSummary,
  dateGroupLabel,
  detectedText,
  sectionText,
  snapshotPlace,
  snapshotTime,
  unreadBadgeText,
  weekdayText
} from '../src/utils/changeLabels'

const fromMonday = {
  weekday: 1,
  startSection: 3,
  endSection: 4,
  room: 'A101',
  weeksLabel: '1-16周'
}
const toWednesday = {
  weekday: 3,
  startSection: 5,
  endSection: 6,
  room: 'B203',
  weeksLabel: '1-16周'
}

describe('changeLabels', () => {
  it('节次与星期文案', () => {
    expect(weekdayText(1)).toBe('周一')
    expect(weekdayText(7)).toBe('周日')
    expect(weekdayText(0)).toBe('')
    expect(sectionText(3, 4)).toBe('3-4 节')
    expect(sectionText(5, 5)).toBe('5 节')
    expect(snapshotTime(fromMonday)).toBe('周一 3-4 节')
    expect(snapshotPlace(fromMonday)).toBe('周一 3-4 节 · A101')
  })

  it('四类变更的列表详情', () => {
    expect(changeSummary({ type: 'cancel', from: fromMonday })).toBe('周一 3-4 节 · A101')
    expect(changeSummary({ type: 'add', to: toWednesday })).toBe('周三 5-6 节 · B203')
    expect(changeSummary({
      type: 'room',
      from: { ...fromMonday, room: 'A101' },
      to: { ...fromMonday, room: 'B203' }
    })).toBe('A101 → B203')
    expect(changeSummary({ type: 'move', from: fromMonday, to: toWednesday }))
      .toBe('原：周一 3-4 节 · A101 → 新：周三 5-6 节 · B203')
    expect(changeSummary({
      type: 'move',
      from: { ...fromMonday, weeksLabel: '1-8周' },
      to: { ...fromMonday, weeksLabel: '9-16周' }
    })).toBe('1-8周 → 9-16周')
  })

  it('首页提醒条副文案', () => {
    expect(changeSubtitle({ type: 'cancel', courseName: '高等数学', from: fromMonday }))
      .toBe('高等数学 · 周一 3-4 节 · 停课')
    expect(changeSubtitle({ type: 'add', courseName: '大学英语', to: toWednesday }))
      .toBe('大学英语 · 周三 5-6 节 · 新增')
  })

  it('类型元信息与未读角标', () => {
    expect(changeMeta('cancel')).toEqual({ label: '停课', tone: 'danger', mark: '停' })
    expect(changeMeta('unknown').label).toBe('变更')
    expect(unreadBadgeText(3)).toBe('3')
    expect(unreadBadgeText(12)).toBe('9+')
    expect(unreadBadgeText(0)).toBe('0')
  })

  it('按今天/昨天/日期分组', () => {
    const now = new Date('2026-10-30T12:00:00+08:00')
    expect(dateGroupLabel('2026-10-30T07:10:12+08:00', now)).toBe('今天')
    expect(dateGroupLabel('2026-10-29T07:10:12+08:00', now)).toBe('昨天')
    expect(dateGroupLabel('2026-10-20T07:10:12+08:00', now)).toBe('更早')
    expect(dateGroupLabel('bad-value', now)).toBe('更早')
  })

  it('发现时间与空值', () => {
    expect(detectedText('2026-10-30T07:10:12+08:00')).toBe('10-30 07:10 发现')
    expect(detectedText('')).toBe('')
    expect(changeSummary(null)).toBe('')
    expect(changeSubtitle(null)).toBe('')
  })
})