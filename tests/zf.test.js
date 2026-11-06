import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authGet, authPost } from '../src/api/nativeHttp'
import {
  ZfAcademicApi,
  clearScheduleSnapshot,
  mapEvaluations,
  mapGrades,
  mapSchedule,
  mergeGradeGroups,
  parseCourseSelectionStatus,
  parseMessages,
  parsePortalNoticeDetail,
  parsePortalNoticeIndex,
  parsePortalNoticeList,
  parseTermsFromHtml
} from '../src/api/zf'

vi.mock('../src/api/nativeHttp', () => ({
  authGet: vi.fn(),
  authPost: vi.fn(),
  isDebugBuild: vi.fn().mockResolvedValue(false),
  loginWithSso: vi.fn(),
  setAuthCookies: vi.fn()
}))

const SNAPSHOT_KEY = 'jiaowu.zf.snapshot.v1'

const term = {
  xnm: '2026',
  xqm: '3',
  label: '2026-2027 学年第一学期',
  startDate: '2026-08-31',
  isCurrent: true
}

const snapshot = {
  profile: { id: 'test-student-1', name: '测试', className: '', major: '', college: '', avatar: '' },
  term,
  schedule: {
    term,
    courses: [
      { id: 'c1', code: '', name: '高等数学', teacher: '', credits: 0, type: '', color: '#1B5FAA' }
    ],
    sessions: [
      { courseId: 'c1', weekday: 1, startSection: 1, endSection: 2, weeks: [1], room: 'A101' }
    ]
  },
  fetchedAt: new Date().toISOString()
}

const networkError = () => new Error('原生 HTTP 插件仅在 App 内可用')

describe('ZfAcademicApi 离线快照', () => {
  beforeEach(() => {
    localStorage.clear()
    authGet.mockReset()
  })

  it('网络不可达时用快照恢复会话并标注离线来源', async () => {
    authGet.mockRejectedValue(networkError())
    localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot))
    const api = new ZfAcademicApi()
    const profile = await api.restoreSession()
    expect(profile.id).toBe('test-student-1')
    const schedule = await api.getSchedule()
    expect(schedule.source).toBe('教务系统（离线缓存）')
    expect(schedule.courses).toHaveLength(1)
  })

  it('applySnapshot 只读快照不发请求', async () => {
    authGet.mockRejectedValue(networkError())
    localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot))
    const api = new ZfAcademicApi()
    const profile = api.applySnapshot()
    expect(profile.id).toBe('test-student-1')
    expect(authGet).not.toHaveBeenCalled()
    await expect(api.getSchedule()).resolves.toMatchObject({
      source: '教务系统（离线缓存）'
    })
  })

  it('没有快照时向上抛出网络错误', async () => {
    authGet.mockRejectedValue(networkError())
    const api = new ZfAcademicApi()
    await expect(api.restoreSession()).rejects.toMatchObject({ network: true })
  })

  it('刚登录校验证书失败时不回退旧快照', async () => {
    authGet.mockRejectedValue(networkError())
    localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot))
    const api = new ZfAcademicApi()
    await expect(api.restoreSession({ allowSnapshot: false })).rejects.toMatchObject({
      network: true
    })
  })

  it('被重定向到 http 登录页时判定会话失效且不套用快照', async () => {
    authGet.mockRejectedValue(new Error('仅允许 HTTPS 请求'))
    localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot))
    const api = new ZfAcademicApi()
    await expect(api.restoreSession()).rejects.toMatchObject({ sessionExpired: true })
  })

  it('会话解密暂时失败时按网络故障处理并保留登录态', async () => {
    authGet.mockRejectedValue(new Error('SESSION_STORE_UNAVAILABLE'))
    localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot))
    const api = new ZfAcademicApi()
    const profile = await api.restoreSession()
    expect(profile.id).toBe('test-student-1')
  })

  it('clearScheduleSnapshot 清除快照', () => {
    localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot))
    clearScheduleSnapshot()
    expect(localStorage.getItem(SNAPSHOT_KEY)).toBeNull()
  })
})

