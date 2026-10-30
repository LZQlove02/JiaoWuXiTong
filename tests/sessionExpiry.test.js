import { describe, expect, it, vi } from 'vitest'
import { notifySessionExpired, setSessionExpiredHandler } from '../src/utils/sessionExpiry'

describe('会话失效通知', () => {
  it('未注册处理器时不报错', () => {
    setSessionExpiredHandler(null)
    expect(() => notifySessionExpired()).not.toThrow()
  })

  it('注册后只调用一次处理器，处理器抛错不向上传播', () => {
    const handler = vi.fn(() => {
      throw new Error('boom')
    })
    setSessionExpiredHandler(handler)
    expect(() => notifySessionExpired()).not.toThrow()
    expect(handler).toHaveBeenCalledTimes(1)
    setSessionExpiredHandler(null)
  })
})
