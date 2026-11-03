import { describe, expect, it } from 'vitest'
import { ZfAcademicApi } from '../src/api/zf'

describe('会话快照课表视图', () => {
  it('没有课表时返回 null', () => {
    expect(new ZfAcademicApi().snapshotSchedule()).toBeNull()
  })

  it('返回可直接渲染的课表，并保留抓取时间', () => {
    const api = new ZfAcademicApi()
    api.schedule = {
      sessions: [{ courseId: 'c1', weekday: 1, startSection: 1, endSection: 2 }],
      courses: []
    }
    api.term = { xnm: '2026', xqm: '3', startDate: '2026-08-31' }
    api.fetchedAt = '2026-11-03T08:00:00+08:00'

    const view = api.snapshotSchedule()
    expect(view.sessions).toHaveLength(1)
    expect(view.offline).toBe(false)
    expect(view.source).toBe('教务系统（本机缓存）')
    expect(view.fetchedAt).toBe(api.fetchedAt)
    expect(view.rawPayload).toBe('')
    expect(view.term).toEqual(api.term)
  })
})