describe('课表解析与响应校验', () => {
  beforeEach(() => {
    authGet.mockReset()
  })

  it('缺少 zcd 时按 20 周兜底，避免第 17-20 周的课被漏掉', () => {
    const result = mapSchedule(
      {
        kbList: [
          { kch: 'c1', kcmc: '高等数学', jcs: '3-4', xqj: 1, cdmc: 'A101' }
        ]
      },
      term
    )
    expect(result.sessions[0].weeks).toHaveLength(20)
  })

  it('getSchedule 遇到缺少 xsxx 的异常响应时按解析失败处理，而不是显示空课表', async () => {
    authGet.mockResolvedValue({ status: 200, body: '{"kbList":[]}' })
    const api = new ZfAcademicApi()
    api.term = term
    await expect(api.getSchedule(term, { force: true })).rejects.toMatchObject({
      parseError: true
    })
  })
})

describe('成绩解析与学期列表', () => {
  beforeEach(() => {
    authGet.mockReset()
    authPost.mockReset()
  })

  it('汇总学分与绩点，兼容文本成绩', () => {
    const payload = {
      items: [
        {
          key: 'a',
          kch: '1001',
          kcmc: '高等数学',
          cj: '92',
          xf: '4',
          jd: '4.2',
          kclbmc: '必修',
          ksxz: '正常考试',
          jsxm: '张明'
        },
        {
          key: 'b',
          kch: '1002',
          kcmc: '体育',
          cj: '58',
          xf: '1',
          jd: '0',
          kclbmc: '必修',
          ksxz: '正常考试',
          jsxm: '刘强'
        },
        {
          key: 'c',
          kch: '1003',
          kcmc: '形势与政策',
          cj: '优秀',
          xf: '2',
          jd: '',
          kclbmc: '必修',
          ksxz: '考查',
          jsxm: '赵敏'
        }
      ]
    }

    const result = mapGrades(payload, term)
    expect(result.grades).toHaveLength(3)
    expect(result.grades[2].score).toBe('优秀')
    expect(result.grades[2].gradePoint).toBeNull()
    expect(result.summary).toEqual({ gpa: 3.36, totalCredits: 7, count: 3 })
    expect(result.source).toBe('教务系统（实时）')
  })

  it('缺少 items 时按空列表处理', () => {
    const result = mapGrades({}, term)
    expect(result.grades).toEqual([])
    expect(result.summary).toEqual({ gpa: null, totalCredits: 0, count: 0 })
  })

  it('从成绩页下拉框解析全部学期并标记当前学期', () => {
    const html = `
      <select name="xnm">
        <option value="2026">2026-2027</option>
        <option value="2025">2025-2026</option>
        <option value="2024">2024-2025</option>
      </select>
      <select name="xqm">
        <option value="3">第一学期</option>
        <option value="12">第二学期</option>
      </select>
    `
    const terms = parseTermsFromHtml(html, term)
    expect(terms.map((item) => `${item.xnm}_${item.xqm}`)).toEqual([
      '2026_3',
      '2025_12',
      '2025_3',
      '2024_12',
      '2024_3'
    ])
    expect(terms[0].isCurrent).toBe(true)
    expect(terms[0].label).toBe('2026-2027 学年第一学期')
    expect(terms[0].startDate).toBe('2026-08-31')
  })

  it('页面没有学期下拉时返回空列表', () => {
    expect(parseTermsFromHtml('<html><body></body></html>', term)).toEqual([])
  })

  it('按入学年份过滤入学前的学期，并标注第三学期为暑期', () => {
    const html = `
      <select name="xnm">
        <option value="2004">2004-2005</option>
        <option value="2024">2024-2025</option>
        <option value="2025">2025-2026</option>
        <option value="2026">2026-2027</option>
      </select>
      <select name="xqm">
        <option value="3">第一学期</option>
        <option value="12">第二学期</option>
        <option value="16">第三学期</option>
      </select>
    `
    const terms = parseTermsFromHtml(html, term, { enrollmentYear: 2025 })
    expect(terms.map((item) => `${item.xnm}_${item.xqm}`)).toEqual([
      '2026_3',
      '2025_16',
      '2025_12',
      '2025_3'
    ])
    expect(terms.every((item) => Number(item.xnm) >= 2025)).toBe(true)
    expect(terms.find((item) => item.xqm === '16').label).toContain('第三学期（暑期）')
  })

  it('2026 级只显示入学当年与当前学期', () => {
    const html = `
      <select name="xnm">
        <option value="2025">2025-2026</option>
        <option value="2026">2026-2027</option>
        <option value="2027">2027-2028</option>
      </select>
      <select name="xqm">
        <option value="3">第一学期</option>
        <option value="12">第二学期</option>
        <option value="16">第三学期</option>
      </select>
    `
    const current = { xnm: '2026', xqm: '3', label: '2026-2027 学年第一学期' }
    const terms = parseTermsFromHtml(html, current, { enrollmentYear: 2026 })
    expect(terms.map((item) => `${item.xnm}_${item.xqm}`)).toEqual(['2026_3'])
  })

  it('成绩页不可用时按入学年份生成学期兜底', async () => {
    authGet.mockRejectedValue(networkError())
    const api = new ZfAcademicApi()
    api.term = term
    api.profile = { enrollmentYear: 2025 }
    const terms = await api.getTerms()
    expect(terms.map((item) => `${item.xnm}_${item.xqm}`)).toEqual([
      '2026_3',
      '2025_12',
      '2025_3'
    ])
    expect(terms[0].isCurrent).toBe(true)
    expect(terms[0].startDate).toBe('2026-08-31')
  })

  it('getGrades 按学期请求成绩接口并复用解析结果', async () => {
    authPost.mockResolvedValue({
      status: 200,
      body: JSON.stringify({
        items: [
          { key: 'a', kch: '1001', kcmc: '高等数学', cj: '92', xf: '4', jd: '4.2' }
        ]
      })
    })
    const api = new ZfAcademicApi()
    api.term = term
    const result = await api.getGrades(term)
    expect(authPost).toHaveBeenCalledWith(
      'https://zfjw.lzzy.net/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005',
      'xnm=2026&xqm=3&queryModel.showCount=100&queryModel.currentPage=1'
    )
    expect(result.summary).toEqual({ gpa: 4.2, totalCredits: 4, count: 1 })
  })

  it('getGrades 按 totalPage 翻页并合并全部记录', async () => {
    const makeItems = (start, count) => Array.from({ length: count }, (_, index) => ({
      key: `k${start + index}`,
      kch: String(1000 + start + index),
      kcmc: `课程 ${start + index}`,
      cj: '80',
      xf: '1',
      jd: '3'
    }))
    authPost.mockImplementation((url, body) => {
      const page = Number(/queryModel.currentPage=(\d+)/.exec(body)?.[1] || 1)
      const items = page === 1 ? makeItems(0, 10) : makeItems(10, 2)
      return Promise.resolve({
        status: 200,
        body: JSON.stringify({ currentPage: page, totalPage: 2, totalResult: 12, items })
      })
    })
    const api = new ZfAcademicApi()
    api.term = term
    const result = await api.getGrades(term)
    expect(authPost).toHaveBeenCalledTimes(2)
    expect(result.grades).toHaveLength(12)
    expect(result.summary.count).toBe(12)
    expect(result.grades[11].courseName).toBe('课程 11')
  })
})

