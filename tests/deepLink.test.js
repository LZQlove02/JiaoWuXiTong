import { describe, expect, it } from 'vitest'
import { deepLinkPath } from '../src/utils/deepLink'

describe('deepLinkPath', () => {
  it('把 lzpu:// 深链解析成应用内路径', () => {
    expect(deepLinkPath('lzpu://schedule')).toBe('/schedule')
    expect(deepLinkPath('lzpu://messages')).toBe('/messages')
    expect(deepLinkPath('lzpu://grades')).toBe('/grades')
    expect(deepLinkPath('lzpu://notices')).toBe('/notices')
    expect(deepLinkPath('lzpu://changes')).toBe('/changes')
    expect(deepLinkPath('lzpu://login')).toBe('/login')
  })

  it('容忍查询串、锚点与缺斜杠写法', () => {
    expect(deepLinkPath('lzpu://grades?from=widget')).toBe('/grades')
    expect(deepLinkPath('lzpu://notices#top')).toBe('/notices')
    expect(deepLinkPath('lzpu:schedule')).toBe('/schedule')
  })

  it('大小写与首尾空白不敏感', () => {
    expect(deepLinkPath('LZPU://SCHEDULE')).toBe('/schedule')
    expect(deepLinkPath('  lzpu://messages  ')).toBe('/messages')
  })

  it('解析通知、课程与消息详情深链', () => {
    expect(deepLinkPath('lzpu://notice/N1')).toBe('/notices/N1')
    expect(deepLinkPath('lzpu://course/C1?week=3')).toBe('/course/C1')
    expect(deepLinkPath('lzpu://message/M1')).toBe('/messages?id=M1')
    expect(deepLinkPath('lzpu:notice/N1')).toBe('/notices/N1')
  })

  it('详情 ID 非法时返回空串', () => {
    expect(deepLinkPath('lzpu://notice/')).toBe('')
    expect(deepLinkPath('lzpu://notice/..%2F')).toBe('')
    expect(deepLinkPath('lzpu://notice/%3Cscript%3E')).toBe('')
    expect(deepLinkPath(`lzpu://notice/${'a'.repeat(65)}`)).toBe('')
  })

  it('未知 host、空值与其它 scheme 一律返回空串', () => {
    expect(deepLinkPath('lzpu://unknown')).toBe('')
    expect(deepLinkPath('lzpu://')).toBe('')
    expect(deepLinkPath('lzpu:')).toBe('')
    expect(deepLinkPath('')).toBe('')
    expect(deepLinkPath(null)).toBe('')
    expect(deepLinkPath(undefined)).toBe('')
    expect(deepLinkPath('https://example.com/schedule')).toBe('')
    expect(deepLinkPath('/schedule')).toBe('')
  })
})