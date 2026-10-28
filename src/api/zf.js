import { authGet, authPost, isDebugBuild, loginWithSso as nativeSsoLogin, setAuthCookies } from './nativeHttp'
import { probeZfjwEndpoints } from './probe'
import { buildTerm, termParamsFor } from '../utils/term'

const COURSE_COLORS = ['#1B5FAA', '#2F855A', '#B7791F', '#805AD5', '#C05621', '#2C7A7B']
const SCHEDULE_ENDPOINT = 'https://zfjw.lzzy.net/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151'
const GRADE_PAGE_ENDPOINT = 'https://zfjw.lzzy.net/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005'
const GRADES_ENDPOINT = 'https://zfjw.lzzy.net/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005'
const EVALUATION_ENDPOINT = 'https://zfjw.lzzy.net/xspjgl/xspj_cxXspjIndex.html?doType=query&gnmkdm=N401605'
const COURSE_SELECTION_PAGE = 'https://zfjw.lzzy.net/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=N253512'
const SNAPSHOT_KEY = 'lzpu.zf.snapshot.v1'

const QUARTER_RANK = { 3: 0, 12: 1, 16: 2 }

function readSnapshot() {
  try {
    const raw = localStorage.getItem(SNAPSHOT_KEY)
    if (!raw) return null
    const data = JSON.parse(raw)
    if (!data || !data.profile || !data.term || !data.schedule) return null
    return data
  } catch (error) {
    return null
  }
}

function writeSnapshot(api) {
  try {
    localStorage.setItem(
      SNAPSHOT_KEY,
      JSON.stringify({
        profile: api.profile,
        term: api.term,
        schedule: api.schedule,
        fetchedAt: api.fetchedAt
      })
    )
  } catch (error) {
    // 空间不足时放弃快照，不影响本次使用
  }
}

export function clearScheduleSnapshot() {
  localStorage.removeItem(SNAPSHOT_KEY)
}

// 仅 debug 包：会话可用时探测成绩/考试/选课的真实入口，结果只进 logcat。
// 探测失败不影响正常流程。
function probeInDebugBuild(term) {
  isDebugBuild()
    .then((debug) => {
      if (debug) probeZfjwEndpoints(term).catch(() => {})
    })
    .catch(() => {})
}

function parseWeeks(text) {
  if (!text) return Array.from({ length: 16 }, (_, index) => index + 1)
  const weeks = new Set()
  for (const match of text.matchAll(/(\d+)\s*[-~至]\s*(\d+)\s*周?/g)) {
    const start = Number(match[1])
    const end = Number(match[2])
    for (let week = start; week <= end; week += 1) weeks.add(week)
  }
  for (const match of text.matchAll(/(\d+)\s*周/g)) {
    weeks.add(Number(match[1]))
  }
  let result = [...weeks].sort((a, b) => a - b)
  if (!result.length) result = Array.from({ length: 16 }, (_, index) => index + 1)
  if (/单/.test(text) && !/双/.test(text)) result = result.filter((week) => week % 2 === 1)
  if (/双/.test(text) && !/单/.test(text)) result = result.filter((week) => week % 2 === 0)
  return result
}

function parseSections(text) {
  const match = String(text || '').match(/(\d+)\s*[-~至]\s*(\d+)/)
  if (match) return [Number(match[1]), Number(match[2])]
  const single = String(text || '').match(/(\d+)/)
  const value = single ? Number(single[1]) : 1
  return [value, value]
}

function cleanRoom(value) {
  return String(value || '').replace(/\(\d+\s*人\)/g, '').trim()
}

function mapProfile(xsxx) {
  const id = String(xsxx.XH || '')
  const yearFromGrade = Number(String(xsxx.NJDM_ID || xsxx.NJMC || '').match(/\d{4}/)?.[0])
  const yearFromId = Number(id.slice(0, 4))
  return {
    id,
    name: xsxx.XM || '',
    className: xsxx.BJMC || '',
    major: xsxx.ZYMC || '',
    college: xsxx.JGMC || '',
    avatar: '',
    enrollmentYear: Number.isFinite(yearFromGrade) && yearFromGrade >= 2000
      ? yearFromGrade
      : Number.isFinite(yearFromId) && yearFromId >= 2000
        ? yearFromId
        : null
  }
}

