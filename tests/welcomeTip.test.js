import { beforeEach, describe, expect, it, vi } from 'vitest'
import { isWelcomeTipSeen, markWelcomeTipSeen } from '../src/utils/welcomeTip'

const STORAGE_KEY = 'jiaowu.welcomeTip.v1'

describe('首次登录欢迎提示标记', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('未标记时视为未展示', () => {
    expect(isWelcomeTipSeen()).toBe(false)
  })

  it('标记后视为已展示并写入本地存储', () => {
    markWelcomeTipSeen()
    expect(isWelcomeTipSeen()).toBe(true)
    expect(localStorage.getItem(STORAGE_KEY)).toBe('1')
  })

  it('本地存储不可用时读取按未展示、写入不抛错', () => {
    vi.spyOn(window.Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('storage unavailable')
    })
    vi.spyOn(window.Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage unavailable')
    })
    expect(isWelcomeTipSeen()).toBe(false)
    expect(() => markWelcomeTipSeen()).not.toThrow()
  })
})