describe('评教记录与选课状态（只读）', () => {
  beforeEach(() => {
    authGet.mockReset()
    authPost.mockReset()
  })

  it('评教列表映射为只读记录并识别提交状态', () => {
    const result = mapEvaluations({
      totalResult: 23,
      items: [
        {
          jxb_id: 'j1',
          kcmc: '高等数学',
          jzgmc: '张明',
          jxbmc: '高数 1 班',
          tjzt: '1',
          tjztmc: '提交'
        },
        {
          jxb_id: 'j2',
          kcmc: '体育',
          jzgmc: '刘强',
          jxbmc: '体育 2 班',
          tjzt: '0',
          tjztmc: ''
        },
        { jxb_id: 'j3', kcmc: '', jzgmc: '赵敏' }
      ]
    })
    expect(result.total).toBe(23)
    expect(result.items).toHaveLength(2)
    expect(result.items[0]).toMatchObject({
      courseName: '高等数学',
      teacher: '张明',
      status: '已提交',
      submitted: true
    })
    expect(result.items[1].submitted).toBe(false)
    expect(result.source).toBe('教务系统（只读）')
  })

  it('评教 totalResult 缺失时回退为条目数', () => {
    const result = mapEvaluations({ items: [{ kcmc: '课程 A' }] })
    expect(result.total).toBe(1)
  })

  it('选课开关为 0 时显示未开放，为 1 时显示已开放', () => {
    expect(parseCourseSelectionStatus('<input id="iskxk" value="0" />')).toMatchObject({
      open: false,
      label: '当前不在选课时间'
    })
    expect(parseCourseSelectionStatus('<input value="1" id="iskxk" />')).toMatchObject({
      open: true,
      label: '选课已开放'
    })
  })

  it('缺失选课开关时按未开放处理', () => {
    expect(parseCourseSelectionStatus('<html></html>').open).toBe(false)
  })

  it('getEvaluations 只调用列表查询，不包含任何提交动作', async () => {
    authPost.mockResolvedValue({
      status: 200,
      body: JSON.stringify({
        totalResult: 1,
        items: [{ jxb_id: 'j1', kcmc: '课程 A', tjzt: '1', tjztmc: '提交' }]
      })
    })
    const api = new ZfAcademicApi()
    api.term = term
    const result = await api.getEvaluations()
    expect(authPost).toHaveBeenCalledTimes(1)
    const [url, body] = authPost.mock.calls[0]
    expect(url).toContain('xspj_cxXspjIndex.html?doType=query')
    expect(body).toBe('page=1&rows=100&sidx=&sord=asc')
    expect(result.items[0].submitted).toBe(true)
  })

  it('getEvaluations 按 totalPage 翻页并去重', async () => {
    authPost.mockImplementation((url, body) => {
      const page = Number(/page=(\d+)/.exec(body)?.[1] || 1)
      const items = page === 1
        ? [
            { jxb_id: 'j1', kcmc: '课程 A', tjzt: '1' },
            { jxb_id: 'j2', kcmc: '课程 B', tjzt: '0' }
          ]
        : [
            { jxb_id: 'j2', kcmc: '课程 B', tjzt: '0' },
            { jxb_id: 'j3', kcmc: '课程 C', tjzt: '1' }
          ]
      return Promise.resolve({
        status: 200,
        body: JSON.stringify({ totalPage: 2, totalResult: 3, items })
      })
    })
    const api = new ZfAcademicApi()
    api.term = term
    const result = await api.getEvaluations()
    expect(authPost).toHaveBeenCalledTimes(2)
    expect(result.items).toHaveLength(3)
    expect(result.items[2].courseName).toBe('课程 C')
  })

  it('getCourseSelectionStatus 只读取选课入口页', async () => {
    authGet.mockResolvedValue({ status: 200, body: '<input id="iskxk" value="0">' })
    const api = new ZfAcademicApi()
    api.term = term
    const result = await api.getCourseSelectionStatus()
    expect(authGet).toHaveBeenCalledTimes(1)
    expect(authGet.mock.calls[0][0]).toContain('gnmkdm=N253512')
    expect(result.open).toBe(false)
  })
})

