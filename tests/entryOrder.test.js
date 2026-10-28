import { describe, expect, it } from 'vitest'
import { mergeEntryOrder, movePath } from '../src/utils/entryOrder'

const entries = [
  { path: '/a', label: 'A' },
  { path: '/b', label: 'B' },
  { path: '/c', label: 'C' }
]

describe('快捷入口排序', () => {
  it('按保存顺序合并，忽略未知项，未保存项追加到末尾', () => {
    const merged = mergeEntryOrder(entries, ['/c', '/unknown', '/a'])
    expect(merged.map((entry) => entry.path)).toEqual(['/c', '/a', '/b'])
  })

  it('把入口移动到目标索引', () => {
    expect(movePath(['/a', '/b', '/c'], '/a', 2)).toEqual(['/b', '/c', '/a'])
    expect(movePath(['/a', '/b', '/c'], '/c', 0)).toEqual(['/c', '/a', '/b'])
    expect(movePath(['/a', '/b', '/c'], '/b', 1)).toEqual(['/a', '/b', '/c'])
  })

  it('索引越界时收敛到端点，未知路径保持不变', () => {
    expect(movePath(['/a', '/b'], '/a', 99)).toEqual(['/b', '/a'])
    expect(movePath(['/a', '/b'], '/missing', 0)).toEqual(['/a', '/b'])
  })
})