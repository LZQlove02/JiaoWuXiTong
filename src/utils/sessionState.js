// 会话是否已进入「只读」状态：登录票据确认失效后，界面保留缓存数据但不再发请求。
// 用回调而不是模块级布尔值：
//   1. 真实状态只有一个来源（auth store 的 sessionExpired），不会出现两份副本互相漂移；
//   2. 测试里每个用例都会重建 store，模块级变量会跨用例残留，回调每次都是新实例的状态。
// api 层通过 isSessionBlocked() 实时查询，避免 api ↔ store 的循环依赖。
let check = null

export function setSessionBlockCheck(fn) {
  check = typeof fn === 'function' ? fn : null
}

export function isSessionBlocked() {
  try {
    return Boolean(check && check())
  } catch {
    return false
  }
}
