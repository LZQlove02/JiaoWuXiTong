const PREFIX = 'jiaowu.portal.v1.'

export const PORTAL_NOTICE_TTL = 6 * 60 * 60 * 1000
export const PORTAL_NOTICE_DETAIL_TTL = 7 * 24 * 60 * 60 * 1000

export function readPortalCache(key, ttlMs) {
  try {
    const raw = localStorage.getItem(PREFIX + key)
    if (!raw) return null
    const entry = JSON.parse(raw)
    if (!entry || typeof entry !== 'object' || !entry.data || !entry.cachedAt) {
      return null
    }
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

export function writePortalCache(key, data) {
  try {
    localStorage.setItem(
      PREFIX + key,
      JSON.stringify({ data, cachedAt: new Date().toISOString() })
    )
  } catch (error) {
    // 本地存储不可用时只影响离线回退
  }
}