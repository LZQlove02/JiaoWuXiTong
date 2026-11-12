import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// 这里刻意「不」mock ../src/api/zf 与 ../src/utils/sessionExpiry：
// P0-1 需要真实的 requestText 状态码判定，P0-3 需要真实的失效通知注册链路。
// 若把 sessionExpiry 换成空实现，setSessionExpiredHandler 不会真正注册处理器，
// 用例会变成「无论实现对错都绿」的假绿。
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
    loadSchedule: vi.fn(async () => {})
  })
}))

import { api, resetAcademicApi, setAcademicApi } from '../src/api'
import { ZfAcademicApi } from '../src/api/zf'
import {
  authGet,
  authPost,
  clearAuthCookies,
  loadAuthCookies,
  setAuthCookies,
  silentRelogin
} from '../src/api/nativeHttp'
import { clearScheduleChange } from '../src/native/scheduleChangeBridge'
import { useAuthStore } from '../src/stores/auth'

const flush = (ms = 20) => new Promise((resolve) => setTimeout(resolve, ms))

describe('P0-1 403 不判定为会话失效', () => {
  beforeEach(() => {
    vi.useRealTimers()
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
    authGet.mockResolvedValue({ status: 403, body: '' })
    authPost.mockResolvedValue({ status: 403, body: '' })
    // 必须建 store：setSessionExpiredHandler 只在这里注册
    useAuthStore()
    resetAcademicApi()
  })

  it('403 只标记 forbidden，不标记 sessionExpired，也不清理本机会话', async () => {
    setAcademicApi(new ZfAcademicApi())

    const error = await api.getMessages().catch((failure) => failure)

    expect(error).toBeInstanceOf(Error)
    expect(error.status).toBe(403)
    expect(error.forbidden).toBe(true)
    expect(error.sessionExpired).toBeFalsy()

    // 留出足够时间让「倘若误判」的清理链跑完
    await flush()
    expect(clearAuthCookies).not.toHaveBeenCalled()
  })

  it('对照组：401 仍判定会话失效并清理本机会话', async () => {
    authPost.mockResolvedValue({ status: 401, body: '' })
    setAcademicApi(new ZfAcademicApi())

    const error = await api.getMessages().catch((failure) => failure)

    expect(error.sessionExpired).toBe(true)
    expect(error.forbidden).toBeUndefined()

    await flush()
    expect(clearAuthCookies).toHaveBeenCalled()
  })
})

