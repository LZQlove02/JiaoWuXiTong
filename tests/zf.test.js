import { beforeEach, describe, expect, it } from 'vitest'
import { ZfAcademicApi, clearScheduleSnapshot } from '../src/api/zf'

const SNAPSHOT_KEY = 'lzpu.zf.snapshot.v1'

const term = {
  xnm: '2026',
  xqm: '3',
  label: '2026-2027 学年第一学期',
  startDate: '2026-08-31',
  isCurrent: true
}

const snapshot = {
  profile: { id: '20256030154', name: '测试', className: '', major: '', college: '', avatar: '' },
  term,
  schedule: {
    term,
    courses: [
      { id: 'c1', code: '', name: '高等数学', teacher: '', credits: 0, type: '', color: '#1B5FAA' }
    ],
    sessions: [
      { courseId: 'c1', weekday: 1, startSection: 1, endSection: 2, weeks: [1], room: 'A101' }
    ]
  },
  fetchedAt: '2026-10-28T00:00:00.000Z'
}

describe('ZfAcademicApi 离线快照', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('网络不可达时用快照恢复会话并标注离线来源', async () => {
    localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot))
    const api = new ZfAcademicApi()
    const profile = await api.restoreSession()
    expect(profile.id).toBe('20256030154')
    const schedule = await api.getSchedule()
    expect(schedule.source).toBe('教务系统（离线缓存）')
    expect(schedule.courses).toHaveLength(1)
  })

  it('没有快照时向上抛出网络错误', async () => {
    const api = new ZfAcademicApi()
    await expect(api.restoreSession()).rejects.toMatchObject({ network: true })
  })

  it('clearScheduleSnapshot 清除快照', () => {
    localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot))
    clearScheduleSnapshot()
    expect(localStorage.getItem(SNAPSHOT_KEY)).toBeNull()
  })
})
