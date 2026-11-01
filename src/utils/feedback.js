export const FEEDBACK_EMAIL = '3507924962@qq.com'

export const SUBJECT_LIMIT = 120
export const BODY_LIMIT = 6000

export function buildFeedbackSubject(version = '') {
  const base = '教务助手使用反馈'
  const subject = version ? `${base}（v${version}）` : base
  return subject.slice(0, SUBJECT_LIMIT)
}

/** 正文 = 用户描述 + 诊断信息 + 可选联系方式；超长截断，保证能拉起邮件 App。 */
export function buildFeedbackBody({ description = '', diagnostics = '', contact = '' } = {}) {
  const parts = [
    `【问题描述】\n${String(description).trim() || '（未填写）'}`
  ]
  if (diagnostics) parts.push(`【诊断信息】\n${diagnostics}`)
  const trimmedContact = String(contact).trim()
  if (trimmedContact) parts.push(`【联系方式】\n${trimmedContact}`)
  const text = parts.join('\n\n')
  if (text.length <= BODY_LIMIT) return text
  return `${text.slice(0, BODY_LIMIT - 16).trimEnd()}\n…（已截断）`
}

export function buildFeedbackMailto(subject, body) {
  return `mailto:${FEEDBACK_EMAIL}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(
    body
  )}`
}
