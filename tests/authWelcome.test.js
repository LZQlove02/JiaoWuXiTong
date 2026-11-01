import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../src/api', () => ({
  resetAcademicApi: vi.fn(),
  setAcademicApi: vi.fn()
}))
vi.mock('../src/api/zf', () => {
  const ZfAcademicApi = vi.fn(function () {
    this.loginWithSso = vi.fn(async () => ({ id: '20256030001', name: '测试' }))
    this.restoreSession = vi.fn(async () => ({ id: '20256030001', name: '测试' }))
  })
  return { ZfAcademicApi, clearScheduleSnapshot: vi.fn() }
})
vi.mock('../src/api/nativeHttp', () => ({
  clearAuthCookies: vi.fn(async () => {}),
  clearSsoSession: vi.fn(async () => {}),
  loadAuthCookies: vi.fn(async () => ({ restored: false }))
}))
vi.mock('../src/native/scheduleChangeBridge', () => ({
  clearScheduleChange: vi.fn(async () => {})
}))
vi.mock('../src/utils/dataCache', () => ({ clearDataCaches: vi.fn() }))
vi.mock('../src/utils/sessionExpiry', () => ({ setSessionExpiredHandler: vi.fn() }))
vi.mock('../src/stores/academic', () => ({
  useAcademicStore: () => ({ reset: vi.fn(async () => {}) })
}))

import { useAuthStore } from '../src/stores/auth'

describe('欢迎提示的登录标记', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('登录成功后标记一次，消费后清除', async () => {
    const auth = useAuthStore()
    expect(auth.consumeJustLoggedIn()).toBe(false)
    await auth.loginWithSso()
    expect(auth.isLoggedIn).toBe(true)
    expect(auth.consumeJustLoggedIn()).toBe(true)
    expect(auth.consumeJustLoggedIn()).toBe(false)
  })

  it('冷启动恢复会话不算本次登录', async () => {
    const auth = useAuthStore()
    await auth.bootstrap()
    expect(auth.consumeJustLoggedIn()).toBe(false)
  })
})
