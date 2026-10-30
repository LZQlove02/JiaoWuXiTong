// 个人教务数据的本地缓存：断网时回退展示，并在页面上标注更新时间与过期状态。
// 退出登录或会话失效时由 auth store 统一清空，避免下一个账号看到上一个人的数据。
const PREFIX = 'lzpu.data.v1.'

export const GRADES_TTL = 6 * 60 * 60 * 1000
export const MESSAGES_TTL = 30 * 60 * 1000
export const EVALUATIONS_TTL = 6 * 60 * 60 * 1000
export const COURSE_SELECTION_TTL = 60 * 60 * 1000

function keyOf(key) {
  return `${PREFIX}${key}`
}

export function readDataCache(key, ttlMs) {
  try {
    const raw = localStorage.getItem(keyOf(key))
    if (!raw) return null
    const entry = JSON.parse(raw)
    if (!entry || typeof entry !== 'object' || !entry.data || !entry.cachedAt) return null
    const cachedTime = Date.parse(entry.cachedAt)
    if (!Number.isFinite(cachedTime)) return null
    return {
      data: entry.data,
      cachedAt: entry.cachedAt,
      expired: Date.now() - cachedTime > ttlMs
    }
  } catch (error) {
    return null
  }
}

export function writeDataCache(key, data) {
  const cachedAt = new Date().toISOString()
  try {
    localStorage.setItem(keyOf(key), JSON.stringify({ data, cachedAt }))
  } catch (error) {
    // 存储空间不足时放弃缓存，不影响本次展示
  }
  return cachedAt
}

export function clearDataCaches() {
  try {
    Object.keys(localStorage)
      .filter((key) => key.startsWith(PREFIX))
      .forEach((key) => localStorage.removeItem(key))
  } catch (error) {
    // 存储不可用时无需处理
  }
}
