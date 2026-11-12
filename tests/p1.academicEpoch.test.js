import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const api = vi.hoisted(() => ({
  getMessages: vi.fn(),
  getEvaluations: vi.fn()
}))

vi.mock('../src/api', () => ({ api }))
vi.mock('../src/native/scheduleChangeBridge', () => ({
  addScheduleChangeListener: vi.fn(async () => {}),
  applyScheduleChange: vi.fn(async () => null),
  getScheduleChangeState: vi.fn(async () => null),
  markAllChangesRead: vi.fn(async () => null),
  maybeRequestChangeNotificationPermission: vi.fn(async () => ({ asked: false })),
  openScheduleChangeNotificationSettings: vi.fn(async () => {}),
  requestChangeNotificationPermission: vi.fn(async () => ({ granted: false })),
  resetCheckTimes: vi.fn(async () => null),
  saveCheckTimes: vi.fn(async () => null),
  scheduleChangeAvailable: vi.fn(() => false),
  setScheduleChangeEnabled: vi.fn(async () => null),
  getCheckTimes: vi.fn(async () => null)
}))

import { useAcademicStore } from '../src/stores/academic'

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('P1-5 退出后旧响应不得落进新会话', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    Object.values(api).forEach((fn) => fn.mockReset())
  })

  it('loadMessages 在 reset 之后返回时丢弃结果', async () => {
    const pending = deferred()
    api.getMessages.mockReturnValue(pending.promise)

    const store = useAcademicStore()
    const loading = store.loadMessages('unread')
    // 模拟退出登录：reset 递增 dataEpoch
    store.reset()

    pending.resolve({ total: 1, items: [{ id: 'm1', title: '上个账号的消息' }] })
    await loading

    expect(store.messages).toBeNull()
    expect(store.messagesLoading).toBe(false)
  })

  it('loadEvaluations 在 reset 之后失败时不得写入错误态', async () => {
    const pending = deferred()
    api.getEvaluations.mockReturnValue(pending.promise)

    const store = useAcademicStore()
    const loading = store.loadEvaluations()
    store.reset()

    pending.reject(new Error('无法连接教务系统'))
    await loading

    expect(store.evaluations).toBeNull()
    expect(store.errors.evaluations).toBe('')
  })
})