function mapTerm(xsxx, params) {
  const xnm = String(xsxx.XNM || params.xnm)
  const xqm = String(xsxx.XQM || params.xqm)
  const yearLabel = xsxx.XNMC || xnm
  const termLabel = xsxx.XQMMC === '1' ? '第一学期' : xsxx.XQMMC === '2' ? '第二学期' : `第${xsxx.XQMMC || ''}学期`
  return {
    xnm,
    xqm,
    label: `${yearLabel} 学年${termLabel}`,
    startDate: buildTerm(xnm, xqm).startDate,
    isCurrent: true
  }
}

export function mapSchedule(payload, term) {
  const list = Array.isArray(payload?.kbList) ? payload.kbList : []
  const courses = new Map()
  const sessions = []
  const seen = new Set()

  for (const item of list) {
    const id = String(item.kch || item.kcmc || '')
    if (!id || !item.kcmc) continue
    if (!courses.has(id)) {
      courses.set(id, {
        id,
        code: String(item.kch || ''),
        name: item.kcmc,
        teacher: item.xm || '',
        credits: Number(item.xf) || 0,
        type: item.kclbmc || item.kcxz || '',
        color: COURSE_COLORS[courses.size % COURSE_COLORS.length]
      })
    }
    const [startSection, endSection] = parseSections(item.jcs || item.jc)
    const weeks = parseWeeks(item.zcd || '')
    const weekday = Number(item.xqj) || 1
    const room = cleanRoom(item.cdmc)
    const key = `${id}|${weekday}|${startSection}|${endSection}|${weeks.join(',')}|${room}`
    if (seen.has(key)) continue
    seen.add(key)
    sessions.push({ courseId: id, weekday, startSection, endSection, weeks, room })
  }

  return { term, courses: [...courses.values()], sessions }
}

function toNumber(value) {
  const text = String(value ?? '').trim()
  if (!text) return null
  const num = Number(text)
  return Number.isFinite(num) ? num : null
}

function parseSelects(html) {
  const selects = []
  for (const match of String(html || '').matchAll(/<select\b([^>]*)>([\s\S]*?)<\/select>/gi)) {
    const attr = match[1].match(/(?:name|id)\s*=\s*["']([^"']+)["']/i)
    const options = []
    for (const option of match[2].matchAll(/<option\b([^>]*)>([\s\S]*?)<\/option>/gi)) {
      const value = option[1].match(/value\s*=\s*["']([^"']*)["']/i)
      options.push({
        value: value ? value[1] : '',
        label: option[2].replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim()
      })
    }
    selects.push({ name: attr ? attr[1] : '', options })
  }
  return selects
}

function optionYear(option) {
  const match = `${option.value} ${option.label}`.match(/\d{4}/)
  const year = match ? Number(match[0]) : NaN
  return Number.isFinite(year) && year >= 2000 && year <= 2100 ? String(year) : ''
}

function optionQuarter(option) {
  if (/第一/.test(option.label)) return '3'
  if (/第二/.test(option.label)) return '12'
  if (/第三/.test(option.label)) return '16'
  const value = String(option.value || '').trim()
  if (['3', '12', '16'].includes(value)) return value
  if (value === '1') return '3'
  if (value === '2') return '12'
  return ''
}

function sortTerms(terms) {
  return terms.sort((a, b) => Number(b.xnm) - Number(a.xnm)
    || (QUARTER_RANK[b.xqm] ?? 0) - (QUARTER_RANK[a.xqm] ?? 0))
}

function isAfterCurrent(term, current) {
  if (!current?.xnm || !current?.xqm) return false
  const year = Number(term.xnm)
  const currentYear = Number(current.xnm)
  if (year !== currentYear) return year > currentYear
  return (QUARTER_RANK[term.xqm] ?? 0) > (QUARTER_RANK[current.xqm] ?? 0)
}

