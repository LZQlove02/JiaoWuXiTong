import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../src/native/scheduleChangeBridge', () => ({
  scheduleChangeAvailable: vi.fn(() => true),
  applyScheduleChange: vi.fn(),
  getScheduleChangeState: vi.fn(),
  markAllChangesRead: vi.fn(),
  setScheduleChangeEnabled: vi.fn(),
  requestChangeNotificationPermission: vi.fn(),
  maybeRequestChangeNotificationPermission: vi.fn(() => Promise.resolve({ asked: false })),
  openScheduleChangeNotificationSettings: vi.fn(),
  clearScheduleChange: vi.fn(),
  addScheduleChangeListener: vi.fn(() => () => {})
}))

import {
  applyScheduleChange,
  getScheduleChangeState,
  markAllChangesRead,
  maybeRequestChangeNotificationPermission,
  requestChangeNotificationPermission,
  scheduleChangeAvailable,
  setScheduleChangeEnabled
} from '../src/native/scheduleChangeBridge'
import { useAuthStore } from '../src/stores/auth'
import { useChangesStore } from '../src/stores/changes'

const entry = (overrides = {}) => ({
  id: 'c_1',
  type: 'cancel',
  courseId: 'C1',
  courseName: '高等数学',
  from: { weekday: 1, startSection: 3, endSection: 4, room: 'A101', weeksLabel: '1-16周' },
  to: null,
  detectedAt: '2026-10-30T07:10:12+08:00',
  detectedBy: 'background',
  quiet: false,
  read: false,
  notified: true,
  ...overrides
})

const state = (overrides = {}) => ({
  enabled: true,
  unread: 1,
  notificationsEnabled: true,
  entries: [entry()],
  ...overrides
})

describe('changes store', () => {
  let changes

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    scheduleChangeAvailable.mockReturnValue(true)
    getScheduleChangeState.mockResolvedValue(state())
    markAllChangesRead.mockResolvedValue(state({ unread: 0, entries: [entry({ read: true })] }))
    setScheduleChangeEnabled.mockResolvedValue(state({ enabled: false, unread: 0 }))
    requestChangeNotificationPermission.mockResolvedValue({ granted: true })
    useAuthStore().user = { id: '20256030001', name: '测试' }
    changes = useChangesStore()
  })

  it('init 读取原生状态并给出提醒条数据', async () => {
    await changes.init()
    expect(changes.loaded).toBe(true)
    expect(changes.unread).toBe(1)
    expect(changes.bannerVisible).toBe(true)
    expect(changes.bannerCount).toBe(1)
    expect(changes.latest.id).toBe('c_1')
  })

  it('原生返回 null 时结束加载态并给出错误态', async () => {
    getScheduleChangeState.mockResolvedValue(null)
    await changes.init()
    expect(changes.loaded).toBe(true)
    expect(changes.error).toBe('变更记录加载失败')
    expect(changes.bannerVisible).toBe(false)
  })

  it('实时课表交给原生对账时带上学号与学期', async () => {
    applyScheduleChange.mockResolvedValue(state({ unread: 3 }))
    await changes.applyFromSchedule({
      source: '教务系统（实时）',
      rawPayload: '{"kbList":[]}',
      term: { xnm: '2026', xqm: '3', startDate: '2026-08-31' }
    })
    expect(applyScheduleChange).toHaveBeenCalledTimes(1)
    const payload = applyScheduleChange.mock.calls[0][0]
    expect(payload.raw).toBe('{"kbList":[]}')
    expect(payload.studentId).toBe('20256030001')
    expect(payload.term).toEqual({ xnm: '2026', xqm: '3', startDate: '2026-08-31' })
    expect(payload.sectionTimes[1]).toEqual({ start: '08:20', end: '09:00' })
    expect(changes.unread).toBe(3)
    expect(maybeRequestChangeNotificationPermission).toHaveBeenCalledTimes(1)
  })

  it('离线快照或缺少原文时不参与对账', async () => {
    await changes.applyFromSchedule({
      offline: true,
      source: '教务系统（离线缓存）',
      rawPayload: '{"kbList":[]}',
      term: { xnm: '2026', xqm: '3', startDate: '2026-08-31' }
    })
    await changes.applyFromSchedule({
      offline: false,
      source: '教务系统（实时）',
      rawPayload: '',
      term: { xnm: '2026', xqm: '3', startDate: '2026-08-31' }
    })
    expect(applyScheduleChange).not.toHaveBeenCalled()
    expect(maybeRequestChangeNotificationPermission).not.toHaveBeenCalled()
  })

  it('进入列表后全部已读，红点归零', async () => {
    await changes.init()
    await changes.markAllRead()
    expect(markAllChangesRead).toHaveBeenCalledTimes(1)
    expect(changes.unread).toBe(0)
    expect(changes.bannerVisible).toBe(false)
    expect(changes.entries[0].read).toBe(true)
  })

  it('关闭开关后未读归零、提醒条隐藏', async () => {
    await changes.init()
    await changes.setEnabled(false)
    expect(setScheduleChangeEnabled).toHaveBeenCalledWith(false)
    expect(changes.enabled).toBe(false)
    expect(changes.unread).toBe(0)
    expect(changes.bannerVisible).toBe(false)
  })

  it('申请通知权限后同步权限状态', async () => {
    requestChangeNotificationPermission.mockResolvedValue({ granted: false })
    const granted = await changes.requestNotificationPermission()
    expect(granted).toBe(false)
    expect(changes.notificationsEnabled).toBe(false)
  })

  it('Web 端（无插件）保持空态，不抛错', async () => {
    scheduleChangeAvailable.mockReturnValue(false)
    await changes.init()
    expect(changes.loaded).toBe(true)
    await changes.applyFromSchedule({
      source: '教务系统（实时）',
      rawPayload: '{}',
      term: { xnm: '2026', xqm: '3', startDate: '2026-08-31' }
    })
    expect(applyScheduleChange).not.toHaveBeenCalled()
    expect(maybeRequestChangeNotificationPermission).not.toHaveBeenCalled()
  })
})