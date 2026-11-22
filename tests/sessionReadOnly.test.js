import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// 票据确认失效后的「只读模式」：保留快照与缓存，只清凭据；api 层不再打网络。
const state = vi.hoisted(() => ({
  snapshot: null,
  snapshotClears: 0
}))

vi.mock('../src/api/nativeHttp', () => ({
  authGet: vi.fn(),
  authPost: vi.fn(),
  isDebugBuild: vi.fn(async () => false),
  loginWithSso: vi.fn(async () => ({ cookieHeader: 'CASTGC=test' })),
  setAuthCookies: vi.fn(async () => {}),
  clearAuthCookies: vi.fn(async () => {}),
  clearAuthDownloads: vi.fn(async () => {}),
  clearPublicDownloads: vi.fn(async () => {}),
  clearSsoSession: vi.fn(async () => {}),
  loadAuthCookies: vi.fn(async () => ({ restored: false, pending: false })),
  setKeepAlive: vi.fn(async () => ({ enabled: false })),
  silentRelogin: vi.fn(async () => ({ ok: false, reason: 'no-ticket' })),
  hasSsoTicket: vi.fn(async () => ({ hasTicket: false }))
}))
vi.mock('../src/api/zf', () => {
  const ZfAcademicApi = vi.fn(function () {
    this.applySnapshot = vi.fn(() => state.snapshot)
    this.snapshotSchedule = vi.fn(() => ({ term: '2026-2027-1', weeks: [] }))
    this.loginWithSso = vi.fn(async () => ({ id: 'student-1', name: '学生' }))
    this.restoreSession = vi.fn(async () => ({ id: 'student-1', name: '学生' }))
    this.getMessages = vi.fn(async () => ({ items: [] }))
    this.getSchedule = vi.fn(async () => ({ courses: [] }))
  })
  return {
    ZfAcademicApi,
    clearScheduleSnapshot: vi.fn(() => {
      state.snapshotClears += 1
    })
  }
})
vi.mock('../src/native/scheduleChangeBridge', () => ({
  clearScheduleChange: vi.fn(async () => {})
}))
vi.mock('../src/native/supportBridge', () => ({ clearCrashLog: vi.fn(async () => {}) }))
vi.mock('../src/utils/dataCache', () => ({ clearDataCaches: vi.fn() }))
vi.mock('../src/utils/portalCache', () => ({ clearPortalCache: vi.fn() }))
vi.mock('../src/stores/academic', () => ({
  useAcademicStore: () => ({
    reset: vi.fn(async () => {}),
    applySnapshotSchedule: vi.fn(),
    initTerms: vi.fn(async () => {}),
    loadSchedule: vi.fn(async () => {}),
    loadPortalNotices: vi.fn(async () => {}),
    loadMessages: vi.fn(async () => {})
  })
}))

import { api } from '../src/api'
import { ZfAcademicApi } from '../src/api/zf'
import { useAuthStore } from '../src/stores/auth'

const EXPIRED_AT_KEY = 'jiaowu.session.expiredAt'
const EXPIRED_REASON_KEY = 'jiaowu.session.expiredReason'
const DAY = 24 * 60 * 60 * 1000

function markExpiredLocally(reason = 'login-form', age = DAY) {
  localStorage.setItem(EXPIRED_AT_KEY, String(Date.now() - age))
  localStorage.setItem(EXPIRED_REASON_KEY, reason)
}

describe('票据失效后的只读模式', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
    state.snapshot = { id: 'cached-student', name: '缓存学生' }
    state.snapshotClears = 0
  })

  it('冷启动用快照续上只读界面，不重新请求教务', async () => {
    markExpiredLocally()
    const auth = useAuthStore()

    const restored = await auth.bootstrap()

    expect(restored).toBe(true)
    expect(auth.isLoggedIn).toBe(true)
    expect(auth.sessionExpired).toBe(true)
    expect(auth.expiredReason).toBe('login-form')
    expect(auth.user.id).toBe('cached-student')
    // 只读续接不走网络恢复
    expect(ZfAcademicApi.mock.instances[0].restoreSession).not.toHaveBeenCalled()
  })

  it('只读模式下 api 直接拒绝，不再用失效 Cookie 打网络', async () => {
    markExpiredLocally()
    const auth = useAuthStore()
    await auth.bootstrap()
    const zf = ZfAcademicApi.mock.instances[0]

    const error = await api.getMessages().catch((failure) => failure)

    expect(error).toBeInstanceOf(Error)
    expect(error.message).toBe('登录已过期，请重新登录')
    expect(zf.getMessages).not.toHaveBeenCalled()
  })

  it('超过 7 天的只读缓存直接丢弃，回到未登录', async () => {
    markExpiredLocally('login-form', 8 * DAY)
    const auth = useAuthStore()

    const restored = await auth.bootstrap()

    expect(restored).toBe(false)
    expect(auth.isLoggedIn).toBe(false)
    expect(auth.sessionExpired).toBe(false)
    expect(state.snapshotClears).toBeGreaterThan(0)
  })

  it('重新登录成功后清掉只读标记与旧快照', async () => {
    markExpiredLocally()
    const auth = useAuthStore()
    await auth.bootstrap()
    expect(auth.sessionExpired).toBe(true)

    await auth.loginWithSso()

    expect(auth.sessionExpired).toBe(false)
    expect(auth.expiredReason).toBeNull()
    expect(auth.isLoggedIn).toBe(true)
    expect(state.snapshotClears).toBeGreaterThan(0)
    expect(localStorage.getItem(EXPIRED_AT_KEY)).toBeNull()
  })
})
