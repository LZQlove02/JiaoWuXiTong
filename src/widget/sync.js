// 把 store 变化防抖后推给原生桌面组件。
// 写入频率由 DEBOUNCE_MS 控制，组件只读快照，不做后台刷新。
import { buildWidgetPayload } from './payload'
import { clearWidget, setWidgetTheme, updateWidget, widgetAvailable } from '../native/widgetBridge'

const DEBOUNCE_MS = 300

let timer = null
let lastPayload = ''
let lastTheme = null

function debounce(run) {
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => {
    timer = null
    run()
  }, DEBOUNCE_MS)
}

/**
 * @param {Function|object} getContext 返回 store 快照的函数（推荐），或快照本身
 */
export function syncWidget(getContext) {
  if (!widgetAvailable()) return
  debounce(() => {
    const context = typeof getContext === 'function' ? getContext() : getContext
    const payload = buildWidgetPayload(context || {})
    const json = JSON.stringify(payload)
    // 同样的数据不重复写盘，避免无意义的组件重绘
    if (json === lastPayload) return
    lastPayload = json
    updateWidget(payload)
  })
}

export function syncWidgetTheme(theme) {
  if (!widgetAvailable() || !theme || theme === lastTheme) return
  lastTheme = theme
  setWidgetTheme(theme)
}

// 退出登录：立刻清空组件内容，不等防抖
export function clearWidgetNow() {
  if (timer) clearTimeout(timer)
  timer = null
  lastPayload = ''
  if (!widgetAvailable()) return
  clearWidget()
}

// 仅供测试或调试使用
export function resetWidgetSync() {
  if (timer) clearTimeout(timer)
  timer = null
  lastPayload = ''
  lastTheme = null
}