describe('全部学期成绩汇总', () => {
  beforeEach(() => {
    authGet.mockReset()
    authPost.mockReset()
  })

  it('只保留有成绩的学期，并汇总全部学分与绩点', () => {
    const merged = mergeGradeGroups([
      {
        term: { xnm: '2026', xqm: '3', label: '2026-2027 学年第一学期' },
        grades: [{ id: 'a', credit: 2, gradePoint: 4, courseName: '课程 A' }]
      },
      {
        term: { xnm: '2025', xqm: '12', label: '2025-2026 学年第二学期' },
        grades: []
      },
      {
        term: { xnm: '2025', xqm: '3', label: '2025-2026 学年第一学期' },
        grades: [{ id: 'b', credit: 4, gradePoint: 3, courseName: '课程 B' }]
      }
    ])
    expect(merged.groups).toHaveLength(2)
    expect(merged.summary).toEqual({ gpa: 3.33, totalCredits: 6, count: 2 })
    expect(merged.all).toBe(true)
  })

  it('getAllGrades 逐学期查询并保留部分失败信息', async () => {
    authPost.mockImplementation((url, body) => {
      if (body.includes('xqm=3')) {
        return Promise.resolve({
          status: 200,
          body: JSON.stringify({
            items: [{ key: 'a', kch: '1', kcmc: '课程 A', cj: '90', xf: '2', jd: '4' }]
          })
        })
      }
      if (body.includes('xqm=12')) {
        return Promise.resolve({
          status: 200,
          body: JSON.stringify({
            items: [{ key: 'b', kch: '2', kcmc: '课程 B', cj: '80', xf: '2', jd: '3' }]
          })
        })
      }
      return Promise.reject(new Error('网络错误'))
    })
    const api = new ZfAcademicApi()
    api.term = term
    const result = await api.getAllGrades([
      { xnm: '2026', xqm: '3', label: '2026-2027 学年第一学期' },
      { xnm: '2025', xqm: '12', label: '2025-2026 学年第二学期' },
      { xnm: '2025', xqm: '16', label: '2025-2026 学年第三学期（暑期）' }
    ])
    expect(authPost).toHaveBeenCalledTimes(3)
    expect(result.groups).toHaveLength(2)
    expect(result.summary.count).toBe(2)
    expect(result.partial).toHaveLength(1)
    expect(result.partial[0].termLabel).toContain('第三学期')
  })
})

