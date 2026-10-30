import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  DISCLAIMER_VERSION,
  acceptDisclaimer,
  acceptedDisclaimerVersion,
  isDisclaimerAccepted
} from '../src/utils/disclaimer'

describe('首启免责声明同意状态', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('首次启动视为未同意', () => {
    expect(acceptedDisclaimerVersion()).toBe('')
    expect(isDisclaimerAccepted()).toBe(false)
  })

  it('同意后按当前声明版本记录', () => {
    expect(acceptDisclaimer()).toBe(true)
    expect(localStorage.getItem('lzpu.disclaimer.accepted')).toBe(DISCLAIMER_VERSION)
    expect(isDisclaimerAccepted()).toBe(true)
  })

  it('旧声明版本不通过', () => {
    localStorage.setItem('lzpu.disclaimer.accepted', '0')
    expect(isDisclaimerAccepted()).toBe(false)
  })

  it('读取存储抛异常时按未同意处理', () => {
    vi.stubGlobal('localStorage', {
      getItem() {
        throw new Error('storage disabled')
      }
    })
    expect(acceptedDisclaimerVersion()).toBe('')
    expect(isDisclaimerAccepted()).toBe(false)
  })

  it('写入存储抛异常时不崩溃，返回 false', () => {
    vi.stubGlobal('localStorage', {
      setItem() {
        throw new Error('quota exceeded')
      }
    })
    expect(acceptDisclaimer()).toBe(false)
  })
})