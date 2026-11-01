import { describe, expect, it } from 'vitest'
import { deepLinkPath } from '../src/utils/deepLink'

describe('deepLinkPath', () => {
  it('把 jiaowu:// 深链解析成应用内路径', () => {
    expect(deepLinkPath('jiaowu://schedule')).toBe('/schedule')
    expect(deepLinkPath('jiaowu://messages')).toBe('/messages')
    expect(deepLinkPath('jiaowu://grades')).toBe('/grades')
    expect(deepLinkPath('jiaowu://notices')).toBe('/notices')
    expect(deepLinkPath('jiaowu://changes')).toBe('/changes')
    expect(deepLinkPath('jiaowu://login')).toBe('/login')
  })

  it('容忍查询串、锚点与缺斜杠写法', () => {
    expect(deepLinkPath('jiaowu://grades?from=widget')).toBe('/grades')
    expect(deepLinkPath('jiaowu://notices#top')).toBe('/notices')
    expect(deepLinkPath('jiaowu:schedule')).toBe('/schedule')
  })

  it('大小写与首尾空白不敏感', () => {
    expect(deepLinkPath('JIAOWU://SCHEDULE')).toBe('/schedule')
    expect(deepLinkPath('  jiaowu://messages  ')).toBe('/messages')
  })

  it('解析通知、课程与消息详情深链', () => {
    expect(deepLinkPath('jiaowu://notice/N1')).toBe('/notices/N1')
    expect(deepLinkPath('jiaowu://course/C1?week=3')).toBe('/course/C1')
    expect(deepLinkPath('jiaowu://message/M1')).toBe('/messages?id=M1')
    expect(deepLinkPath('jiaowu:notice/N1')).toBe('/notices/N1')
  })

  it('详情 ID 非法时返回空串', () => {
    expect(deepLinkPath('jiaowu://notice/')).toBe('')
    expect(deepLinkPath('jiaowu://notice/..%2F')).toBe('')
    expect(deepLinkPath('jiaowu://notice/%3Cscript%3E')).toBe('')
    expect(deepLinkPath(`jiaowu://notice/${'a'.repeat(65)}`)).toBe('')
  })

  it('未知 host、空值与其它 scheme 一律返回空串', () => {
    expect(deepLinkPath('jiaowu://unknown')).toBe('')
    expect(deepLinkPath('jiaowu://')).toBe('')
    expect(deepLinkPath('jiaowu:')).toBe('')
    expect(deepLinkPath('')).toBe('')
    expect(deepLinkPath(null)).toBe('')
    expect(deepLinkPath(undefined)).toBe('')
    expect(deepLinkPath('https://example.com/schedule')).toBe('')
    expect(deepLinkPath('/schedule')).toBe('')
  })
})