describe('学生门户通知与消息（只读）', () => {
  it('解析首页通知列表，去掉置顶/通知前缀与日期', () => {
    const html = `
      <a href="/xtgl/xwck_ckXw.html?xwbh=ID1&doType=save">【置顶】 【通知】 关于做好2026-2027学年上学期课程重修工作的通知 2026-09-16</a>
      <a href="/xtgl/xwck_ckXw.html?xwbh=ID2&doType=save">【通知】 关于2026年下半年大学英语等级考试报名工作的通知 2026-09-15</a>
    `
    const items = parsePortalNoticeIndex(html).items
    expect(items).toHaveLength(2)
    expect(items[0]).toMatchObject({ id: 'ID1', date: '2026-09-16', pinned: true })
    expect(items[0].title).toBe('关于做好2026-2027学年上学期课程重修工作的通知')
    expect(items[1].pinned).toBe(false)
  })

  it('解析通知列表分页结果', () => {
    const result = parsePortalNoticeList({
      totalResult: 8,
      items: [
        { xwbh: 'ID1', xwbtqc: '通知 A', fbsj: '2026-09-16', fbr: '教务处', sfzd: '1' },
        { xwbh: 'ID2', xwbtqc: '通知 B', fbsj: '2026-09-15', fbr: '教务处', sfzd: '0' }
      ]
    })
    expect(result.total).toBe(8)
    expect(result.items[0]).toMatchObject({
      id: 'ID1',
      title: '通知 A',
      date: '2026-09-16',
      pinned: true
    })
    expect(result.source).toContain('学生门户')
  })

  it('解析通知正文、发布时间、浏览数与附件名', () => {
    const html = `
      <h3>关于做好重修工作的通知</h3>
      <h5 class="news_title1"><span>发布时间：2026-09-16 10:00:00</span><span>浏览人数：3001</span></h5>
      <div class="news_con">
        <p>各二级学院：</p>
        <p>请及时查看通知。</p>
      </div>
      <a href="../ftp/x.pdf?filename=%E9%99%84%E4%BB%B61.pdf">附件1</a>
    `
    const detail = parsePortalNoticeDetail(html)
    expect(detail.title).toBe('关于做好重修工作的通知')
    expect(detail.publishedAt).toBe('2026-09-16 10:00:00')
    expect(detail.views).toBe(3001)
    expect(detail.paragraphs).toEqual(['各二级学院：', '请及时查看通知。'])
    expect(detail.attachments[0].name).toBe('附件1.pdf')
  })

  it('解析消息列表，正文转纯文本并按时间倒序', () => {
    const result = parseMessages({
      totalResult: 2,
      items: [
        { zjxx: 'm1', xxbt: '调课提醒 A', xxnr: '<p>周一第 1-2 节调课</p>', cjsj: '2026-09-20 10:00:00' },
        { zjxx: 'm2', xxbt: '补课提醒 B', xxnr: '周五补课', cjsj: '2026-10-08 15:56:39' }
      ]
    })
    expect(result.total).toBe(2)
    expect(result.items[0].id).toBe('m2')
    expect(result.items[1].content).toBe('周一第 1-2 节调课')
    expect(result.source).toBe('教务系统（只读）')
  })

  it('结构无法识别时抛错，不伪装成暂无数据', () => {
    expect(() => parsePortalNoticeIndex('<div>系统升级</div>')).toThrow()
    expect(() => parsePortalNoticeList({ totalResult: 2, items: [] })).toThrow()
    expect(() => parsePortalNoticeList({ totalResult: 1, items: [{ foo: 'bar' }] })).toThrow()
    expect(() => parseMessages({ totalResult: 3, items: [] })).toThrow()
    expect(() => parsePortalNoticeDetail('<div>没有标题</div>')).toThrow()
  })

  it('消息缺少 zjxx 时用标题与时间生成稳定 id', () => {
    const payload = {
      totalResult: 1,
      items: [{ xxbt: '调课提醒 X', cjsj: '2026-10-01 10:00:00' }]
    }
    const first = parseMessages(payload).items[0].id
    const second = parseMessages(payload).items[0].id
    expect(first).toBe(second)
    expect(first).toMatch(/^msg_/)
  })

  it('详情只有图片时仍可展示，并给出图片数量', () => {
    const html = `
      <h3>图片通知</h3>
      <div class="news_con"><p><img src="a.png" /></p></div>
    `
    const detail = parsePortalNoticeDetail(html)
    expect(detail.title).toBe('图片通知')
    expect(detail.images).toBe(1)
    expect(detail.paragraphs).toEqual([])
  })

  it('通知详情图片与附件按详情页地址解析为 zfjw 完整 URL', () => {
    const html = `
      <h3>附件通知</h3>
      <div class="news_con">
        <p>正文</p>
        <p><img src="/xtgl/img/a.png" /></p>
        <p><a href="../ftp/x.pdf?filename=%E9%99%84%E4%BB%B61.pdf">附件1</a></p>
      </div>
    `
    const detail = parsePortalNoticeDetail(
      html,
      'https://zfjw.lzzy.net/xtgl/xwck_ckXw.html?xwbh=ID1'
    )
    expect(detail.imageUrls).toHaveLength(1)
    expect(detail.imageUrls[0].url).toBe('https://zfjw.lzzy.net/xtgl/img/a.png')
    expect(detail.images).toBe(1)
    expect(detail.attachments[0].url).toBe('https://zfjw.lzzy.net/ftp/x.pdf?filename=%E9%99%84%E4%BB%B61.pdf')
  })

  it('非 zfjw 域的附件与图片不进入可下载列表', () => {
    const html = `
      <h3>外部链接通知</h3>
      <div class="news_con">
        <p>正文</p>
        <p><img src="https://cdn.example.com/a.png" /></p>
        <p><a href="https://ftp.lzzy.net/x.pdf?filename=附件.pdf">外部附件</a></p>
      </div>
    `
    const detail = parsePortalNoticeDetail(
      html,
      'https://zfjw.lzzy.net/xtgl/xwck_ckXw.html?xwbh=ID1'
    )
    expect(detail.imageUrls).toHaveLength(0)
    expect(detail.attachments[0].url).toBe('')
  })
})

