import { describe, expect, it } from 'vitest'
import { estimateLocalStorageBytes, formatBytes } from '../src/utils/storage'

function fakeStorage(entries) {
  return {
    get length() {
      return entries.length
    },
    key(index) {
      return entries[index]?.[0] ?? null
    },
    getItem(key) {
      return entries.find(([name]) => name === key)?.[1] ?? null
    }
  }
}

describe('storage', () => {
  it('按 UTF-16 估算 localStorage 体积', () => {
    const storage = fakeStorage([
      ['a', '12'],
      ['long-key', 'value']
    ])
    expect(estimateLocalStorageBytes(storage)).toBe((1 + 2) * 2 + (8 + 5) * 2)
  })

  it('格式化字节数', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(1023)).toBe('1023 B')
    expect(formatBytes(1536)).toBe('1.5 KB')
    expect(formatBytes(2.5 * 1024 * 1024)).toBe('2.5 MB')
  })
})
