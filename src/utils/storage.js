// 本地缓存占用估算：localStorage 按 UTF-16 每字符 2 字节近似，原生目录由插件返回字节数
export function estimateLocalStorageBytes(storage = globalThis.localStorage) {
  if (!storage) return 0
  let total = 0
  try {
    for (let index = 0; index < storage.length; index += 1) {
      const key = storage.key(index) || ''
      const value = storage.getItem(key) || ''
      total += (key.length + value.length) * 2
    }
  } catch (error) {
    return total
  }
  return total
}

export function formatBytes(bytes) {
  const value = Number(bytes)
  if (!Number.isFinite(value) || value <= 0) return '0 B'
  if (value < 1024) return `${Math.round(value)} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}
