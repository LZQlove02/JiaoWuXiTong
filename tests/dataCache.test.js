import { beforeEach, describe, expect, it } from 'vitest'
import {
  GRADES_TTL,
  clearDataCaches,
  readDataCache,
  writeDataCache
} from '../src/utils/dataCache'

describe('个人数据缓存', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('写入后可读回数据与缓存时间，并按 TTL 判断过期', () => {
    const payload = { term: { xnm: '2026', xqm: '3' }, grades: [] }
    const cachedAt = writeDataCache('grades.2026_3', payload)
    const cached = readDataCache('grades.2026_3', GRADES_TTL)
    expect(cached).toMatchObject({ data: payload, cachedAt, expired: false })

    localStorage.setItem(
      'lzpu.data.v1.grades.2026_3',
      JSON.stringify({ data: payload, cachedAt: '2020-01-01T00:00:00.000Z' })
    )
    expect(readDataCache('grades.2026_3', GRADES_TTL)?.expired).toBe(true)
  })

  it('退出登录时只清个人数据缓存，不影响主题与免责声明', () => {
    writeDataCache('grades.2026_3', { grades: [] })
    localStorage.setItem('lzpu.theme', 'dark')
    localStorage.setItem('lzpu.disclaimer.accepted', '1')
    clearDataCaches()
    expect(readDataCache('grades.2026_3', GRADES_TTL)).toBeNull()
    expect(localStorage.getItem('lzpu.theme')).toBe('dark')
    expect(localStorage.getItem('lzpu.disclaimer.accepted')).toBe('1')
  })
})
