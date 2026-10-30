import { describe, expect, it } from 'vitest'
import {
  compareVersions,
  formatReleaseNotes,
  isNewer,
  parseVersion,
  shouldAutoCheck
} from '../src/utils/update'

describe('update', () => {
  it('版本号解析', () => {
    expect(parseVersion('1.2.3')).toEqual([1, 2, 3, 0])
    expect(parseVersion('v1.2')).toEqual([1, 2, 0, 0])
    expect(parseVersion('V2')).toEqual([2, 0, 0, 0])
    expect(parseVersion('1.2.3-beta.1')).toEqual([1, 2, 3, 0])
    expect(parseVersion('')).toBeNull()
    expect(parseVersion('abc')).toBeNull()
    expect(parseVersion(null)).toBeNull()
  })

  it('版本比较', () => {
    expect(compareVersions('1.2.0', 'v1.2.0')).toBe(0)
    expect(compareVersions('v1.10.0', '1.9.0')).toBe(1)
    expect(compareVersions('1.2.3', '1.3.0')).toBe(-1)
    expect(compareVersions('1.2', '1.2.0.1')).toBe(-1)
    expect(compareVersions('bad', '1.0.0')).toBe(0)
  })

  it('新版本判定', () => {
    expect(isNewer('v1.3.0', '1.2.1')).toBe(true)
    expect(isNewer('1.2.1', '1.2.1')).toBe(false)
    expect(isNewer('1.2.0', '1.2.1')).toBe(false)
    expect(isNewer('v1.3.0', '')).toBe(false)
    expect(isNewer('', '1.2.1')).toBe(false)
  })

  it('24 小时节流', () => {
    const now = 1_700_000_000_000
    expect(shouldAutoCheck(0, now)).toBe(true)
    expect(shouldAutoCheck(now - 60 * 60 * 1000, now)).toBe(false)
    expect(shouldAutoCheck(now - 25 * 60 * 60 * 1000, now)).toBe(true)
    expect(shouldAutoCheck(now + 1000, now)).toBe(true)
    expect(shouldAutoCheck(Number.NaN, now)).toBe(true)
  })

  it('发布说明清洗与截断', () => {
    const notes = [
      '## 本次更新',
      '',
      '- 首启免责声明',
      '- 修复会话失效',
      '',
      '详见 [发布页](https://example.com)',
      '',
      '```',
      'code',
      '```'
    ].join('\n')
    const text = formatReleaseNotes(notes)
    expect(text).toContain('本次更新')
    expect(text).toContain('· 首启免责声明')
    expect(text).not.toContain('##')
    expect(text).not.toContain('https://example.com')
    expect(text).not.toContain('code')
    expect(formatReleaseNotes('')).toBe('')
    expect(formatReleaseNotes(null)).toBe('')
    const long = 'a'.repeat(50)
    expect(formatReleaseNotes(long, 10)).toBe(`${'a'.repeat(10)}…`)
  })
})
