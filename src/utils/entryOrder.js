export function mergeEntryOrder(entries, saved) {
  const remaining = new Map(entries.map((entry) => [entry.path, entry]))
  const result = []
  for (const path of Array.isArray(saved) ? saved : []) {
    const entry = remaining.get(path)
    if (entry) {
      result.push(entry)
      remaining.delete(path)
    }
  }
  for (const entry of entries) {
    if (remaining.has(entry.path)) result.push(entry)
  }
  return result
}

export function movePath(order, path, targetIndex) {
  const from = order.indexOf(path)
  if (from < 0) return order.slice()
  const list = order.filter((item) => item !== path)
  const index = Math.max(0, Math.min(targetIndex, list.length))
  list.splice(index, 0, path)
  return list
}