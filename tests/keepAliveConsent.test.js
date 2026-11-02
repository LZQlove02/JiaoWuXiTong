import { beforeEach, describe, expect, it } from 'vitest'
import {
  KEEPALIVE_CLAUSES,
  KEEPALIVE_CONSENT_VERSION,
  acceptKeepAliveConsent,
  isKeepAliveConsentAccepted,
  revokeKeepAliveConsent
} from '../src/utils/keepAliveConsent'

describe('保持登录说明同意状态', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('默认未同意', () => {
    expect(isKeepAliveConsentAccepted()).toBe(false)
  })

  it('同意后记录当前版本，撤回后失效', () => {
    expect(acceptKeepAliveConsent()).toBe(true)
    expect(localStorage.getItem('jiaowu.keepalive.consent')).toBe(KEEPALIVE_CONSENT_VERSION)
    expect(isKeepAliveConsentAccepted()).toBe(true)
    revokeKeepAliveConsent()
    expect(isKeepAliveConsentAccepted()).toBe(false)
  })

  it('说明条目完整且覆盖关闭方式', () => {
    const titles = KEEPALIVE_CLAUSES.map((clause) => clause.title)
    expect(titles).toContain('关闭方式')
    for (const clause of KEEPALIVE_CLAUSES) {
      expect(clause.title).toBeTruthy()
      expect(clause.text.length).toBeGreaterThan(20)
    }
  })
})