// 从成绩查询页的学年 / 学期下拉框解析全部学期；页面结构变化时返回空数组，由调用方兜底
export function parseTermsFromHtml(html, currentTerm = null, options = {}) {
  const selects = parseSelects(html)
  const yearSelect = selects.find((select) => /xnm/i.test(select.name))
    || selects.find((select) => select.options.some((option) => /学年/.test(option.label)))
  if (!yearSelect) return []

  const quarterSelect = selects.find((select) => /xqm/i.test(select.name))
    || selects.find((select) => select.options.some((option) => /学期/.test(option.label)))
  // 教务下拉框包含系统全部年份，按入学年份收口，避免出现入学前的学期
  const enrollmentYear = Number(options?.enrollmentYear)
  const currentYear = Number(currentTerm?.xnm) || new Date().getFullYear()
  const minYear = Number.isFinite(enrollmentYear) && enrollmentYear >= 2000
    ? enrollmentYear
    : currentYear - 5
  const years = [...new Set(yearSelect.options.map(optionYear).filter(Boolean))]
    .filter((year) => Number(year) >= minYear)
  let quarters = quarterSelect
    ? [...new Set(quarterSelect.options.map(optionQuarter).filter(Boolean))]
    : []
  if (!quarters.length) quarters = ['3', '12']
  if (quarters.length === 1) quarters = ['3', '12']

  const terms = new Map()
  const add = (xnm, xqm) => {
    if (!xnm || !xqm) return
    const isCurrent = currentTerm
      && String(currentTerm.xnm) === String(xnm)
      && String(currentTerm.xqm) === String(xqm)
    const built = buildTerm(xnm, xqm)
    terms.set(`${xnm}_${xqm}`, {
      xnm: String(xnm),
      xqm: String(xqm),
      label: built.label,
      startDate: built.startDate,
      isCurrent: Boolean(isCurrent)
    })
  }
  for (const year of years) {
    for (const quarter of quarters) add(year, quarter)
  }
  if (currentTerm?.xnm && currentTerm?.xqm) add(currentTerm.xnm, currentTerm.xqm)
  // 学期下拉可能包含尚未开始的学期，只保留当前及以前的学期
  return sortTerms([...terms.values()].filter((item) => !isAfterCurrent(item, currentTerm)))
}

function summarizeGrades(grades) {
  let totalCredits = 0
  let weighted = 0
  let weight = 0
  for (const item of grades) {
    if (item.credit === null || item.credit <= 0) continue
    totalCredits += item.credit
    if (item.gradePoint === null) continue
    weight += item.credit
    weighted += item.gradePoint * item.credit
  }
  return {
    gpa: weight > 0 ? Math.round((weighted / weight) * 100) / 100 : null,
    totalCredits: Math.round(totalCredits * 10) / 10,
    count: grades.length
  }
}

export function mapGrades(payload, term) {
  const items = Array.isArray(payload?.items) ? payload.items : []
  const grades = items
    .map((item, index) => {
      const score = String(item.cj ?? '').trim()
      return {
        id: `${item.key || item.row_id || item.kch || 'course'}_${index}`,
        courseId: String(item.kch || ''),
        courseName: String(item.kcmc || '').trim(),
        score,
        credit: toNumber(item.xf),
        gradePoint: toNumber(item.jd),
        category: String(item.kclbmc || '').trim(),
        examType: String(item.ksxz || '').trim(),
        teacher: String(item.jsxm || '').trim()
      }
    })
    .filter((item) => item.courseName)

  return {
    term: term ? { ...term } : null,
    grades,
    summary: summarizeGrades(grades),
    source: '教务系统（实时）',
    fetchedAt: new Date().toISOString()
  }
}

// 汇总多个学期的成绩：只保留有成绩的学期，汇总绩点与学分
export function mergeGradeGroups(results) {
  const groups = (Array.isArray(results) ? results : [])
    .filter((result) => result?.term && Array.isArray(result.grades))
    .map((result) => ({
      key: `${result.term.xnm}_${result.term.xqm}`,
      termLabel: result.term.label,
      grades: result.grades
    }))
    .filter((group) => group.grades.length)

  return {
    all: true,
    groups,
    summary: summarizeGrades(groups.flatMap((group) => group.grades)),
    source: '教务系统（实时）',
    fetchedAt: new Date().toISOString()
  }
}

// 评教记录只读解析：不包含任何提交动作，只把列表映射成展示模型
export function mapEvaluations(payload) {
  const items = Array.isArray(payload?.items) ? payload.items : []
  const evaluations = items
    .map((item, index) => {
      const rawStatus = String(item?.tjztmc || '').trim()
      const submitted = String(item?.tjzt) === '1' || /提交/.test(rawStatus)
      return {
        id: `${item?.jxb_id || item?.kch_id || 'evaluation'}_${index}`,
        courseName: String(item?.kcmc || '').trim(),
        teacher: String(item?.jzgmc || '').trim(),
        className: String(item?.jxbmc || '').trim(),
        status: submitted ? '已提交' : rawStatus || '未提交',
        submitted
      }
    })
    .filter((item) => item.courseName)
  const total = Number(payload?.totalResult)
  return {
    total: Number.isFinite(total) && total >= evaluations.length ? total : evaluations.length,
    items: evaluations,
    source: '教务系统（只读）',
    fetchedAt: new Date().toISOString()
  }
}

