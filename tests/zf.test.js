import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authGet } from '../src/api/nativeHttp'
import { ZfAcademicApi, clearScheduleSnapshot } from '../src/api/zf'

vi.mock('../src/api/nativeHttp', () => ({
  authGet: vi.fn(),
  loginWithSso: vi.fn(),
  setAuthCookies: vi.fn()
}))

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

const networkError = () => new Error('原生 HTTP 插件仅在 App 内可用')

describe('ZfAcademicApi 离线快照', () => {
  beforeEach(() => {
    localStorage.clear()
    authGet.mockReset()
  })

  it('网络不可达时用快照恢复会话并标注离线来源', async () => {
    authGet.mockRejectedValue(networkError())
    localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot))
    const api = new ZfAcademicApi()
    const profile = await api.restoreSession()
    expect(profile.id).toBe('20256030154')
    const schedule = await api.getSchedule()
    expect(schedule.source).toBe('教务系统（离线缓存）')
    expect(schedule.courses).toHaveLength(1)
  })

  it('applySnapshot 只读快照不发请求', async () => {
    authGet.mockRejectedValue(networkError())
    localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot))
    const api = new ZfAcademicApi()
    const profile = api.applySnapshot()
    expect(profile.id).toBe('20256030154')
    expect(authGet).not.toHaveBeenCalled()
    await expect(api.getSchedule()).resolves.toMatchObject({
      source: '教务系统（离线缓存）'
    })
  })

  it('没有快照时向上抛出网络错误', async () => {
    authGet.mockRejectedValue(networkError())
    const api = new ZfAcademicApi()
    await expect(api.restoreSession()).rejects.toMatchObject({ network: true })
  })

  it('刚登录校验证书失败时不回退旧快照', async () => {
    authGet.mockRejectedValue(networkError())
    localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot))
    const api = new ZfAcademicApi()
    await expect(api.restoreSession({ allowSnapshot: false })).rejects.toMatchObject({
      network: true
    })
  })

  it('被重定向到 http 登录页时判定会话失效且不套用快照', async () => {
    authGet.mockRejectedValue(new Error('仅允许 HTTPS 请求'))
    localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot))
    const api = new ZfAcademicApi()
    await expect(api.restoreSession()).rejects.toMatchObject({ sessionExpired: true })
  })

  it('clearScheduleSnapshot 清除快照', () => {
    localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot))
    clearScheduleSnapshot()
    expect(localStorage.getItem(SNAPSHOT_KEY)).toBeNull()
  })
})
