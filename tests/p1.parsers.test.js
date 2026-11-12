import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authPost } from '../src/api/nativeHttp'
import { ZfAcademicApi, parseMessages } from '../src/api/zf'

vi.mock('../src/api/nativeHttp', () => ({
  authGet: vi.fn(),
  authPost: vi.fn(),
  isDebugBuild: vi.fn(() => false),
  setAuthCookies: vi.fn(async () => {}),
  loginWithSso: vi.fn(async () => ({ cookieHeader: 'CASTGC=test' }))
}))

const term = {
  xnm: '2026',
  xqm: '3',
  label: '2026-2027 学年第一学期',
  startDate: '2026-08-31',
  isCurrent: true
}

const messagePage = (count, prefix, totalResult) =>
  JSON.stringify({
    ...(Number.isFinite(totalResult) ? { totalResult } : {}),
    items: Array.from({ length: count }, (_, index) => ({
      zjxx: `${prefix}_${index}`,
      xxbt: `消息 ${prefix} ${index}`,
      xxnr: `<p>正文 ${prefix} ${index}</p>`,
      cjsj: '2026-03-01 08:00:00'
    }))
  })

const gradePage = (count, prefix, totalResult, totalPage) =>
  JSON.stringify({
    ...(Number.isFinite(totalResult) ? { totalResult } : {}),
    ...(Number.isFinite(totalPage) ? { totalPage } : {}),
    items: Array.from({ length: count }, (_, index) => ({
      key: `${prefix}_${index}`,
      kch: 'B001',
      kcmc: `课程 ${prefix} ${index}`,
      cj: '88',
      xf: '2',
      jd: '3.5'
    }))
  })

function newApi() {
  const api = new ZfAcademicApi()
  api.term = term
  return api
}

describe('P1-1 消息 id 不再因哈希碰撞被去重', () => {
  it('同标题同时间的两条消息保留两条，且 id 唯一', () => {
    const result = parseMessages({
      totalResult: 2,
      items: [
        { xxbt: '系统通知', xxnr: '<p>一</p>', cjsj: '2026-03-01 08:00:00' },
        { xxbt: '系统通知', xxnr: '<p>二</p>', cjsj: '2026-03-01 08:00:00' }
      ]
    })

    expect(result.items.map((item) => item.content)).toEqual(['一', '二'])
    expect(new Set(result.items.map((item) => item.id)).size).toBe(2)
  })
})

describe('P1-1 门户分页不再提前 break', () => {
  beforeEach(() => authPost.mockReset())

  it('首页 totalResult 正常、次页响应缺失 totalResult 时仍翻页，并把短少标为不完整', async () => {
    authPost
      .mockResolvedValueOnce({ status: 200, body: messagePage(100, 'p1', 137) })
      .mockResolvedValueOnce({ status: 200, body: messagePage(0, 'p2', NaN) })

    const result = await newApi().getMessages()

    expect(authPost).toHaveBeenCalledTimes(2)
    expect(result.total).toBe(137)
    expect(result.items).toHaveLength(100)
    expect(result.truncated).toBe(true)
  })

  it('totalResult 完全缺失时不能被首页条数骗成「已拉完」', async () => {
    authPost
      .mockResolvedValueOnce({ status: 200, body: messagePage(100, 'p1', NaN) })
      .mockResolvedValueOnce({ status: 200, body: messagePage(5, 'p2', NaN) })

    const result = await newApi().getMessages()

    expect(authPost).toHaveBeenCalledTimes(2)
    expect(result.items).toHaveLength(105)
    expect(result.truncated).toBe(false)
  })
})

describe('P1-2 成绩分页回退与去重', () => {
  beforeEach(() => authPost.mockReset())

  it('totalPage 缺失时按 totalResult 推算页数', async () => {
    authPost.mockImplementation((url, body) => {
      const page = Number(/currentPage=(\d+)/.exec(body)?.[1] || 1)
      const payload = page === 1 ? gradePage(100, 'a', 150, NaN) : gradePage(50, 'b', 150, NaN)
      return Promise.resolve({ status: 200, body: payload })
    })

    const result = await newApi().getGrades(term)

    expect(authPost).toHaveBeenCalledTimes(2)
    expect(result.grades).toHaveLength(150)
  })

  it('同一成绩跨页重复时去重，绩点学分不重复累加', async () => {
    authPost.mockResolvedValue({
      status: 200,
      body: gradePage(5, 'dup', 5, 2)
    })

    const result = await newApi().getGrades(term)

    expect(result.grades).toHaveLength(5)
    expect(result.summary.totalCredits).toBe(10)
    expect(result.truncated).toBeUndefined()
  })
})
