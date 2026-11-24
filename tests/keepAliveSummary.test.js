import { describe, expect, it } from 'vitest'
import { describeKeepAlive, formatKeepAliveStamp } from '../src/utils/keepAliveSummary'

const NOW = new Date(2026, 10, 24, 12, 0)
const at = (day, hour, minute) => new Date(2026, 10, day, hour, minute).getTime()

describe('保活状态文案', () => {
  it('有会话时照常展示上次检查与下次排程', () => {
    expect(
      describeKeepAlive(
        { active: true, lastCheckAt: at(24, 10, 13), lastResult: 'ALIVE', nextCheckAt: at(24, 11, 7) },
        NOW
      )
    ).toBe('上次保活 10:13（会话有效） · 下次约 11:07')
  })

  it('开关开着但本地没有会话时提示已停止，不再承诺下次排程', () => {
    expect(
      describeKeepAlive(
        { active: false, lastCheckAt: at(24, 10, 13), lastResult: 'EXPIRED', nextCheckAt: 0 },
        NOW
      )
    ).toBe('上次保活 10:13（会话已失效） · 已停止，等待重新登录')
  })

  it('没有会话也没有历史时只说明已停止', () => {
    expect(describeKeepAlive({ active: false, lastCheckAt: 0, lastResult: null }, NOW)).toBe(
      '已停止，等待重新登录'
    )
  })

  it('旧版原生没有 active 字段时按有会话处理，不误报停止', () => {
    expect(describeKeepAlive({ lastCheckAt: at(24, 10, 13), lastResult: 'ALIVE' }, NOW)).toBe(
      '上次保活 10:13（会话有效）'
    )
  })

  it('从未执行过保活时说明尚未执行', () => {
    expect(describeKeepAlive({ active: true, lastCheckAt: 0, nextCheckAt: at(24, 11, 7) }, NOW)).toBe(
      '尚未执行过保活 · 下次约 11:07'
    )
  })

  it('跨天的时间补上月日', () => {
    expect(describeKeepAlive({ active: true, lastCheckAt: at(23, 19, 13), lastResult: 'ALIVE' }, NOW)).toBe(
      '上次保活 11月23日 19:13（会话有效）'
    )
  })

  it('没有状态时不显示任何文案', () => {
    expect(describeKeepAlive(null, NOW)).toBe('')
  })
})

describe('保活时间格式', () => {
  it('空值与非法值返回空串', () => {
    expect(formatKeepAliveStamp(0, NOW)).toBe('')
    expect(formatKeepAliveStamp('abc', NOW)).toBe('')
  })
})
