import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useUiStore } from '../src/stores/ui'

// 用可控的 matchMedia 替代系统查询，模拟「跟随系统」时的深浅色变化
function stubMedia(matches) {
  const listeners = []
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => ({
      matches,
      media: '(prefers-color-scheme: dark)',
      addEventListener: (type, handler) => {
        if (type === 'change') listeners.push(handler)
      },
      removeEventListener: () => {}
    }))
  )
  return {
    fire(next) {
      listeners.forEach((handler) => handler({ matches: next }))
    }
  }
}

describe('App 实际生效主题', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('固定浅色/深色时不使用系统设置', () => {
    stubMedia(true)
    const ui = useUiStore()
    ui.setTheme('light')
    expect(ui.effectiveTheme).toBe('light')

    ui.setTheme('dark')
    expect(ui.effectiveTheme).toBe('dark')
  })

  it('跟随系统时解析为系统深浅色', () => {
    stubMedia(true)
    const ui = useUiStore()
    ui.setTheme('system')
    expect(ui.effectiveTheme).toBe('dark')
  })

  it('跟随系统时系统切换会同步更新实际主题', () => {
    const media = stubMedia(false)
    const ui = useUiStore()
    ui.initTheme()
    ui.setTheme('system')
    expect(ui.effectiveTheme).toBe('light')

    media.fire(true)
    expect(ui.effectiveTheme).toBe('dark')
  })
})
