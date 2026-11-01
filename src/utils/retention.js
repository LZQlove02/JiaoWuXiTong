// 本地数据保留期限：个人缓存最长 30 天，公开内容缓存最长 7 天。
// 启动时清理超期条目，避免退出登录前长期堆积历史数据。
export const PERSONAL_RETENTION_MS = 30 * 24 * 60 * 60 * 1000
export const PUBLIC_RETENTION_MS = 7 * 24 * 60 * 60 * 1000

const PERSONAL_PREFIXES = ['lzpu.data.v1.', 'lzpu.portal.v1.']
const PUBLIC_PREFIX = 'lzpu.cache.public.v1.'
const SNAPSHOT_KEY = 'lzpu.zf.snapshot.v1'

function isExpired(value, limit, now) {
  const time = typeof value === 'number' ? value : Date.parse(value)
  if (!Number.isFinite(time)) return true
  return now - time > limit
}

export function pruneLocalData(storage = globalThis.localStorage, now = Date.now()) {
  if (!storage) return 0
  let removed = 0
  try {
    const keys = Object.keys(storage)
    for (const key of keys) {
      if (key === SNAPSHOT_KEY) {
        try {
          const snapshot = JSON.parse(storage.getItem(key) || '')
          if (isExpired(snapshot?.fetchedAt, PERSONAL_RETENTION_MS, now)) {
            storage.removeItem(key)
            removed += 1
          }
        } catch (error) {
          storage.removeItem(key)
          removed += 1
        }
        continue
      }
      if (PERSONAL_PREFIXES.some((prefix) => key.startsWith(prefix))) {
        try {
          const entry = JSON.parse(storage.getItem(key) || '')
          if (isExpired(entry?.cachedAt, PERSONAL_RETENTION_MS, now)) {
            storage.removeItem(key)
            removed += 1
          }
        } catch (error) {
          storage.removeItem(key)
          removed += 1
        }
        continue
      }
      if (key.startsWith(PUBLIC_PREFIX)) {
        try {
          const entry = JSON.parse(storage.getItem(key) || '')
          if (isExpired(entry?.cachedAt, PUBLIC_RETENTION_MS, now)) {
            storage.removeItem(key)
            removed += 1
          }
        } catch (error) {
          storage.removeItem(key)
          removed += 1
        }
      }
    }
  } catch (error) {
    // 存储不可用时跳过清理
  }
  return removed
}