// 选课只读状态：仅读取页面里的 iskxk 开关，不加载也不触发选课数据操作
export function parseCourseSelectionStatus(html) {
  const text = String(html || '')
  const match = text.match(/id=["']iskxk["'][^>]*value=["']([^"']*)["']/i)
    || text.match(/value=["']([^"']*)["'][^>]*id=["']iskxk["']/i)
  const open = match?.[1] === '1'
  return {
    open,
    label: open ? '选课已开放' : '当前不在选课时间',
    description: open
      ? '选课窗口已开放。本应用仅同步状态，不提供选课或退课操作。'
      : '请在教务系统开放时间内完成选课或退课；本应用仅同步状态，不提供相关操作。'
  }
}

export class ZfAcademicApi {
  remote = true

  constructor() {
    this.profile = null
    this.term = null
    this.schedule = null
    this.fetchedAt = ''
    this.offline = false
    this.terms = null
  }

  // 冷启动先用本地快照渲染，随后由后台刷新替换为实时数据
  applySnapshot() {
    const snapshot = readSnapshot()
    if (!snapshot) return null
    this.profile = snapshot.profile
    this.term = snapshot.term
    this.schedule = snapshot.schedule
    this.fetchedAt = snapshot.fetchedAt
    this.offline = true
    return this.profile
  }

  async loginWithSso() {
    const result = await nativeSsoLogin()
    if (!result?.cookieHeader) throw new Error('未获取到登录会话')
    await setAuthCookies(result.cookieHeader)
    // 刚登录必须现场校验会话，不能回退到旧快照，否则会把登录失败伪装成成功
    return this.restoreSession({ allowSnapshot: false })
  }

  async restoreSession({ allowSnapshot = true } = {}) {
    const snapshot = readSnapshot()
    let params = termParamsFor()
    try {
      let payload = await this.fetchSchedule(params)
      if (!payload?.xsxx) {
        const alternative = params.xqm === '3'
          ? { xnm: params.xnm, xqm: '12' }
          : { xnm: String(Number(params.xnm) + 1), xqm: '3' }
        const second = await this.fetchSchedule(alternative)
        if (second?.xsxx) {
          params = alternative
          payload = second
        }
      }
      if (!payload?.xsxx) {
        const failure = new Error('课表数据缺少必要字段')
        failure.parseError = true
        throw failure
      }

      this.profile = mapProfile(payload.xsxx)
      this.term = mapTerm(payload.xsxx, params)
      this.schedule = mapSchedule(payload, this.term)
      this.fetchedAt = new Date().toISOString()
      this.offline = false
      writeSnapshot(this)
      probeInDebugBuild(this.term)
      return this.profile
    } catch (error) {
      // 网络不可达时用上次成功的快照保持登录态，避免离线冷启动被踢回登录页
      if (error?.network && allowSnapshot && snapshot) {
        this.profile = snapshot.profile
        this.term = snapshot.term
        this.schedule = snapshot.schedule
        this.fetchedAt = snapshot.fetchedAt
        this.offline = true
        return this.profile
      }
      throw error
    }
  }

  async fetchSchedule(params) {
    const url = `${SCHEDULE_ENDPOINT}&xnm=${encodeURIComponent(params.xnm)}&xqm=${encodeURIComponent(params.xqm)}`
    let response
    try {
      response = await authGet(url)
    } catch (error) {
      const message = String(error?.message || '')
      // 未登录时教务系统会 302 到 http 登录页，被网络策略拦截，应判定为会话失效
      if (message.includes('仅允许 HTTPS 请求')) {
        const failure = new Error('登录会话已失效')
        failure.sessionExpired = true
        throw failure
      }
      const failure = new Error('无法连接教务系统')
      failure.network = true
      throw failure
    }
    if (response.status !== 200) {
      const failure = new Error(`课表接口返回 ${response.status}`)
      failure.status = response.status
      failure.sessionExpired = response.status === 401 || response.status === 403
      throw failure
    }
    const body = String(response.body || '').replace(/^\uFEFF/, '')
    // 会话失效时教务系统会返回 200 + 登录页 HTML，不能只按状态码判定成功
    if (/^\s*</.test(body)) {
      const expired = /login_slogin|统一身份认证|authserver/i.test(body)
      const failure = new Error(expired ? '登录会话已失效' : '课表数据解析失败')
      if (expired) failure.sessionExpired = true
      else failure.parseError = true
      throw failure
    }
    try {
      return JSON.parse(body)
    } catch (error) {
      const failure = new Error('课表数据解析失败')
      failure.parseError = true
      throw failure
    }
  }

  async getProfile() {
    if (!this.profile) throw new Error('尚未登录')
    return { ...this.profile }
  }

  async getTerms() {
    if (!this.term) throw new Error('尚未登录')
    if (this.terms?.length) return this.terms.map((term) => ({ ...term }))
    const fromPage = await this.fetchTerms()
    const terms = fromPage.length ? fromPage : this.buildFallbackTerms()
    this.terms = terms
    return terms.map((term) => ({ ...term }))
  }

  // 成绩查询页的学年 / 学期下拉框包含全部可选学期
  async fetchTerms() {
    try {
      const response = await authGet(GRADE_PAGE_ENDPOINT)
      if (response?.status !== 200) return []
      const body = String(response.body || '').replace(/^\uFEFF/, '')
      if (!/^\s*</.test(body)) return []
      return parseTermsFromHtml(body, this.term, { enrollmentYear: this.profile?.enrollmentYear })
    } catch (error) {
      return []
    }
  }

  // 页面结构无法解析时，按入学年份推算到当前学期，保证学期选择始终可用
  buildFallbackTerms() {
    const currentXnm = Number(this.term?.xnm) || new Date().getFullYear()
    const currentXqm = String(this.term?.xqm || '3')
    const enrollmentYear = Number(this.profile?.enrollmentYear)
    const start = Number.isFinite(enrollmentYear)
      && enrollmentYear >= 2000
      && enrollmentYear <= currentXnm
      ? enrollmentYear
      : currentXnm - 3
    const currentRank = QUARTER_RANK[currentXqm] ?? 0
    const terms = []
    for (let year = start; year <= currentXnm; year += 1) {
      const quarters = year < currentXnm
        ? ['3', '12']
        : ['3', '12', '16'].filter((quarter) => (QUARTER_RANK[quarter] ?? 0) <= currentRank)
      for (const quarter of quarters) {
        const built = buildTerm(year, quarter)
        terms.push({
          xnm: String(year),
          xqm: quarter,
          label: built.label,
          startDate: built.startDate,
          isCurrent: year === currentXnm && quarter === currentXqm
        })
      }
    }
    return sortTerms(terms)
  }

  async getSchedule(term) {
    if (!this.term) throw new Error('尚未登录')
    if (term && `${term.xnm}_${term.xqm}` !== `${this.term.xnm}_${this.term.xqm}`) {
      const payload = await this.fetchSchedule(term)
      const nextTerm = mapTerm(payload.xsxx || {}, term)
      this.term = nextTerm
      this.schedule = mapSchedule(payload, nextTerm)
      this.fetchedAt = new Date().toISOString()
      this.offline = false
      writeSnapshot(this)
    }
    return {
      ...this.schedule,
      source: this.offline ? '教务系统（离线缓存）' : '教务系统（实时）',
      fetchedAt: this.fetchedAt
    }
  }

  async fetchGradesPage(term, page, pageSize = 100) {
    const form = `xnm=${encodeURIComponent(term.xnm)}&xqm=${encodeURIComponent(term.xqm)}`
      + `&queryModel.showCount=${pageSize}&queryModel.currentPage=${page}`
    let response
    try {
      response = await authPost(GRADES_ENDPOINT, form)
    } catch (error) {
      const message = String(error?.message || '')
      if (message.includes('仅允许 HTTPS 请求')) {
        const failure = new Error('登录会话已失效')
        failure.sessionExpired = true
        throw failure
      }
      const failure = new Error('无法连接教务系统')
      failure.network = true
      throw failure
    }
    if (response?.status !== 200) {
      const failure = new Error(`成绩接口返回 ${response?.status ?? '未知状态'}`)
      failure.status = response?.status
      failure.sessionExpired = response?.status === 401 || response?.status === 403
      throw failure
    }
    const body = String(response.body || '').replace(/^\uFEFF/, '')
    if (/^\s*</.test(body)) {
      const expired = /login_slogin|统一身份认证|authserver/i.test(body)
      const failure = new Error(expired ? '登录会话已失效' : '成绩数据解析失败')
      if (expired) failure.sessionExpired = true
      else failure.parseError = true
      throw failure
    }
    let payload
    try {
      payload = JSON.parse(body)
    } catch (error) {
      const failure = new Error('成绩数据解析失败')
      failure.parseError = true
      throw failure
    }
    if (!payload || !Array.isArray(payload.items)) {
      const failure = new Error('成绩数据解析失败')
      failure.parseError = true
      throw failure
    }
    return payload
  }

  // 正方成绩接口默认只返回 10 条，必须带 queryModel.showCount/currentPage 翻页
  async getGrades(term) {
    if (!this.term) throw new Error('尚未登录')
    const params = term || this.term
    const first = await this.fetchGradesPage(params, 1)
    const items = [...first.items]
    const totalPage = Math.min(Number(first.totalPage) || 1, 20)
    for (let page = 2; page <= totalPage; page += 1) {
      const next = await this.fetchGradesPage(params, page)
      items.push(...next.items)
    }
    return mapGrades({ items, totalResult: first.totalResult }, params)
  }

  // 全部学期：逐学期只读查询后汇总；单个学期失败时保留其余学期，并记录失败项
  async getAllGrades(terms) {
    if (!this.term) throw new Error('尚未登录')
    const list = Array.isArray(terms) && terms.length ? terms : await this.getTerms()
    const results = []
    const partial = []
    for (const term of list) {
      try {
        results.push(await this.getGrades(term))
      } catch (error) {
        if (error?.sessionExpired) throw error
        partial.push({ termLabel: term.label, message: error?.message || '加载失败' })
      }
    }
    if (!results.length && partial.length) {
      const failure = new Error(partial[0].message || '成绩加载失败')
      failure.network = true
      throw failure
    }
    const merged = mergeGradeGroups(results)
    merged.partial = partial
    return merged
  }

  async getExams() {
    throw new Error('考试接口尚未接入，暂不可用')
  }

  async getEvaluations() {
    if (!this.term) throw new Error('尚未登录')
    let response
    try {
      response = await authPost(EVALUATION_ENDPOINT, 'page=1&rows=10&sidx=&sord=asc')
    } catch (error) {
      const message = String(error?.message || '')
      if (message.includes('仅允许 HTTPS 请求')) {
        const failure = new Error('登录会话已失效')
        failure.sessionExpired = true
        throw failure
      }
      const failure = new Error('无法连接教务系统')
      failure.network = true
      throw failure
    }
    if (response?.status !== 200) {
      const failure = new Error(`评教接口返回 ${response?.status ?? '未知状态'}`)
      failure.status = response?.status
      failure.sessionExpired = response?.status === 401 || response?.status === 403
      throw failure
    }
    const body = String(response.body || '').replace(/^\uFEFF/, '')
    if (/^\s*</.test(body)) {
      const expired = /login_slogin|统一身份认证|authserver/i.test(body)
      const failure = new Error(expired ? '登录会话已失效' : '评教数据解析失败')
      if (expired) failure.sessionExpired = true
      else failure.parseError = true
      throw failure
    }
    let payload
    try {
      payload = JSON.parse(body)
    } catch (error) {
      const failure = new Error('评教数据解析失败')
      failure.parseError = true
      throw failure
    }
    if (!payload || !Array.isArray(payload.items)) {
      const failure = new Error('评教数据解析失败')
      failure.parseError = true
      throw failure
    }
    return mapEvaluations(payload)
  }

  async getCourseSelectionStatus() {
    if (!this.term) throw new Error('尚未登录')
    let response
    try {
      response = await authGet(COURSE_SELECTION_PAGE)
    } catch (error) {
      const message = String(error?.message || '')
      if (message.includes('仅允许 HTTPS 请求')) {
        const failure = new Error('登录会话已失效')
        failure.sessionExpired = true
        throw failure
      }
      const failure = new Error('无法连接教务系统')
      failure.network = true
      throw failure
    }
    if (response?.status !== 200) {
      const failure = new Error(`选课状态返回 ${response?.status ?? '未知状态'}`)
      failure.status = response?.status
      failure.sessionExpired = response?.status === 401 || response?.status === 403
      throw failure
    }
    const body = String(response.body || '').replace(/^\uFEFF/, '')
    if (/^\s*</.test(body) && /login_slogin|统一身份认证|authserver/i.test(body)) {
      const failure = new Error('登录会话已失效')
      failure.sessionExpired = true
      throw failure
    }
    return {
      ...parseCourseSelectionStatus(body),
      source: '教务系统（只读）',
      fetchedAt: new Date().toISOString()
    }
  }

  async getCourse(id) {
    if (!this.schedule) return null
    const course = this.schedule.courses.find((item) => item.id === id)
    if (!course) return null
    return {
      ...course,
      sessions: this.schedule.sessions.filter((item) => item.courseId === id)
    }
  }
}
