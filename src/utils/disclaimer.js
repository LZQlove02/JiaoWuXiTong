const STORAGE_KEY = 'lzpu.disclaimer.accepted'

// 声明文案修订时同步 +1，老用户下次启动会重新看到声明
// v2（2026-10-30）：允许校内同学免费自行安装，明确不做商业用途与禁止二次打包
export const DISCLAIMER_VERSION = '2'
export const DISCLAIMER_DATE = '2026-10-30'

export const DISCLAIMER_CLAUSES = [
  {
    title: '非官方应用',
    text: '本应用为个人开发的第三方客户端，与学校及教务系统运营方无隶属关系，也未获官方授权；仅限校内同学免费自行安装使用，不做商业用途，禁止二次打包与改名分发。'
  },
  {
    title: '数据来源',
    text: '课表、成绩等数据来自学校教务系统，仅限本人账号只读使用；不采集、不上传他人数据。学校公开发布的通知与公示，应用仅展示并保留来源。'
  },
  {
    title: '账号安全',
    text: '登录在学校统一认证页面完成，本应用不保存账号密码；本机仅保存加密会话与必要的教务缓存，可在退出登录或清除本机个人数据时删除。'
  },
  {
    title: '使用风险',
    text: '接口可能随时变更或失效，数据仅供参考，以学校教务系统为准；使用本应用产生的后果由使用者自行承担。'
  },
  {
    title: '合规使用',
    text: '请遵守学校网络与信息管理规定，不得用于爬取、传播他人数据或商业用途。'
  },
  {
    title: '声明更新',
    text: '本声明修订后将于下次启动重新提示。'
  }
]

export function acceptedDisclaimerVersion() {
  try {
    return localStorage.getItem(STORAGE_KEY) || ''
  } catch (error) {
    // 存储不可用时按未同意处理
    return ''
  }
}

export function isDisclaimerAccepted() {
  return acceptedDisclaimerVersion() === DISCLAIMER_VERSION
}

export function acceptDisclaimer() {
  try {
    localStorage.setItem(STORAGE_KEY, DISCLAIMER_VERSION)
    return true
  } catch (error) {
    // 写不进去也放行本次会话，下次启动会重新提示
    return false
  }
}