describe('P0-2 清理链首步失败仍完成登出', () => {
  beforeEach(() => {
    vi.useRealTimers()
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('clearScheduleChange 抛错时，后续清理与登录态置空照常执行', async () => {
    clearScheduleChange.mockRejectedValueOnce(new Error('原生桥接不可用'))
    const auth = useAuthStore()
    // 直接注入登录态：本用例只验证清理链的失败路径，不关心登录流程
    auth.user = { id: 'test-student-1', name: '测试' }
    expect(auth.isLoggedIn).toBe(true)

    await auth.logout()

    expect(clearScheduleChange).toHaveBeenCalled()
    expect(clearAuthCookies).toHaveBeenCalled()
    expect(auth.isLoggedIn).toBe(false)
  })
})

describe('P0-3 恢复超时后迟到的会话失效不被吞掉', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('超时后 restoreSession 才返回 401 时，仍触发本机会话清理', async () => {
    vi.useFakeTimers()
    let releaseLateRequest = null
    loadAuthCookies.mockResolvedValue({ restored: true, pending: false })
    // 模拟弱网：课表请求一直挂着，直到前端已经判定超时之后才拿到 401
    authGet.mockImplementation(
      () =>
        new Promise((resolve) => {
          releaseLateRequest = () => resolve({ status: 401, body: '' })
        })
    )

    const auth = useAuthStore()
    const booting = auth.bootstrap()

    await vi.advanceTimersByTimeAsync(15100)
    await expect(booting).resolves.toBe(false)
    expect(releaseLateRequest).toBeTypeOf('function')
    expect(clearAuthCookies).not.toHaveBeenCalled()

    // 迟到的 401 抵达：此时 Promise 已经被超时 reject 掉，实现必须自行上报失效
    releaseLateRequest()
    await vi.advanceTimersByTimeAsync(50)

    expect(clearAuthCookies).toHaveBeenCalled()
    vi.useRealTimers()
  })
})

const TERM = {
  xnm: '2026',
  xqm: '3',
  label: '2026-2027 学年第一学期',
  startDate: '2026-08-31',
  isCurrent: true
}
const SCHEDULE_BODY = JSON.stringify({
  xsxx: {
    XH: '2026001',
    XM: '测试学生',
    XNM: '2026',
    XQM: '3',
    XQMMC: '1',
    NJDM_ID: '2026'
  },
  kbList: []
})
const EMPTY_GRADES = JSON.stringify({ items: [], totalResult: 0 })

function useLoggedInApi() {
  const zf = new ZfAcademicApi()
  zf.term = { ...TERM }
  setAcademicApi(zf)
  return zf
}

describe('P0-4 会话失效后的静默重登', () => {
  beforeEach(() => {
    vi.useRealTimers()
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
    // 默认按「无 CASTGC」失败；具体用例再覆盖
    silentRelogin.mockResolvedValue({ ok: false, reason: 'no-ticket' })
    useAuthStore()
    resetAcademicApi()
  })

  it('静默换票成功时自动重放原请求，不清空本机会话', async () => {
    authGet.mockResolvedValue({ status: 200, body: SCHEDULE_BODY })
    authPost
      .mockResolvedValueOnce({ status: 401, body: '' })
      .mockResolvedValueOnce({ status: 200, body: EMPTY_GRADES })
    silentRelogin.mockResolvedValueOnce({
      ok: true,
      reason: 'ok',
      cookieHeader: 'JSESSIONID=new'
    })
    const zf = useLoggedInApi()

    const result = await api.getGrades(zf.term)

    expect(result.grades).toEqual([])
    expect(authPost).toHaveBeenCalledTimes(2)
    expect(silentRelogin).toHaveBeenCalledTimes(1)
    expect(setAuthCookies).toHaveBeenCalledWith('JSESSIONID=new')
    expect(clearAuthCookies).not.toHaveBeenCalled()
  })

  it('静默换票失败时清空本机数据并返回原始错误', async () => {
    authPost.mockResolvedValue({ status: 401, body: '' })
    silentRelogin.mockResolvedValueOnce({ ok: false, reason: 'no-ticket' })
    const zf = useLoggedInApi()

    const error = await api.getGrades(zf.term).catch((failure) => failure)

    expect(error.sessionExpired).toBe(true)
    expect(silentRelogin).toHaveBeenCalledTimes(1)
    expect(clearAuthCookies).toHaveBeenCalled()
  })

  it('并发失效请求只触发一次静默换票', async () => {
    authGet.mockResolvedValue({ status: 200, body: SCHEDULE_BODY })
    let postCalls = 0
    authPost.mockImplementation(async () => {
      postCalls += 1
      if (postCalls <= 5) return { status: 401, body: '' }
      return { status: 200, body: EMPTY_GRADES }
    })
    silentRelogin.mockResolvedValueOnce({
      ok: true,
      reason: 'ok',
      cookieHeader: 'JSESSIONID=new'
    })
    const zf = useLoggedInApi()

    const results = await Promise.all(
      Array.from({ length: 5 }, () => api.getGrades(zf.term))
    )

    expect(results).toHaveLength(5)
    expect(silentRelogin).toHaveBeenCalledTimes(1)
    expect(clearAuthCookies).not.toHaveBeenCalled()
  })

  it('主动退出后不再尝试静默重登', async () => {
    const auth = useAuthStore()
    auth.user = { id: 'test-student-1', name: '测试' }
    await auth.logout()
    silentRelogin.mockClear()
    authPost.mockResolvedValue({ status: 401, body: '' })
    const zf = useLoggedInApi()

    const error = await api.getGrades(zf.term).catch((failure) => failure)

    expect(error.sessionExpired).toBe(true)
    expect(silentRelogin).not.toHaveBeenCalled()
  })
})
