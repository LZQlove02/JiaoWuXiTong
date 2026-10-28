import { authGet, isDebugBuild, loginWithSso as nativeSsoLogin, setAuthCookies } from './nativeHttp'
import { probeZfjwEndpoints } from './probe'
import { buildTerm, termParamsFor } from '../utils/term'

const COURSE_COLORS = ['#1B5FAA', '#2F855A', '#B7791F', '#805AD5', '#C05621', '#2C7A7B']
const SCHEDULE_ENDPOINT = 'https://zfjw.lzzy.net/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151'
const SNAPSHOT_KEY = 'lzpu.zf.snapshot.v1'

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
  return {
    id: xsxx.XH || '',
    name: xsxx.XM || '',
    className: xsxx.BJMC || '',
    major: xsxx.ZYMC || '',
    college: xsxx.JGMC || '',
    avatar: ''
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

export class ZfAcademicApi {
  remote = true

  constructor() {
    this.profile = null
    this.term = null
    this.schedule = null
    this.fetchedAt = ''
    this.offline = false
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
    const body = String(response.body || '')
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
    return [{ ...this.term }]
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

  async getGrades() {
    throw new Error('成绩接口尚未接入，暂不可用')
  }

  async getExams() {
    throw new Error('考试接口尚未接入，暂不可用')
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
