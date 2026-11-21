import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// 静默换票与课表刷新都发生在 bootstrap 之后的异步链里，测试通过共享 state 控制结果
const state = vi.hoisted(() => ({
  snapshot: null,
  reloginCalls: 0,
  reloginResults: []
}))

const academic = vi.hoisted(() => ({
  reset: vi.fn(async () => {}),
  initTerms: vi.fn(async () => {}),
  loadSchedule: vi.fn(async () => {}),
  loadPortalNotices: vi.fn(async () => {}),
  loadMessages: vi.fn(async () => {}),
  applySnapshotSchedule: vi.fn(),
  currentTerm: '2026-2027-1',
  portalNoticesOffline: false,
  messagesOffline: false
}))

vi.mock('../src/api', () => ({
  resetAcademicApi: vi.fn(),
  setAcademicApi: vi.fn()
}))
vi.mock('../src/api/zf', () => {
  const ZfAcademicApi = vi.fn(function () {
    this.applySnapshot = vi.fn(() => state.snapshot)
    this.snapshotSchedule = vi.fn(() => ({ term: '2026-2027-1', weeks: [] }))
    this.loginWithSso = vi.fn(async () => ({ id: 'test-student-1', name: '测试' }))
    this.restoreSession = vi.fn(async () => {
      const error = new Error('会话失效')
      error.sessionExpired = true
      throw error
    })
    this.reloginSilently = vi.fn(async () => {
      state.reloginCalls += 1
      const next = state.reloginResults.shift()
      if (next) return next
      return { ok: false, reason: 'network' }
    })
  })
  return { ZfAcademicApi, clearScheduleSnapshot: vi.fn() }
})
vi.mock('../src/api/nativeHttp', () => ({
  clearAuthCookies: vi.fn(async () => {}),
  clearAuthDownloads: vi.fn(async () => {}),
  clearPublicDownloads: vi.fn(async () => {}),
  clearSsoSession: vi.fn(async () => {}),
  loadAuthCookies: vi.fn(async () => ({ restored: true })),
  setKeepAlive: vi.fn(async () => ({ enabled: true })),
  silentRelogin: vi.fn(async () => ({ ok: false, reason: 'no-ticket' })),
  hasSsoTicket: vi.fn(async () => ({ hasTicket: true }))
}))
vi.mock('../src/native/scheduleChangeBridge', () => ({
  clearScheduleChange: vi.fn(async () => {})
}))
vi.mock('../src/native/supportBridge', () => ({
  clearCrashLog: vi.fn(async () => {})
}))
vi.mock('../src/utils/sessionExpiry', () => ({
  setSessionExpiredHandler: vi.fn(),
  notifySessionExpired: vi.fn(async () => false)
}))
vi.mock('../src/utils/dataCache', () => ({ clearDataCaches: vi.fn() }))
vi.mock('../src/utils/portalCache', () => ({ clearPortalCache: vi.fn() }))
vi.mock('../src/stores/academic', () => ({
  useAcademicStore: () => academic
}))

import { useAuthStore } from '../src/stores/auth'

async function bootstrapWithSnapshot() {
  const auth = useAuthStore()
  await auth.bootstrap()
  return auth
}

describe('会话暂时不可用时保留登录态', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    state.snapshot = { id: 'cached-student', name: '缓存学生' }
    state.reloginCalls = 0
    state.reloginResults = []
    academic.portalNoticesOffline = false
    academic.messagesOffline = false
  })

  it('静默换票遇到网络失败时保持登录态、保留数据并标记挂起', async () => {
    state.reloginResults = [{ ok: false, reason: 'network' }]
    const auth = await bootstrapWithSnapshot()

    await vi.waitFor(() => expect(auth.sessionSuspended).toBe(true))

    const nativeHttp = await import('../src/api/nativeHttp')
    const { clearScheduleChange } = await import('../src/native/scheduleChangeBridge')
    const { clearScheduleSnapshot } = await import('../src/api/zf')
    const { clearDataCaches } = await import('../src/utils/dataCache')
    const { clearPortalCache } = await import('../src/utils/portalCache')

    // 弱网不等于会话失效：登录态与已加载数据都要留着
    expect(auth.isLoggedIn).toBe(true)
    expect(auth.user.id).toBe('cached-student')
    expect(academic.reset).not.toHaveBeenCalled()
    expect(nativeHttp.clearAuthCookies).not.toHaveBeenCalled()
    expect(clearScheduleChange).not.toHaveBeenCalled()
    expect(clearScheduleSnapshot).not.toHaveBeenCalled()
    expect(clearDataCaches).not.toHaveBeenCalled()
    expect(clearPortalCache).not.toHaveBeenCalled()
    expect(nativeHttp.clearAuthDownloads).not.toHaveBeenCalled()
    expect(nativeHttp.clearPublicDownloads).not.toHaveBeenCalled()
  })

  it('票据明确失效时仍然清空本机会话并回到未登录', async () => {
    state.reloginResults = [{ ok: false, reason: 'login-form' }]
    const auth = await bootstrapWithSnapshot()

    await vi.waitFor(() => expect(auth.isLoggedIn).toBe(false))

    const nativeHttp = await import('../src/api/nativeHttp')
    const { clearScheduleSnapshot } = await import('../src/api/zf')

    expect(auth.sessionSuspended).toBe(false)
    expect(nativeHttp.clearAuthCookies).toHaveBeenCalled()
    expect(clearScheduleSnapshot).toHaveBeenCalled()
  })

  it('手动重试成功后恢复登录态并刷新实时数据', async () => {
    state.reloginResults = [{ ok: false, reason: 'timeout' }]
    const auth = await bootstrapWithSnapshot()
    await vi.waitFor(() => expect(auth.sessionSuspended).toBe(true))

    state.reloginResults = [{ ok: true, profile: { id: 'cached-student', name: '缓存学生' } }]
    const recovered = await auth.retrySession()

    expect(recovered).toBe(true)
    expect(auth.sessionSuspended).toBe(false)
    expect(auth.isLoggedIn).toBe(true)
    expect(academic.initTerms).toHaveBeenCalled()
    expect(academic.loadSchedule).toHaveBeenCalledWith('2026-2027-1', { force: true })
  })

  it('会话恢复后把断网期间降级成缓存的板块补成实时数据', async () => {
    state.reloginResults = [{ ok: false, reason: 'network' }]
    const auth = await bootstrapWithSnapshot()
    await vi.waitFor(() => expect(auth.sessionSuspended).toBe(true))

    // 断网刷新失败后，通知与消息都降级成了缓存
    academic.portalNoticesOffline = true
    academic.messagesOffline = true
    state.reloginResults = [{ ok: true, profile: { id: 'cached-student', name: '缓存学生' } }]

    const recovered = await auth.retrySession()

    expect(recovered).toBe(true)
    expect(academic.loadPortalNotices).toHaveBeenCalled()
    expect(academic.loadMessages).toHaveBeenCalled()
  })

  it('暂时失败后的短冷却内不再重复拉起静默换票', async () => {
    state.reloginResults = [{ ok: false, reason: 'network' }]
    const auth = await bootstrapWithSnapshot()
    await vi.waitFor(() => expect(auth.sessionSuspended).toBe(true))

    const { setSessionExpiredHandler } = await import('../src/utils/sessionExpiry')
    const handler = setSessionExpiredHandler.mock.calls.at(-1)?.[0]
    expect(handler).toBeTypeOf('function')

    state.reloginCalls = 0
    await handler()
    await handler()

    expect(state.reloginCalls).toBe(0)
    expect(auth.sessionSuspended).toBe(true)
  })
})
