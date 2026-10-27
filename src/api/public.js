import { httpGet } from './nativeHttp'

const BASE = 'https://www.lzpu.edu.cn'
const SECTIONS = {
  tzgg: '/jwysxglc/tzgg',
  bszn: '/jwysxglc/bszn'
}

const CACHE_PREFIX = 'lzpu.cache.public.v1.'
const CALENDAR_PAGE = `${BASE}/ggfw/xl`
export const LIST_TTL_MS = 6 * 60 * 60 * 1000
export const ARTICLE_TTL_MS = 7 * 24 * 60 * 60 * 1000
export const CALENDAR_TTL_MS = 7 * 24 * 60 * 60 * 1000

function readCache(key) {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    const value = JSON.parse(raw)
    return value && value.v === 1 ? value : null
  } catch (error) {
    return null
  }
}

function writeCache(key, payload) {
  try {
    localStorage.setItem(key, JSON.stringify({ v: 1, ...payload }))
  } catch (error) {
    // 存储空间不足时放弃缓存，不影响本次展示
  }
}

function listKey(section) {
  return `${CACHE_PREFIX}list.${section}`
}

function articleKey(section, id) {
  return `${CACHE_PREFIX}article.${section}.${id}`
}

function calendarKey() {
  return `${CACHE_PREFIX}calendar`
}

export function isCacheExpired(cachedAt, ttl) {
  if (!cachedAt) return true
  return Date.now() - cachedAt > ttl
}

function parseList(html) {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  const items = []
  doc.querySelectorAll('ul.infoList li').forEach((li) => {
    const link = li.querySelector('a')
    if (!link) return
    const href = link.getAttribute('href') || ''
    const id = href.split('/').pop()
    if (!id) return
    items.push({
      id,
      title: (link.textContent || '').trim(),
      date: (li.querySelector('.date')?.textContent || '').trim(),
      url: BASE + href
    })
  })
  return items
}

export function getCachedList(section) {
  const cached = readCache(listKey(section))
  if (!cached || !Array.isArray(cached.items) || !cached.items.length) return null
  return {
    items: cached.items,
    source: cached.source,
    fetchedAt: cached.fetchedAt,
    cachedAt: cached.cachedAt,
    expired: isCacheExpired(cached.cachedAt, LIST_TTL_MS)
  }
}

export function getCachedArticle(section, id) {
  const cached = readCache(articleKey(section, id))
  if (!cached || !cached.title) return null
  return {
    id: cached.id,
    section: cached.section,
    title: cached.title,
    paragraphs: cached.paragraphs || [],
    text: cached.text || '',
    source: cached.source,
    url: cached.url,
    fetchedAt: cached.fetchedAt,
    cachedAt: cached.cachedAt,
    expired: isCacheExpired(cached.cachedAt, ARTICLE_TTL_MS)
  }
}

export async function fetchArticleList(section) {
  if (!SECTIONS[section]) throw new Error('未知栏目')

  const response = await httpGet(`${BASE}${SECTIONS[section]}`)
  if (response.status !== 200) {
    throw new Error(`教务处官网返回 ${response.status}`)
  }

  const items = parseList(response.body)
  if (!items.length) {
    throw new Error('页面结构变化，未能解析出公告列表')
  }

  const fetchedAt = new Date().toISOString()
  const cachedAt = Date.now()
  writeCache(listKey(section), {
    items,
    source: '教务处官网',
    fetchedAt,
    cachedAt
  })

  return { items, source: '教务处官网', fetchedAt, cachedAt }
}

export async function fetchArticle(section, id) {
  if (!SECTIONS[section]) throw new Error('未知栏目')

  const url = `${BASE}${SECTIONS[section]}/${id}`
  const response = await httpGet(url)
  if (response.status !== 200) {
    throw new Error(`教务处官网返回 ${response.status}`)
  }

  const doc = new DOMParser().parseFromString(response.body, 'text/html')
  const container =
    doc.querySelector('.articleCon') || doc.querySelector('.content.main_content')
  if (!container) {
    throw new Error('页面结构变化，未能解析正文')
  }

  const heading = container.querySelector('h1, .articleTitle, .title')
  const paragraphs = Array.from(container.querySelectorAll('p'))
    .map((item) => item.textContent.trim())
    .filter(Boolean)

  const fetchedAt = new Date().toISOString()
  const cachedAt = Date.now()
  const data = {
    id,
    section,
    title:
      (heading?.textContent || doc.querySelector('h1')?.textContent || '').trim() ||
      '详情',
    paragraphs,
    text: paragraphs.length
      ? ''
      : (container.textContent || '').replace(/\s+/g, ' ').trim(),
    source: '教务处官网',
    url,
    fetchedAt,
    cachedAt
  }

  writeCache(articleKey(section, id), data)
  return data
}

export function getCachedCalendar() {
  const cached = readCache(calendarKey())
  if (!cached || !cached.imageUrl) return null
  return {
    title: cached.title || '校历',
    imageUrl: cached.imageUrl,
    pageUrl: cached.pageUrl,
    fetchedAt: cached.fetchedAt,
    cachedAt: cached.cachedAt,
    expired: isCacheExpired(cached.cachedAt, CALENDAR_TTL_MS)
  }
}

export async function fetchCalendar() {
  const response = await httpGet(CALENDAR_PAGE)
  if (response.status !== 200) {
    throw new Error(`学校官网返回 ${response.status}`)
  }

  const doc = new DOMParser().parseFromString(response.body, 'text/html')
  const image =
    doc.querySelector('.articleCon .conTxt img') ||
    doc.querySelector('.articleCon img')
  if (!image) {
    throw new Error('页面结构变化，未能解析校历图片')
  }

  const src = image.getAttribute('src') || ''
  const imageUrl = src.startsWith('http') ? src : BASE + src
  const fetchedAt = new Date().toISOString()
  const cachedAt = Date.now()
  const data = {
    title: '校历',
    imageUrl,
    pageUrl: CALENDAR_PAGE,
    fetchedAt,
    cachedAt
  }

  writeCache(calendarKey(), data)
  return data
}

export function formatFetchedAt(value) {
  if (!value) return ''
  const date = new Date(typeof value === 'number' ? value : Date.parse(value))
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getMonth() + 1} 月 ${date.getDate()} 日 ${String(
    date.getHours()
  ).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
