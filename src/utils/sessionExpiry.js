// API 层与登录态解耦：任意业务请求判定会话失效后，通过这里统一通知 auth store。
let handler = null

export function setSessionExpiredHandler(fn) {
  handler = typeof fn === 'function' ? fn : null
}

export function notifySessionExpired() {
  if (!handler) return
  try {
    handler()
  } catch (error) {
    // 失效处理失败不能把原始业务错误吞掉
  }
}
