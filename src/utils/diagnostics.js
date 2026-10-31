// 反馈邮件附带的诊断信息：只包含版本、机型、系统与界面状态，不含账号、学号、姓名或数据内容。

const THEME_LABELS = {
  system: '跟随系统',
  light: '浅色',
  dark: '深色'
}

function pad(value) {
  return String(value).padStart(2, '0')
}

export function formatStamp(moment = new Date()) {
  const date = moment instanceof Date ? moment : new Date(moment)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}`
}

export function buildDiagnostics({
  version = '',
  device = null,
  theme = 'system',
  platform = '',
  online = true,
  crash = '',
  now = new Date()
} = {}) {
  const model = [device?.manufacturer, device?.model].filter(Boolean).join(' ').trim()
  const android = device?.android ? `Android ${device.android}` : 'Android 未知'
  const sdk = Number.isFinite(Number(device?.sdk)) && Number(device.sdk) > 0
    ? `（API ${device.sdk}）`
    : ''
  const lines = [
    `应用版本：${version ? `v${version}` : '未知'}`,
    model ? `设备：${model}` : '设备：未知',
    `系统：${android}${sdk}`,
    `主题：${THEME_LABELS[theme] || THEME_LABELS.system}`,
    platform ? `平台：${platform}` : '',
    `网络：${online ? '在线' : '离线'}`,
    `生成时间：${formatStamp(now)}`
  ]
  const base = lines.filter(Boolean).join('\n')
  const crashText = String(crash || '').trim()
  return crashText ? `${base}\n\n【最近崩溃】\n${crashText}` : base
}
