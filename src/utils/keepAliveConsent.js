const STORAGE_KEY = 'jiaowu.keepalive.consent'
const DECLINED_KEY = 'jiaowu.keepalive.declined'

// 保活范围或频率变化时同步 +1，重新向用户说明
// v2：会话失效时会用本地加密保存的统一认证票据向 authserver 换票续期，需重新征得同意
export const KEEPALIVE_CONSENT_VERSION = '2'

export const KEEPALIVE_CLAUSES = [
  {
    title: '检查内容',
    text: '开启后，应用约每 30 分钟在后台访问一次学校教务系统的轻量接口，仅携带本机加密保存的登录会话，用于维持登录状态；不读取、不解析响应内容。'
  },
  {
    title: '执行频率与限制',
    text: '系统省电策略可能造成延迟或跳过本次保活；学校服务器会话仍可能到期。判定会话失效时，应用会用本机加密保存的统一认证票据向学校统一认证服务器换一次新会话，成功时不必重新登录，失败时才需要重新登录并完成短信验证。'
  },
  {
    title: '数据与隐私',
    text: '保活请求主要发往 zfjw.lzzy.net；失效后的换票请求会携带加密保存的统一认证票据发往 authserver.lzzy.net，不携带学号、姓名或账号密码。票据副本只保存在本机、使用 Android Keystore 加密，换票成功时按学校返回更新，退出登录、清除本机数据或撤回同意时删除。响应内容不写入缓存，也不上传到其他服务器。'
  },
  {
    title: '关闭方式',
    text: '本功能默认开启，可随时在设置页关闭「保持登录」（关闭即删除票据副本）；退出登录、清除本机个人数据或撤回隐私同意后会自动停止后台保活并删除票据副本。'
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
    localStorage.removeItem(DECLINED_KEY)
    return true
  } catch (error) {
    return false
  }
}

// 用户明确关闭时记录，避免每次启动重复弹说明；同意后清除
export function isKeepAliveConsentDeclined() {
  try {
    return localStorage.getItem(DECLINED_KEY) === KEEPALIVE_CONSENT_VERSION
  } catch (error) {
    return false
  }
}

export function declineKeepAliveConsent() {
  try {
    localStorage.setItem(DECLINED_KEY, KEEPALIVE_CONSENT_VERSION)
    return true
  } catch (error) {
    return false
  }
}

export function revokeKeepAliveConsent() {
  try {
    localStorage.removeItem(STORAGE_KEY)
    localStorage.removeItem(DECLINED_KEY)
  } catch (error) {
    // 存储不可用时无需处理
  }
}
