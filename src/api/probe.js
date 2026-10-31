import { authGet, authPost } from './nativeHttp'

// 仅 debug 构建调用：从教务首页与「我的应用」菜单中找成绩、选课的真实入口。
// 只输出状态码、页面长度与结构，不输出任何 Cookie、学号或成绩内容。
const MENU_URLS = [
  'https://zfjw.lzzy.net/xtgl/index_initMenu.html',
  'https://zfjw.lzzy.net/xtgl/index_cxWdyy.html',
  'https://zfjw.lzzy.net/index_cxWdyy.html'
]

const INTERESTING_PATH = /\/(?:cjcx|xsxk)\/[A-Za-z0-9_.]+\.html(?:\?[^"'\s<>]*)?/gi

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
}
