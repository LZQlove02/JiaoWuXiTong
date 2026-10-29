import { Capacitor, registerPlugin } from '@capacitor/core'

// 原生侧实现见 android/app/src/main/java/com/lzpu/jwxt/WidgetBridgePlugin.java
export const WidgetBridge = registerPlugin('WidgetBridge')

// 只有 Android 原生层有这个插件；Web / 其它平台一律 no-op
export function widgetAvailable() {
  return Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android'
}

async function call(method, options) {
  if (!widgetAvailable()) return false
  try {
    await WidgetBridge[method](options)
    return true
  } catch (error) {
    // 组件写入失败不能影响 App 主流程，只记录
    console.warn(`[widget] WidgetBridge.${method} 调用失败`, error)
    return false
  }
}

export function updateWidget(payload) {
  return call('update', { payload: JSON.stringify(payload) })
}

export function clearWidget() {
  return call('clear')
}

export function setWidgetTheme(theme) {
  return call('setTheme', { theme })
}

// 组件背景样式：glass / transparent / opaque
export function setWidgetStyle(style) {
  return call('setStyle', { style })
}