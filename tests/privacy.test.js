import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  PRIVACY_VERSION,
  acceptPrivacy,
  acceptedPrivacyVersion,
  isPrivacyAccepted,
  revokePrivacy,
  PRIVACY_SECTIONS
} from '../src/utils/privacy'

describe('隐私政策同意状态', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('首次启动视为未同意', () => {
    expect(acceptedPrivacyVersion()).toBe('')
    expect(isPrivacyAccepted()).toBe(false)
  })

  it('政策章节字段完整且 id 唯一', () => {
    const ids = new Set()
    for (const section of PRIVACY_SECTIONS) {
      expect(section.id).toBeTruthy()
      expect(section.title).toBeTruthy()
      expect(section.text.length).toBeGreaterThan(20)
      expect(ids.has(section.id)).toBe(false)
      ids.add(section.id)
    }
  })

  it('同意后按当前版本记录，撤回后失效', () => {
    expect(acceptPrivacy()).toBe(true)
    expect(localStorage.getItem('lzpu.privacy.accepted')).toBe(PRIVACY_VERSION)
    expect(isPrivacyAccepted()).toBe(true)
    revokePrivacy()
    expect(isPrivacyAccepted()).toBe(false)
  })

  it('旧版本同意不通过', () => {
    localStorage.setItem('lzpu.privacy.accepted', '0')
    expect(isPrivacyAccepted()).toBe(false)
  })

  it('读取存储抛异常时按未同意处理', () => {
    vi.stubGlobal('localStorage', {
      getItem() {
        throw new Error('storage disabled')
      }
    })
    expect(acceptedPrivacyVersion()).toBe('')
    expect(isPrivacyAccepted()).toBe(false)
    vi.unstubAllGlobals()
  })

  it('写入存储抛异常时不崩溃，返回 false', () => {
    vi.stubGlobal('localStorage', {
      setItem() {
        throw new Error('quota exceeded')
      }
    })
    expect(acceptPrivacy()).toBe(false)
    vi.unstubAllGlobals()
  })
})
