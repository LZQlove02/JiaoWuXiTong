import { authGet, loginWithSso as nativeSsoLogin, setAuthCookies } from './nativeHttp'
import { parseWeeks } from './importer'

const COURSE_COLORS = ['#1B5FAA', '#2F855A', '#B7791F', '#805AD5', '#C05621', '#2C7A7B']
const SCHEDULE_ENDPOINT = 'https://zfjw.lzzy.net/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151'

function termParamsFor(date = new Date()) {
  const year = date.getFullYear()
  const month = date.getMonth() + 1
  if (month >= 8) return { xnm: String(year), xqm: '3' }
  if (month === 1) return { xnm: String(year - 1), xqm: '3' }
  return { xnm: String(year - 1), xqm: '12' }
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
    startDate: xqm === '3' ? `${xnm}-08-31` : `${Number(xnm) + 1}-02-23`,
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
  constructor() {
    this.profile = null
    this.term = null
    this.schedule = null
    this.fetchedAt = ''
  }

  async loginWithSso() {
    const result = await nativeSsoLogin()
    if (!result?.cookieHeader) throw new Error('未获取到登录会话')
    await setAuthCookies(result.cookieHeader)
    return this.restoreSession()
  }

  async restoreSession() {
    let params = termParamsFor()
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
    if (!payload?.xsxx) throw new Error('登录会话已失效')

    this.profile = mapProfile(payload.xsxx)
    this.term = mapTerm(payload.xsxx, params)
    this.schedule = mapSchedule(payload, this.term)
    this.fetchedAt = new Date().toISOString()
    return this.profile
  }

  async fetchSchedule(params) {
    const url = `${SCHEDULE_ENDPOINT}&xnm=${encodeURIComponent(params.xnm)}&xqm=${encodeURIComponent(params.xqm)}`
    let response
    try {
      response = await authGet(url)
    } catch (error) {
      const failure = new Error('无法连接教务系统')
      failure.network = true
      throw failure
    }
    if (response.status !== 200) {
      throw new Error(`课表接口返回 ${response.status}`)
    }
    try {
      return JSON.parse(response.body)
    } catch (error) {
      throw new Error('课表数据解析失败')
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
    }
    return { ...this.schedule, source: '教务系统（实时）', fetchedAt: this.fetchedAt }
  }

  async getGrades() {
    throw new Error('成绩接口尚未接入，当前不显示模拟成绩')
  }

  async getExams() {
    throw new Error('考试接口尚未接入，当前不显示模拟考试')
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
