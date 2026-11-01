import { beforeEach, describe, expect, it } from 'vitest'
import {
  BACKGROUND_CLAUSES,
  acceptBackgroundConsent,
  isBackgroundConsentAccepted,
  revokeBackgroundConsent
} from '../src/utils/backgroundConsent'

describe('后台检查说明同意状态', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('默认未同意', () => {
    expect(isBackgroundConsentAccepted()).toBe(false)
  })

  it('同意后记录，撤回后失效，说明条目完整', () => {
    for (const clause of BACKGROUND_CLAUSES) {
      expect(clause.title).toBeTruthy()
      expect(clause.text.length).toBeGreaterThan(20)
    }
    expect(acceptBackgroundConsent()).toBe(true)
    expect(isBackgroundConsentAccepted()).toBe(true)
    revokeBackgroundConsent()
    expect(isBackgroundConsentAccepted()).toBe(false)
  })
})
