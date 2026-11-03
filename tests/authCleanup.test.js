import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../src/api', () => ({
  resetAcademicApi: vi.fn(),
  setAcademicApi: vi.fn()
}))
vi.mock('../src/api/zf', () => {
  const ZfAcademicApi = vi.fn(function () {
    this.loginWithSso = vi.fn(async () => ({ id: 'test-student-1', name: '测试' }))
    this.restoreSession = vi.fn(async () => ({ id: 'test-student-1', name: '测试' }))
  })
  return { ZfAcademicApi, clearScheduleSnapshot: vi.fn() }
})
vi.mock('../src/api/nativeHttp', () => ({
  clearAuthCookies: vi.fn(async () => {}),
  clearAuthDownloads: vi.fn(async () => {}),
  clearPublicDownloads: vi.fn(async () => {}),
  clearSsoSession: vi.fn(async () => {}),
  loadAuthCookies: vi.fn(async () => ({ restored: false })),
  setKeepAlive: vi.fn(async () => ({ enabled: false }))
}))
vi.mock('../src/native/scheduleChangeBridge', () => ({
  clearScheduleChange: vi.fn(async () => {})
}))
vi.mock('../src/native/supportBridge', () => ({
  clearCrashLog: vi.fn(async () => {})
}))
vi.mock('../src/utils/sessionExpiry', () => ({ setSessionExpiredHandler: vi.fn() }))
vi.mock('../src/utils/dataCache', () => ({ clearDataCaches: vi.fn() }))
vi.mock('../src/utils/portalCache', () => ({ clearPortalCache: vi.fn() }))
vi.mock('../src/stores/academic', () => ({
  useAcademicStore: () => ({ reset: vi.fn(async () => {}) })
}))

import { useAuthStore } from '../src/stores/auth'

describe('退出登录的本机数据清理', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('停止保持登录，并清理附件、崩溃摘要与门户缓存', async () => {
    const auth = useAuthStore()
    await auth.logout()

    const nativeHttp = await import('../src/api/nativeHttp')
    const support = await import('../src/native/supportBridge')
    const { clearPortalCache } = await import('../src/utils/portalCache')
    const { clearDataCaches } = await import('../src/utils/dataCache')

    expect(nativeHttp.setKeepAlive).toHaveBeenCalledWith(false)
    expect(nativeHttp.clearAuthDownloads).toHaveBeenCalled()
    expect(nativeHttp.clearPublicDownloads).toHaveBeenCalled()
    expect(support.clearCrashLog).toHaveBeenCalled()
    expect(clearPortalCache).toHaveBeenCalled()
    expect(clearDataCaches).toHaveBeenCalled()
  })

  it('冷启动恢复判定会话失效时执行完整本机清理', async () => {
    const nativeHttp = await import('../src/api/nativeHttp')
    const { ZfAcademicApi } = await import('../src/api/zf')
    nativeHttp.loadAuthCookies.mockResolvedValueOnce({ restored: true })
    ZfAcademicApi.mockImplementationOnce(function () {
      this.applySnapshot = vi.fn(() => null)
      this.restoreSession = vi.fn(async () => {
        const error = new Error('会话失效')
        error.sessionExpired = true
        throw error
      })
    })

    const auth = useAuthStore()
    await auth.bootstrap()

    const { clearScheduleSnapshot } = await import('../src/api/zf')
    const { clearScheduleChange } = await import('../src/native/scheduleChangeBridge')
    const { clearPortalCache } = await import('../src/utils/portalCache')
    const { clearDataCaches } = await import('../src/utils/dataCache')

    expect(nativeHttp.clearAuthCookies).toHaveBeenCalled()
    expect(clearScheduleChange).toHaveBeenCalled()
    expect(clearScheduleSnapshot).toHaveBeenCalled()
    expect(clearDataCaches).toHaveBeenCalled()
    expect(clearPortalCache).toHaveBeenCalled()
    expect(nativeHttp.clearAuthDownloads).toHaveBeenCalled()
    expect(nativeHttp.clearPublicDownloads).toHaveBeenCalled()
  })
})
