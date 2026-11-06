import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const state = vi.hoisted(() => ({
  hasSnapshot: true,
  profile: { id: 'test-student-1', name: '测试', className: '', major: '', college: '', avatar: '' }
}))

vi.mock('../src/api', () => ({
  resetAcademicApi: vi.fn(),
  setAcademicApi: vi.fn()
}))
vi.mock('../src/api/zf', () => {
  const ZfAcademicApi = vi.fn(function () {
    this.applySnapshot = vi.fn(() => (state.hasSnapshot ? state.profile : null))
    this.snapshotSchedule = vi.fn(() => ({ term: null, courses: [], sessions: [] }))
    this.restoreSession = vi.fn(async () => state.profile)
    this.loginWithSso = vi.fn(async () => state.profile)
  })
  return { ZfAcademicApi, clearScheduleSnapshot: vi.fn() }
})
vi.mock('../src/api/nativeHttp', () => ({
  authGet: vi.fn(),
  authPost: vi.fn(),
  clearAuthCookies: vi.fn(async () => {}),
  clearAuthDownloads: vi.fn(async () => {}),
  clearPublicDownloads: vi.fn(async () => {}),
  clearSsoSession: vi.fn(async () => {}),
  loadAuthCookies: vi.fn(async () => ({ restored: false, pending: false })),
  setAuthCookies: vi.fn(),
  setKeepAlive: vi.fn(async () => ({ enabled: false }))
}))
vi.mock('../src/native/scheduleChangeBridge', () => ({
  clearScheduleChange: vi.fn(async () => {})
}))
vi.mock('../src/native/supportBridge', () => ({ clearCrashLog: vi.fn(async () => {}) }))
vi.mock('../src/utils/sessionExpiry', () => ({ setSessionExpiredHandler: vi.fn() }))
vi.mock('../src/utils/dataCache', () => ({ clearDataCaches: vi.fn() }))
vi.mock('../src/utils/portalCache', () => ({ clearPortalCache: vi.fn() }))
vi.mock('../src/stores/academic', () => ({
  useAcademicStore: () => ({
    reset: vi.fn(async () => {}),
    applySnapshotSchedule: vi.fn(),
    initTerms: vi.fn(async () => {}),
    loadSchedule: vi.fn(async () => {})
  })
}))

import { clearAuthCookies, loadAuthCookies } from '../src/api/nativeHttp'
import { useAuthStore } from '../src/stores/auth'

describe('会话解密暂时失败时的冷启动恢复', () => {
  beforeEach(() => {
    vi.useRealTimers()
    setActivePinia(createPinia())
    loadAuthCookies.mockReset()
    loadAuthCookies.mockResolvedValue({ restored: false, pending: false })
    clearAuthCookies.mockClear()
    state.hasSnapshot = true
  })

  it('有快照时保留登录态且不清空本地会话', async () => {
    vi.useFakeTimers()
    loadAuthCookies.mockResolvedValue({ restored: false, pending: true })
    const auth = useAuthStore()
    await auth.bootstrap()
    expect(auth.isLoggedIn).toBe(true)
    expect(clearAuthCookies).not.toHaveBeenCalled()
    // 收尾延迟重试定时器，避免跨用例泄漏
    await vi.advanceTimersByTimeAsync(700)
  })

  it('无快照且重试仍失败时判定未登录，但不清空本地会话', async () => {
    vi.useFakeTimers()
    state.hasSnapshot = false
    loadAuthCookies.mockResolvedValue({ restored: false, pending: true })
    const auth = useAuthStore()
    const booting = auth.bootstrap()
    await vi.advanceTimersByTimeAsync(700)
    await expect(booting).resolves.toBe(false)
    expect(auth.isLoggedIn).toBe(false)
    expect(clearAuthCookies).not.toHaveBeenCalled()
  })

  it('无快照但重试成功时正常恢复登录', async () => {
    vi.useFakeTimers()
    state.hasSnapshot = false
    loadAuthCookies
      .mockResolvedValueOnce({ restored: false, pending: true })
      .mockResolvedValueOnce({ restored: true, pending: false })
    const auth = useAuthStore()
    const booting = auth.bootstrap()
    await vi.advanceTimersByTimeAsync(700)
    await expect(booting).resolves.toBe(true)
    expect(auth.isLoggedIn).toBe(true)
  })
})
