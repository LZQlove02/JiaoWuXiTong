import { beforeEach, describe, expect, it } from 'vitest'
import { pruneLocalData } from '../src/utils/retention'

const NOW = Date.parse('2026-11-01T12:00:00+08:00')

function isoDaysAgo(days) {
  return new Date(NOW - days * 24 * 60 * 60 * 1000).toISOString()
}

describe('本地数据保留期限', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('删除超过 30 天的课表快照与个人缓存', () => {
    localStorage.setItem(
      'lzpu.zf.snapshot.v1',
      JSON.stringify({
        profile: { id: 'test' },
        term: { xnm: '2025' },
        schedule: { courses: [] },
        fetchedAt: isoDaysAgo(40)
      })
    )
    localStorage.setItem(
      'lzpu.data.v1.grades',
      JSON.stringify({ data: { grades: [] }, cachedAt: isoDaysAgo(31) })
    )
    localStorage.setItem(
      'lzpu.portal.v1.notices.index',
      JSON.stringify({ data: { items: [] }, cachedAt: isoDaysAgo(32) })
    )
    expect(pruneLocalData(localStorage, NOW)).toBe(3)
  })

  it('保留 30 天内的个人数据与 7 天内的公开缓存', () => {
    localStorage.setItem(
      'lzpu.zf.snapshot.v1',
      JSON.stringify({
        profile: { id: 'test' },
        term: { xnm: '2025' },
        schedule: { courses: [] },
        fetchedAt: isoDaysAgo(10)
      })
    )
    localStorage.setItem(
      'lzpu.data.v1.grades',
      JSON.stringify({ data: { grades: [] }, cachedAt: isoDaysAgo(5) })
    )
    localStorage.setItem(
      'lzpu.cache.public.v1.list.tzgg',
      JSON.stringify({ items: [], cachedAt: Date.parse(isoDaysAgo(3)) })
    )
    expect(pruneLocalData(localStorage, NOW)).toBe(0)
  })

  it('删除超过 7 天的公开内容缓存与损坏条目', () => {
    localStorage.setItem(
      'lzpu.cache.public.v1.article.tzgg.1',
      JSON.stringify({ title: '旧文章', cachedAt: Date.parse(isoDaysAgo(8)) })
    )
    localStorage.setItem('lzpu.data.v1.broken', '{not json')
    expect(pruneLocalData(localStorage, NOW)).toBe(2)
    expect(localStorage.getItem('lzpu.data.v1.broken')).toBeNull()
  })

  it('无 fetchedAt 的旧快照按超期删除', () => {
    localStorage.setItem(
      'lzpu.zf.snapshot.v1',
      JSON.stringify({ profile: { id: 'test' }, term: {}, schedule: {} })
    )
    expect(pruneLocalData(localStorage, NOW)).toBe(1)
  })
})
