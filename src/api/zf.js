import {
  authGet,
  authPost,
  isDebugBuild,
  loginWithSso as nativeSsoLogin,
  setAuthCookies,
  silentRelogin
} from './nativeHttp'
import { probeZfjwEndpoints } from './probe'
import { buildTerm, termCandidatesFor, termParamsFor } from '../utils/term'

const COURSE_COLORS = ['#1B5FAA', '#2F855A', '#B7791F', '#805AD5', '#C05621', '#2C7A7B']
const SCHEDULE_ENDPOINT = 'https://zfjw.lzzy.net/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151'
const GRADE_PAGE_ENDPOINT = 'https://zfjw.lzzy.net/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005'
const GRADES_ENDPOINT = 'https://zfjw.lzzy.net/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005'
const EVALUATION_ENDPOINT = 'https://zfjw.lzzy.net/xspjgl/xspj_cxXspjIndex.html?doType=query&gnmkdm=N401605'
const COURSE_SELECTION_PAGE = 'https://zfjw.lzzy.net/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=N253512'
const PORTAL_NOTICE_INDEX = 'https://zfjw.lzzy.net/xtgl/index_cxNews.html?localeKey=zh_CN'
const PORTAL_NOTICE_LIST = 'https://zfjw.lzzy.net/xtgl/xwck_cxMoreXwList.html?doType=query'
const PORTAL_NOTICE_DETAIL = 'https://zfjw.lzzy.net/xtgl/xwck_ckXw.html'
const PORTAL_MESSAGES = 'https://zfjw.lzzy.net/xtgl/index_cxDbsy.html?doType=query'
const SNAPSHOT_KEY = 'jiaowu.zf.snapshot.v1'
// 个人数据本地保留上限：超期快照不再用于离线恢复，并主动删除
export const SNAPSHOT_RETENTION_MS = 30 * 24 * 60 * 60 * 1000

const QUARTER_RANK = { 3: 0, 12: 1, 16: 2 }

