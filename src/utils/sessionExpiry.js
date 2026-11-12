// API 层与登录态解耦：任意业务请求判定会话失效后，通过这里统一通知 auth store。
let handler = null

export function setSessionExpiredHandler(fn) {
  handler = typeof fn === 'function' ? fn : null
}

// 返回值表示会话是否已恢复：true 时调用方可以重放刚才失败的请求
export function notifySessionExpired() {
  if (!handler) return Promise.resolve(false)
  try {
    // 处理器内部会先尝试静默换票，失败才清理本机数据；
    // 任何异常都统一收敛为 false，不能影响调用方的原始业务错误。
    return Promise.resolve(handler()).then((recovered) => Boolean(recovered)).catch(() => false)
  } catch (error) {
    return Promise.resolve(false)
  }
}
