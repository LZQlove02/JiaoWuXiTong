const STORAGE_KEY = 'jiaowu.background.consent'

// 后台检查范围变化时同步 +1，重新向用户说明
export const BACKGROUND_CONSENT_VERSION = '1'

export const BACKGROUND_CLAUSES = [
  {
    title: '检查内容',
    text: '开启后，应用会在后台访问学校教务系统，检查本周课表是否出现停课、调课、新增或换教室，并检查本学期是否出现新的不合格成绩；如另行开启通知/消息提醒，还会检查新通知与新消息。'
  },
  {
    title: '检查时间',
    text: '默认每天 07:10 与 12:50 各检查一次，可在设置页修改，两个时段间隔至少 1 小时。系统省电策略可能造成少量延迟；关闭全部提醒或退出登录后不再检查。'
  },
  {
    title: '数据存放',
    text: '检查结果只保存在本机应用私有目录，用于变更记录、桌面组件角标与通知，不上传任何服务器。锁屏通知只显示「教务有更新」「成绩有更新」等通用文案，不展示课程名或成绩。'
  },
  {
    title: '关闭方式',
    text: '可随时在设置页关闭对应开关；关闭课表变更、成绩、通知与消息提醒后，后台检查闹钟会一并取消。'
  }
]

export function isBackgroundConsentAccepted() {
  try {
    return localStorage.getItem(STORAGE_KEY) === BACKGROUND_CONSENT_VERSION
  } catch (error) {
    return false
  }
}

export function acceptBackgroundConsent() {
  try {
    localStorage.setItem(STORAGE_KEY, BACKGROUND_CONSENT_VERSION)
    return true
  } catch (error) {
    return false
  }
}

export function revokeBackgroundConsent() {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch (error) {
    // 存储不可用时无需处理
  }
}
