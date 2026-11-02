const STORAGE_KEY = 'jiaowu.keepalive.consent'

// 保活范围或频率变化时同步 +1，重新向用户说明
export const KEEPALIVE_CONSENT_VERSION = '1'

export const KEEPALIVE_CLAUSES = [
  {
    title: '检查内容',
    text: '开启后，应用约每 30 分钟在后台访问一次学校教务系统的轻量接口，仅携带本机加密保存的登录会话，用于维持登录状态；不读取、不解析响应内容。'
  },
  {
    title: '执行频率与限制',
    text: '系统省电策略可能造成延迟或跳过本次保活；学校服务器会话仍可能到期，届时需要重新登录并完成短信验证。保活只降低重新登录频率，不能绕过学校认证。'
  },
  {
    title: '数据与隐私',
    text: '保活请求只发往 zfjw.lzzy.net，不携带学号、姓名等额外信息；响应内容不写入缓存，也不上传到其他服务器。'
  },
  {
    title: '关闭方式',
    text: '可随时在设置页关闭「保持登录」；退出登录、清除本机个人数据或撤回隐私同意后会自动停止后台保活。'
  }
]

export function isKeepAliveConsentAccepted() {
  try {
    return localStorage.getItem(STORAGE_KEY) === KEEPALIVE_CONSENT_VERSION
  } catch (error) {
    return false
  }
}

export function acceptKeepAliveConsent() {
  try {
    localStorage.setItem(STORAGE_KEY, KEEPALIVE_CONSENT_VERSION)
    return true
  } catch (error) {
    return false
  }
}

export function revokeKeepAliveConsent() {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch (error) {
    // 存储不可用时无需处理
  }
}
