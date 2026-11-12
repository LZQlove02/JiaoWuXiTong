import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const bridge = vi.hoisted(() => ({
  applyScheduleChange: vi.fn()
}))

vi.mock('../src/native/scheduleChangeBridge', () => ({
  addScheduleChangeListener: vi.fn(async () => {}),
  applyScheduleChange: bridge.applyScheduleChange,
  getScheduleChangeState: vi.fn(async () => null),
  markAllChangesRead: vi.fn(async () => null),
  maybeRequestChangeNotificationPermission: vi.fn(async () => ({ asked: false })),
  openScheduleChangeNotificationSettings: vi.fn(async () => {}),
  requestChangeNotificationPermission: vi.fn(async () => ({ granted: false })),
  resetCheckTimes: vi.fn(async () => null),
  saveCheckTimes: vi.fn(async () => null),
  scheduleChangeAvailable: vi.fn(() => true),
  setScheduleChangeEnabled: vi.fn(async () => null),
  getCheckTimes: vi.fn(async () => null)
}))

import { useChangesStore } from '../src/stores/changes'

const term = { xnm: '2026', xqm: '3', startDate: '2026-08-31' }
const payload = (raw) => ({ rawPayload: raw, offline: false, term })

describe('P1-8 课表对账串行排队，不丢弃并发调用', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    bridge.applyScheduleChange.mockReset()
    bridge.applyScheduleChange.mockResolvedValue(null)
  })

  it('两次 applyFromSchedule 都会进原生基线', async () => {
    const store = useChangesStore()

    store.applyFromSchedule(payload('first'))
    store.applyFromSchedule(payload('second'))
    await store.applyFromSchedule(payload('third'))

    expect(bridge.applyScheduleChange).toHaveBeenCalledTimes(3)
    const raws = bridge.applyScheduleChange.mock.calls.map(([input]) => input.raw)
    expect(raws).toEqual(['first', 'second', 'third'])
  })

  it('单次失败不阻断后续队列', async () => {
    const store = useChangesStore()
    bridge.applyScheduleChange
      .mockRejectedValueOnce(new Error('原生引擎忙'))
      .mockResolvedValueOnce(null)

    store.applyFromSchedule(payload('bad'))
    await store.applyFromSchedule(payload('good'))

    expect(bridge.applyScheduleChange).toHaveBeenCalledTimes(2)
  })
})
