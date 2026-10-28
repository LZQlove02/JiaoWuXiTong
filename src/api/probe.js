import { authGet, authPost } from './nativeHttp'

// 仅 debug 构建调用：从教务首页与「我的应用」菜单中找成绩、考试、选课的真实入口。
// 只输出状态码、页面长度与结构，不输出任何 Cookie、学号或成绩内容。
const MENU_URLS = [
  'https://zfjw.lzzy.net/xtgl/index_initMenu.html',
  'https://zfjw.lzzy.net/xtgl/index_cxWdyy.html',
  'https://zfjw.lzzy.net/index_cxWdyy.html'
]

const INTERESTING_PATH = /\/(?:cjcx|kwgl|xsxk)\/[A-Za-z0-9_.]+\.html(?:\?[^"'\s<>]*)?/gi

let probed = false

function collectLinks(body) {
  const normalized = String(body || '').replace(/\\\//g, '/')
  const links = new Set()
  for (const match of normalized.matchAll(INTERESTING_PATH)) {
    links.add(match[0])
  }
  return [...links]
}

// 只描述响应结构，不输出字段值，避免成绩或个人信息进入日志
function describeShape(value, depth = 0) {
  if (Array.isArray(value)) {
    if (!value.length) return '数组[0]'
    const first = value[0]
    if (depth < 1 && first && typeof first === 'object') {
      return `数组[${value.length}] 首项字段 ${Object.keys(first).join(',')}`
    }
    return `数组[${value.length}]`
  }
  if (value && typeof value === 'object') {
    const keys = Object.keys(value)
    const allNumeric = keys.length > 0 && keys.every((key) => /^\d+$/.test(key))
    if (allNumeric && depth < 1) {
      const detail = keys.slice(0, 8).map((key) => `${key}:${describeShape(value[key], depth + 1)}`)
      return `数字键对象[${keys.length}] ${detail.join(' ')}${keys.length > 8 ? ' ...' : ''}`
    }
    return `对象{${keys.join(',')}}`
  }
  return typeof value
}

function describeBody(body) {
  const text = String(body || '').trim()
  if (!text) return '空响应'
  if (text.startsWith('<')) {
    return `HTML ${text.length}B`
  }
  try {
    const data = JSON.parse(text)
    if (typeof data === 'string') {
      const inner = data.trim()
      if (inner.startsWith('{') || inner.startsWith('[')) {
        return `JSON 字符串内嵌 ${describeBody(inner)}`
      }
      return `JSON 字符串 长度 ${data.length}`
    }
    if (Array.isArray(data)) return describeShape(data)
    const keys = Object.keys(data)
    const arrays = keys
      .filter((key) => Array.isArray(data[key]))
      .map((key) => `${key}:${describeShape(data[key])}`)
    const numeric = /^\d+$/.test(keys[0] || '') ? ` ${describeShape(data)}` : ''
    return `JSON 字段 ${keys.join(',')}${arrays.length ? `（${arrays.join(' ')}）` : ''}${numeric}`
  } catch (error) {
    return `非 JSON 文本 ${text.length}B`
  }
}

// 只做只读查询，不触碰选课、退课、评教等写操作
async function probeDataEndpoint(url, body) {
  try {
    const response = await authPost(url, body)
    console.log(`[probe] POST ${response?.status} ${url} -> ${describeBody(response?.body)}`)
  } catch (error) {
    console.log(`[probe] POST 失败 ${url} ${error?.message || error}`)
  }
}

function collectExamHints(body) {
  const normalized = String(body || '').replace(/\\\//g, '/')
  const hints = new Set()
  for (const match of normalized.matchAll(/<a[^>]+href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi)) {
    const text = match[2].replace(/<[^>]+>/g, '').trim()
    if (text.includes('考试')) hints.add(`${text} -> ${match[1]}`)
  }
  for (const match of normalized.matchAll(/[^"'\s<>]{0,80}考试[^"'\s<>]{0,80}/g)) {
    if (/\.html|gnmkdm/.test(match[0])) hints.add(match[0])
  }
  return [...hints]
}

function collectModuleLabels(body) {
  const normalized = String(body || '').replace(/\\\//g, '/')
  const result = new Set()
  for (const match of normalized.matchAll(/["'](N\d+)["']\s*:\s*["']([^"']+)["']/g)) {
    result.add(`${match[1]}:${match[2]}`)
  }
  for (const match of normalized.matchAll(/gnmkdm=["']?(N\d+)/gi)) {
    const before = normalized.slice(Math.max(0, match.index - 240), match.index)
    const labels = before.match(/[\u4e00-\u9fa5]{2,12}/g)
    result.add(`${match[1]}:${labels ? labels[labels.length - 1] : ''}`)
  }
  return [...result]
}

function describeExamPage(body) {
  const text = String(body || '')
  const title = text.match(/<title[^>]*>([^<]*)<\/title>/i)
  const flags = ['考试', '未开放', '无权限', '登录', '暂无', '错误'].filter((key) => text.includes(key))
  return `标题 ${title ? title[1].trim() : '无'}${flags.length ? ` 关键词 ${flags.join(',')}` : ''}`
}

async function probeExamPageFields() {
  try {
    const response = await authGet('https://zfjw.lzzy.net/kwgl/kscx_cxXsksxxIndex.html?gnmkdm=N2155')
    const body = String(response?.body || '')
    const names = new Set()
    for (const match of body.matchAll(/name=["']([^"']+)["']/gi)) {
      names.add(match[1])
    }
    console.log(`[probe] 考试页 ${response?.status} ${body.length}B 表单字段 ${[...names].join(',')} ${describeExamPage(body)}`)
  } catch (error) {
    console.log(`[probe] 考试页失败 ${error?.message || error}`)
  }
}

export async function probeZfjwEndpoints(term) {
  if (probed) return
  probed = true
  for (const url of MENU_URLS) {
    try {
      const response = await authGet(url)
      const body = String(response?.body || '')
      console.log(`[probe] ${response?.status} ${url} ${body.length}B`)
      const links = collectLinks(body)
      if (links.length) {
        console.log(`[probe] 候选入口\n${links.join('\n')}`)
      }
      const hints = collectExamHints(body)
      if (hints.length) {
        console.log(`[probe] 考试线索\n${hints.join('\n')}`)
      }
      const codes = collectModuleLabels(body)
      if (codes.length) {
        console.log(`[probe] 模块代码 ${codes.join(',')}`)
      }
    } catch (error) {
      console.log(`[probe] 失败 ${url} ${error?.message || error}`)
    }
  }

  if (!term?.xnm || !term?.xqm) return
  const form = `xnm=${encodeURIComponent(term.xnm)}&xqm=${encodeURIComponent(term.xqm)}`
  await probeDataEndpoint(
    'https://zfjw.lzzy.net/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005',
    form
  )
  await probeExamPageFields()
}