describe('学生门户请求与分页', () => {
  beforeEach(() => {
    authGet.mockReset()
    authPost.mockReset()
  })

  it('通知列表按页拉取并合并，请求带 queryModel 参数', async () => {
    const page1 = {
      totalResult: 150,
      items: Array.from({ length: 100 }, (_, index) => ({
        xwbh: `a${index}`,
        xwbtqc: `通知 A${index}`,
        fbsj: '2026-09-16'
      }))
    }
    const page2 = {
      totalResult: 150,
      items: Array.from({ length: 50 }, (_, index) => ({
        xwbh: `b${index}`,
        xwbtqc: `通知 B${index}`,
        fbsj: '2026-09-15'
      }))
    }
    authPost
      .mockResolvedValueOnce({ status: 200, body: JSON.stringify(page1) })
      .mockResolvedValueOnce({ status: 200, body: JSON.stringify(page2) })

    const result = await new ZfAcademicApi().getPortalNoticeList()
    expect(result.items).toHaveLength(150)
    expect(result.truncated).toBe(false)
    expect(authPost.mock.calls[0][1]).toContain('queryModel.showCount=100')
    expect(authPost.mock.calls[0][1]).toContain('queryModel.currentPage=1')
    expect(authPost.mock.calls[1][1]).toContain('queryModel.currentPage=2')
  })

  it('消息请求带 flag=1 & sfyy=1', async () => {
    authPost.mockResolvedValue({
      status: 200,
      body: JSON.stringify({
        totalResult: 1,
        items: [{ zjxx: 'm1', xxbt: '调课提醒', cjsj: '2026-10-08 15:56:39' }]
      })
    })
    const result = await new ZfAcademicApi().getMessages()
    expect(result.items).toHaveLength(1)
    expect(authPost.mock.calls[0][1]).toContain('flag=1&sfyy=1')
  })

  it('全部消息使用 flag=0', async () => {
    authPost.mockResolvedValue({
      status: 200,
      body: JSON.stringify({
        totalResult: 1,
        items: [{ zjxx: 'm1', xxbt: '调课提醒', cjsj: '2026-10-08 15:56:39' }]
      })
    })
    await new ZfAcademicApi().getMessages('all')
    expect(authPost.mock.calls[0][1]).toContain('flag=0&sfyy=1')
  })

  it('返回登录页 HTML 时标记会话失效而不是解析失败', async () => {
    authGet.mockResolvedValue({
      status: 200,
      body: '<html><body><form id="login_slogin"></form></body></html>'
    })
    await expect(new ZfAcademicApi().getPortalNotices()).rejects.toMatchObject({
      sessionExpired: true
    })
  })
})