function readSnapshot() {
  try {
    const raw = localStorage.getItem(SNAPSHOT_KEY)
    if (!raw) return null
    const data = JSON.parse(raw)
    if (!data || !data.profile || !data.term || !data.schedule) return null
    const fetchedTime = Date.parse(data.fetchedAt)
    if (!Number.isFinite(fetchedTime) || Date.now() - fetchedTime > SNAPSHOT_RETENTION_MS) {
      localStorage.removeItem(SNAPSHOT_KEY)
      return null
    }
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
        // 只保留离线展示必需的字段，不落头像等无关信息
        profile: {
          id: api.profile.id,
          name: api.profile.name,
          className: api.profile.className,
          major: api.profile.major,
          college: api.profile.college,
          enrollmentYear: api.profile.enrollmentYear
        },
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

// 仅 debug 包：会话可用时探测成绩/选课的真实入口，结果只进 logcat。
// 探测失败不影响正常流程。
function probeInDebugBuild(term) {
  isDebugBuild()
    .then((debug) => {
      if (debug) probeZfjwEndpoints(term).catch(() => {})
    })
    .catch(() => {})
}

function parseWeeks(text) {
  // 与课表页/桌面组件的 20 周上限保持一致，接口缺周次时不会漏掉第 17-20 周
  if (!text) return Array.from({ length: 20 }, (_, index) => index + 1)
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
  if (!result.length) result = Array.from({ length: 20 }, (_, index) => index + 1)
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

// 小组件与成绩页共用同一套绩点/学分口径，避免两处算法漂移
export function summarizeGrades(grades) {
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

function dedupeEvaluations(items) {
  const seen = new Set()
  return items.filter((item, index) => {
    const key = String(item?.jxb_id || item?.kch_id || `row_${index}`)
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
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

// 学生门户通知 / 消息的只读解析，不包含任何“标记已读”或提交动作
function htmlToText(html) {
  const source = String(html || '')
  if (typeof DOMParser === 'undefined') {
    return source.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
  }
  const doc = new DOMParser().parseFromString(source, 'text/html')
  return (doc.body?.textContent || '').replace(/\s+/g, ' ').trim()
}

function cleanNoticeTitle(text) {
  return String(text || '')
    .replace(/\d{4}-\d{2}-\d{2}/g, ' ')
    .replace(/【置顶】/g, ' ')
    .replace(/【通知】/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function parseFailure(message) {
  const failure = new Error(message)
  failure.parseError = true
  return failure
}

function hashString(value) {
  let hash = 0
  const text = String(value || '')
  for (let index = 0; index < text.length; index += 1) {
    hash = (hash * 31 + text.charCodeAt(index)) | 0
  }
  return Math.abs(hash).toString(36)
}

export function parsePortalNoticeIndex(html) {
  const text = String(html || '')
  const seen = new Set()
  const items = []
  for (const match of text.matchAll(/<a[^>]+href=["']([^"']*xwck_ckXw\.html\?xwbh=([^"'&]+)[^"']*)["'][^>]*>([\s\S]*?)<\/a>/gi)) {
    const id = match[2]
    if (!id || seen.has(id)) continue
    seen.add(id)
    const raw = htmlToText(match[3])
    const dateMatch = raw.match(/\d{4}-\d{2}-\d{2}/)
    const title = cleanNoticeTitle(raw)
    if (!title) continue
    items.push({
      id,
      title,
      date: dateMatch ? dateMatch[0] : '',
      pinned: /【置顶】/.test(raw)
    })
  }
  if (!items.length) {
    if (/xwck_ckXw\.html/.test(text)) {
      throw parseFailure('通知列表结构无法识别')
    }
    if (!/暂无|无数据/.test(text)) {
      throw parseFailure('通知列表结构无法识别')
    }
  }
  return {
    total: items.length,
    items,
    source: '教务系统（学生门户）',
    fetchedAt: new Date().toISOString()
  }
}

export function parsePortalNoticeList(payload) {
  if (!Array.isArray(payload?.items)) {
    throw parseFailure('通知数据缺少 items')
  }
  const rawItems = payload.items
  const items = rawItems
    .map((item) => ({
      id: String(item?.xwbh || item?.row_id || '').trim(),
      title: String(item?.xwbtqc || item?.xwbt || '').trim(),
      date: String(item?.fbsj || '').slice(0, 10),
      publisher: String(item?.fbr || '').trim(),
      pinned: String(item?.sfzd) === '1'
    }))
    .filter((item) => item.id && item.title)
  if (rawItems.length > 0 && items.length === 0) {
    throw parseFailure('通知数据字段无法识别')
  }
  const total = Number(payload?.totalResult)
  if (Number.isFinite(total) && total < items.length) {
    throw parseFailure('通知数据总数异常')
  }
  const resolvedTotal = Number.isFinite(total) ? total : items.length
  if (rawItems.length === 0 && resolvedTotal > 0) {
    throw parseFailure('通知数据为空但总数非零')
  }
  return {
    total: resolvedTotal,
    totalKnown: Number.isFinite(total),
    items,
    source: '教务系统（学生门户）',
    fetchedAt: new Date().toISOString()
  }
}

// 详情页里的图片/附件地址可能相对当前通知页，统一用 URL 构造解析并限制在 zfjw 域
function resolveZfjwUrl(href, baseUrl) {
  if (!href) return ''
  try {
    const url = new URL(href, baseUrl || undefined)
    if (url.protocol !== 'https:' || url.hostname !== 'zfjw.lzzy.net') return ''
    return url.toString()
  } catch (error) {
    return ''
  }
}

export function parsePortalNoticeDetail(html, pageUrl = '') {
  const source = String(html || '')
  if (typeof DOMParser === 'undefined') {
    throw parseFailure('通知详情解析环境不可用')
  }
  const doc = new DOMParser().parseFromString(source, 'text/html')
  const title = (doc.querySelector('h3')?.textContent || '').trim()
  if (!title) throw parseFailure('通知详情缺少标题')
  const meta = (doc.querySelector('.news_title1')?.textContent || '').replace(/\s+/g, ' ')
  const publishedAt = (meta.match(/发布时间[:：]\s*([0-9-]+\s+[0-9:]+)/) || [])[1] || ''
  const views = Number((meta.match(/浏览人数[:：]\s*(\d+)/) || [])[1]) || null
  const content = doc.querySelector('.news_con')
  const paragraphs = []
  if (content) {
    for (const p of content.querySelectorAll('p')) {
      const text = (p.textContent || '').replace(/\s+/g, ' ').trim()
      if (text) paragraphs.push(text)
    }
    if (!paragraphs.length) {
      const text = (content.textContent || '').replace(/\s+/g, ' ').trim()
      if (text) paragraphs.push(text)
    }
  }
  const rawImages = content ? [...content.querySelectorAll('img')] : []
  const imageUrls = []
  for (const img of rawImages) {
    const url = resolveZfjwUrl(img.getAttribute('src'), pageUrl)
    if (!url) continue
    imageUrls.push({ url, alt: (img.getAttribute('alt') || '').trim() })
  }
  // 保留原始数量用于「还有 N 张图需在教务系统查看」的提示
  const images = Math.max(imageUrls.length, rawImages.length)
  const attachments = [...doc.querySelectorAll('a[href*="filename="]')]
    .map((a) => {
      const href = a.getAttribute('href') || ''
      const match = href.match(/filename=([^&]+)/)
      let name = ''
      try {
        name = match ? decodeURIComponent(match[1]) : ''
      } catch (error) {
        name = ''
      }
      const url = resolveZfjwUrl(href, pageUrl)
      return { name: name || (a.textContent || '').trim(), href, url }
    })
    .filter((item) => item.name)
  if (!paragraphs.length && !attachments.length && images === 0) {
    throw parseFailure('通知详情没有可展示内容')
  }
  return {
    title,
    publishedAt,
    views,
    paragraphs,
    images,
    imageUrls,
    attachments,
    url: pageUrl || '',
    source: '教务系统（学生门户）',
    fetchedAt: new Date().toISOString()
  }
}

// 兜底 id 只在缺少服务端主键时使用。同一页里「同标题 + 同创建时间」的消息很常见
// （批量下发/模板通知），纯哈希会把它们合并成一条；用响应内出现序号保证唯一，
// 同时保留「同一条消息被重复下发时仍可去重」的收益。
function messageId(item, occurrences) {
  const explicit = String(item?.zjxx || item?.id || item?.w_id || '').trim()
  if (explicit) return explicit
  const base = `msg_${hashString(`${item?.xxbt || ''}|${item?.cjsj || ''}`)}`
  const count = (occurrences.get(base) || 0) + 1
  occurrences.set(base, count)
  return count > 1 ? `${base}#${count}` : base
}

export function parseMessages(payload) {
  if (!Array.isArray(payload?.items)) {
    throw parseFailure('消息数据缺少 items')
  }
  const rawItems = payload.items
  const occurrences = new Map()
  const items = rawItems
    .map((item) => ({
      id: messageId(item, occurrences),
      title: String(item?.xxbt || '').trim(),
      content: htmlToText(item?.xxnr || ''),
      time: String(item?.cjsj || '').trim()
    }))
    .filter((item) => item.title)
    .sort((a, b) => b.time.localeCompare(a.time))
  if (rawItems.length > 0 && items.length === 0) {
    throw parseFailure('消息数据字段无法识别')
  }
  const total = Number(payload?.totalResult)
  if (Number.isFinite(total) && total < items.length) {
    throw parseFailure('消息数据总数异常')
  }
  const resolvedTotal = Number.isFinite(total) ? total : items.length
  if (rawItems.length === 0 && resolvedTotal > 0) {
    throw parseFailure('消息数据为空但总数非零')
  }
  return {
    total: resolvedTotal,
    // totalResult 缺失时 total 只是「本页条数」的兜底，不能用来判断「是否已拉完」
    totalKnown: Number.isFinite(total),
    items,
    source: '教务系统（只读）',
    fetchedAt: new Date().toISOString()
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
    // 课表接口原文：交给原生变更引擎对账，避免前后两端各解析一遍
    this.lastRawPayload = ''
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
    this.lastRawPayload = ''
    return this.profile
  }

  // 快照对应的课表视图：字段与 getSchedule 返回保持一致，供冷启动首帧直接渲染；
  // 网络刷新失败时由 getSchedule 标记为 offline，这里先按正常数据显示，避免启动瞬间闪出「离线缓存」
  snapshotSchedule() {
    if (!this.schedule) return null
    return {
      ...this.schedule,
      offline: false,
      source: '教务系统（本机缓存）',
      fetchedAt: this.fetchedAt,
      rawPayload: '',
      term: this.term
    }
  }

  // 统一处理网络失败、状态码、登录页 HTML 与 BOM；各业务方法只负责自己的结构校验
  async requestText(url, options = {}) {
    const { method = 'GET', body = '', label = '接口', allowHtml = false } = options
    let response
    try {
      response = method === 'POST' ? await authPost(url, body) : await authGet(url)
    } catch (error) {
      const message = String(error?.message || '')
      // 本机会话解密暂时失败（Keystore 未就绪）不是会话失效，按临时故障处理，
      // 由调用方回退快照保持登录态，绝不在这里清空本地 Cookie
      if (message.includes('SESSION_STORE_UNAVAILABLE')) {
        const failure = new Error('会话读取暂时失败')
        failure.network = true
        throw failure
      }
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
    if (response?.status !== 200) {
      const failure = new Error(`${label}接口返回 ${response?.status ?? '未知状态'}`)
      failure.status = response?.status
      // 只有 401 才是真的掉登录态；403 多为「该功能未开放 / WAF 拦截 / 权限不足」，
      // 误判成会话失效会清空本机全部数据（Cookie、快照、附件），代价远大于收益。
      failure.sessionExpired = response?.status === 401
      if (response?.status === 403) failure.forbidden = true
      throw failure
    }
    const raw = String(response.body || '').replace(/^\uFEFF/, '')
    if (/^\s*</.test(raw)) {
      // 只认教务登录页自身的明确特征，避免正常业务页出现「统一身份认证」
      // 或 authserver 链接时被误判为会话失效并清空本地会话
      const expired = /login_slogin|name="yhm"/i.test(raw)
      if (expired) {
        const failure = new Error('登录会话已失效')
        failure.sessionExpired = true
        throw failure
      }
      if (!allowHtml) {
        const failure = new Error(`${label}数据解析失败`)
        failure.parseError = true
        throw failure
      }
    }
    return raw
  }

  parseJson(raw, label) {
    try {
      return JSON.parse(raw)
    } catch (error) {
      const failure = new Error(`${label}数据解析失败`)
      failure.parseError = true
      throw failure
    }
  }

  async loginWithSso() {
    const result = await nativeSsoLogin()
    if (!result?.cookieHeader) throw new Error('未获取到登录会话')
    await setAuthCookies(result.cookieHeader)
    // 刚登录必须现场校验会话，不能回退到旧快照，否则会把登录失败伪装成成功
    return this.restoreSession({ allowSnapshot: false })
  }

  /**
   * 会话失效时用统一认证票据静默换票，成功返回新 profile。
   * 换票后必须现场校验一次，避免把无效 Cookie 当成已恢复；
   * 失败只返回原因，不在这里清理本机数据。
   */
  async reloginSilently(options = {}) {
    const result = await silentRelogin({ timeoutMs: options.timeoutMs })
    if (!result?.ok || !result.cookieHeader) {
      return { ok: false, reason: result?.reason || 'no-cookie' }
    }
    try {
      await setAuthCookies(result.cookieHeader)
      const profile = await this.restoreSession({ allowSnapshot: false })
      return { ok: true, reason: 'ok', profile }
    } catch (error) {
      return {
        ok: false,
        reason: error?.sessionExpired
          ? 'login-form'
          : error?.network
            ? 'network'
            : 'verify'
      }
    }
  }

  async restoreSession({ allowSnapshot = true } = {}) {
    const snapshot = readSnapshot()
    let params = termParamsFor()
    try {
      let payload = null
      for (const candidate of termCandidatesFor(params)) {
        payload = await this.fetchSchedule(candidate)
        if (payload?.xsxx) {
          params = candidate
          break
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
        this.lastRawPayload = ''
        return this.profile
      }
      throw error
    }
  }

  async fetchSchedule(params) {
    const url = `${SCHEDULE_ENDPOINT}&xnm=${encodeURIComponent(params.xnm)}&xqm=${encodeURIComponent(params.xqm)}`
    const body = await this.requestText(url, { label: '课表' })
    this.lastRawPayload = body
    return this.parseJson(body, '课表')
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
      const body = await this.requestText(GRADE_PAGE_ENDPOINT, {
        label: '成绩页',
        allowHtml: true
      })
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

  async getSchedule(term, options = {}) {
    if (!this.term) throw new Error('尚未登录')
    const force = Boolean(options.force)
    const target = term || this.term
    const sameTerm = `${target.xnm}_${target.xqm}` === `${this.term.xnm}_${this.term.xqm}`
    if (!sameTerm || force) {
      try {
        const payload = await this.fetchSchedule(target)
        // 接口异常返回空对象时不能静默变成「本周没课」，必须按解析失败处理
        if (!payload?.xsxx) {
          const failure = new Error('课表数据缺少必要字段')
          failure.parseError = true
          throw failure
        }
        const nextTerm = mapTerm(payload.xsxx, target)
        this.term = nextTerm
        this.schedule = mapSchedule(payload, nextTerm)
        this.fetchedAt = new Date().toISOString()
        this.offline = false
        writeSnapshot(this)
      } catch (error) {
        // 主动刷新失败但本地还有课表：保留旧数据并标注离线，不打断使用
        if (force && sameTerm && this.schedule && error?.network) {
          this.offline = true
        } else {
          throw error
        }
      }
    }
    return {
      ...this.schedule,
      offline: this.offline,
      source: this.offline ? '教务系统（离线缓存）' : '教务系统（实时）',
      fetchedAt: this.fetchedAt,
      rawPayload: this.offline ? '' : this.lastRawPayload,
      term: this.term
    }
  }

  async fetchGradesPage(term, page, pageSize = 100) {
    const form = `xnm=${encodeURIComponent(term.xnm)}&xqm=${encodeURIComponent(term.xqm)}`
      + `&queryModel.showCount=${pageSize}&queryModel.currentPage=${page}`
    const body = await this.requestText(GRADES_ENDPOINT, {
      method: 'POST',
      body: form,
      label: '成绩'
    })
    const payload = this.parseJson(body, '成绩')
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
    const pageSize = 100
    const first = await this.fetchGradesPage(params, 1)
    const items = [...first.items]
    const totalResult = Number(first.totalResult)
    const declaredPages = Number(first.totalPage)
    const hasDeclaredPages = Number.isFinite(declaredPages) && declaredPages > 0
    // totalPage 缺失或为 0 时按 totalResult 推算；两者都没有时，只有首页满页才继续翻，
    // 否则会静默只取首页 100 条（成绩页按学期查询通常远小于 100 条）。
    const derivedPages = Number.isFinite(totalResult) && totalResult > 0
      ? Math.ceil(totalResult / pageSize)
      : 0
    const maxPage = Math.min(
      hasDeclaredPages
        ? declaredPages
        : derivedPages || (first.items.length >= pageSize ? 20 : 1),
      20
    )
    for (let page = 2; page <= maxPage; page += 1) {
      const next = await this.fetchGradesPage(params, page)
      items.push(...next.items)
      if (next.items.length < pageSize) break
    }
    // 分页排序不稳时同一门成绩会跨页重复，按服务端主键去重，避免绩点/学分重复累加
    const seenKeys = new Set()
    const unique = items.filter((item) => {
      const key = String(
        item?.key || item?.row_id || `${item?.kch || ''}_${item?.kcmc || ''}_${item?.cj || ''}`
      )
      if (seenKeys.has(key)) return false
      seenKeys.add(key)
      return true
    })
    const mapped = mapGrades({ items: unique, totalResult: first.totalResult }, params)
    if (Number.isFinite(totalResult) && totalResult > unique.length) mapped.truncated = true
    return mapped
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

  async fetchEvaluationsPage(page) {
    const body = await this.requestText(EVALUATION_ENDPOINT, {
      method: 'POST',
      body: `page=${page}&rows=100&sidx=&sord=asc`,
      label: '评教'
    })
    const payload = this.parseJson(body, '评教')
    if (!payload || !Array.isArray(payload.items)) {
      const failure = new Error('评教数据解析失败')
      failure.parseError = true
      throw failure
    }
    return payload
  }

  // 评教接口默认分页；按 totalPage 拉全，避免只显示前 10 条
  async getEvaluations() {
    if (!this.term) throw new Error('尚未登录')
    const first = await this.fetchEvaluationsPage(1)
    const items = [...first.items]
    const total = Number(first.totalResult)
    const totalPage = Number(first.totalPage)
    const pages = Number.isFinite(totalPage) && totalPage > 1
      ? Math.min(totalPage, 20)
      : Number.isFinite(total) && total > items.length
        ? Math.min(Math.ceil(total / 100), 20)
        : 1
    for (let page = 2; page <= pages; page += 1) {
      const next = await this.fetchEvaluationsPage(page)
      items.push(...next.items)
      if (!next.items.length) break
    }
    return mapEvaluations({ ...first, items: dedupeEvaluations(items) })
  }

  async getCourseSelectionStatus() {
    if (!this.term) throw new Error('尚未登录')
    const body = await this.requestText(COURSE_SELECTION_PAGE, {
      label: '选课状态',
      allowHtml: true
    })
    return {
      ...parseCourseSelectionStatus(body),
      source: '教务系统（只读）',
      fetchedAt: new Date().toISOString()
    }
  }

  async requestPortal(url, parser, options = {}) {
    const { method = 'GET', body = '' } = options
    const raw = await this.requestText(url, {
      method,
      body,
      label: '门户',
      allowHtml: true
    })
    try {
      return parser(raw)
    } catch (error) {
      if (error?.parseError || error?.sessionExpired) throw error
      const failure = new Error('数据解析失败')
      failure.parseError = true
      throw failure
    }
  }

  async getPortalNotices() {
    return this.requestPortal(PORTAL_NOTICE_INDEX, parsePortalNoticeIndex)
  }

  async collectPortalPages(url, buildBody, parser) {
    const items = []
    const seen = new Set()
    let total = 0
    let totalKnown = false
    let lastRawCount = 0
    let complete = false
    for (let page = 1; page <= 20; page += 1) {
      const result = await this.requestPortal(url, parser, {
        method: 'POST',
        body: buildBody(page)
      })
      // total 只在首页取一次并冻结：后续页若缺 totalResult，parse 层会退化成
      // 「当前页条数」，每页覆写会让 items.length >= total 恒成立而提前 break，
      // 表现为「少抓 N 条却报告完整」。
      if (page === 1) {
        total = Number(result.total) || 0
        totalKnown = Boolean(result.totalKnown)
      }
      for (const item of result.items) {
        if (seen.has(item.id)) continue
        seen.add(item.id)
        items.push(item)
      }
      lastRawCount = result.items.length
      // 空页 / 短页说明没有下一页；只有 totalResult 可信时才用总数判断拉完
      if (lastRawCount === 0 || lastRawCount < 100) {
        complete = true
        break
      }
      if (totalKnown && items.length >= total) {
        complete = true
        break
      }
    }
    const truncated = !complete || (totalKnown && items.length < total)
    return { total: total || items.length, items, truncated }
  }

  async getPortalNoticeList() {
    const parser = (raw) => parsePortalNoticeList(this.parseJson(raw, '通知'))
    const result = await this.collectPortalPages(
      PORTAL_NOTICE_LIST,
      (page) => `queryModel.showCount=100&queryModel.currentPage=${page}`,
      parser
    )
    return {
      ...result,
      source: '教务系统（学生门户）',
      fetchedAt: new Date().toISOString()
    }
  }

  async getPortalNoticeDetail(id) {
    const url = `${PORTAL_NOTICE_DETAIL}?xwbh=${encodeURIComponent(id)}`
    return this.requestPortal(url, (raw) => parsePortalNoticeDetail(raw, url))
  }

  async getMessages(mode = 'unread') {
    const parser = (raw) => parseMessages(this.parseJson(raw, '消息'))
    const flag = mode === 'all' ? 0 : 1
    const result = await this.collectPortalPages(
      PORTAL_MESSAGES,
      (page) => `flag=${flag}&sfyy=1&queryModel.showCount=100&queryModel.currentPage=${page}`,
      parser
    )
    return {
      ...result,
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
