import { beforeEach, describe, expect, it } from 'vitest'
import {
  PORTAL_NOTICE_TTL,
  clearPortalCache,
  readPortalCache,
  writePortalCache
} from '../src/utils/portalCache'

describe('学生门户缓存', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('写入后可读取数据与时间', () => {
    writePortalCache('notices.index', { items: [{ id: '1', title: '通知' }] })
    const cached = readPortalCache('notices.index', PORTAL_NOTICE_TTL)
    expect(cached.data.items[0].title).toBe('通知')
    expect(Number.isFinite(Date.parse(cached.cachedAt))).toBe(true)
    expect(cached.expired).toBe(false)
  })

  it('超过 TTL 标记为过期但仍可读取', () => {
    writePortalCache('notices.list', { items: [] })
    const cached = readPortalCache('notices.list', -1)
    expect(cached.expired).toBe(true)
    expect(cached.data.items).toEqual([])
  })

  it('内容损坏时返回 null，不抛错', () => {
    localStorage.setItem('jiaowu.portal.v1.notices.index', '{bad json')
    expect(readPortalCache('notices.index', PORTAL_NOTICE_TTL)).toBeNull()
  })

  it('清理只移除门户缓存，不影响其他个人数据缓存', () => {
    writePortalCache('notices.index', { items: [] })
    localStorage.setItem('jiaowu.data.v1.grades', '{}')
    clearPortalCache()
    expect(localStorage.getItem('jiaowu.portal.v1.notices.index')).toBeNull()
    expect(localStorage.getItem('jiaowu.data.v1.grades')).toBe('{}')
  })
})
