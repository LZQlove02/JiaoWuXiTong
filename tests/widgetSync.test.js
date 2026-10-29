import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// 组件写入依赖 Capacitor 原生桥，单测里整体打桩
vi.mock('../src/native/widgetBridge', () => ({
  widgetAvailable: vi.fn(() => true),
  updateWidget: vi.fn(() => Promise.resolve(true)),
  clearWidget: vi.fn(() => Promise.resolve(true)),
  setWidgetTheme: vi.fn(() => Promise.resolve(true))
}))

import { clearWidget, setWidgetTheme, updateWidget, widgetAvailable } from '../src/native/widgetBridge'
import { clearWidgetNow, resetWidgetSync, syncWidget, syncWidgetTheme } from '../src/widget/sync'

// 固定时间：payload 里带 updatedAt，时间一变 JSON 就不同，会绕过「同内容不重复写」的判断
const NOW = new Date('2026-10-29T10:00:00+08:00')

function context(overrides = {}) {
  return {
    user: { name: '测试' },
    currentTerm: { label: '2026-2027 学年第一学期', startDate: '2026-08-31', xnm: '2026', xqm: '3' },
    schedule: { sessions: [], courses: [] },
    grades: null,
    messages: null,
    portalNotices: null,
    now: NOW,
    ...overrides
  }
}

describe('组件同步', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    resetWidgetSync()
    vi.clearAllMocks()
    widgetAvailable.mockReturnValue(true)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('300ms 内多次触发只写一次', () => {
    syncWidget(context())
    vi.advanceTimersByTime(100)
    syncWidget(context())
    vi.advanceTimersByTime(299)
    expect(updateWidget).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1)
    expect(updateWidget).toHaveBeenCalledTimes(1)
  })

  it('内容相同的连续刷新不重复写盘', () => {
    syncWidget(context())
    vi.advanceTimersByTime(300)
    syncWidget(context())
    vi.advanceTimersByTime(300)

    expect(updateWidget).toHaveBeenCalledTimes(1)
    expect(updateWidget.mock.calls[0][0].loggedIn).toBe(true)
  })

  it('内容变化时再写一次', () => {
    syncWidget(context())
    vi.advanceTimersByTime(300)
    syncWidget(context({ now: new Date('2026-10-29T10:05:00+08:00') }))
    vi.advanceTimersByTime(300)

    expect(updateWidget).toHaveBeenCalledTimes(2)
  })

  it('退出登录立即清空，并取消排队中的写入', () => {
    syncWidget(context())
    vi.advanceTimersByTime(100)
    clearWidgetNow()
    vi.advanceTimersByTime(300)

    expect(clearWidget).toHaveBeenCalledTimes(1)
    expect(updateWidget).not.toHaveBeenCalled()
  })

  it('清空之后同样的内容可以重新写入', () => {
    syncWidget(context())
    vi.advanceTimersByTime(300)
    clearWidgetNow()
    syncWidget(context())
    vi.advanceTimersByTime(300)

    expect(updateWidget).toHaveBeenCalledTimes(2)
  })

  it('主题相同不重复下发，变化才下发', () => {
    syncWidgetTheme('dark')
    syncWidgetTheme('dark')
    expect(setWidgetTheme).toHaveBeenCalledTimes(1)

    syncWidgetTheme('light')
    expect(setWidgetTheme).toHaveBeenCalledTimes(2)
  })

  it('非 Android 原生环境不调用任何原生方法', () => {
    widgetAvailable.mockReturnValue(false)

    syncWidget(context())
    vi.advanceTimersByTime(300)
    syncWidgetTheme('dark')
    clearWidgetNow()

    expect(updateWidget).not.toHaveBeenCalled()
    expect(setWidgetTheme).not.toHaveBeenCalled()
    expect(clearWidget).not.toHaveBeenCalled()
  })
})