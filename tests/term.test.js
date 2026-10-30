import { describe, expect, it } from 'vitest'
import { buildTerm, termCandidatesFor, termParamsFor } from '../src/utils/term'

describe('学期推算', () => {
  it('暑期学期从次年 7 月开始，而不是沿用第二学期的 2 月', () => {
    const term = buildTerm('2025', '16')
    expect(term.startDate).toBe('2026-07-01')
    expect(term.label).toContain('第三学期（暑期）')
  })

  it('7 月优先按暑期学期查询，取不到时再回退到第二学期和下一学年', () => {
    expect(termParamsFor(new Date(2026, 6, 15))).toEqual({ xnm: '2025', xqm: '16' })
    expect(termCandidatesFor({ xnm: '2025', xqm: '16' })).toEqual([
      { xnm: '2025', xqm: '16' },
      { xnm: '2025', xqm: '12' },
      { xnm: '2026', xqm: '3' }
    ])
  })

  it('第一学期取不到时依次尝试第二学期与上一学年暑期', () => {
    expect(termCandidatesFor({ xnm: '2026', xqm: '3' })).toEqual([
      { xnm: '2026', xqm: '3' },
      { xnm: '2026', xqm: '12' },
      { xnm: '2025', xqm: '16' }
    ])
  })
})
