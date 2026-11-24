<template>
  <div class="page page--plain">
    <BackBar title="账号与设备" />

    <h2 class="section-title">登录与会话</h2>
    <section class="card settings">
      <div class="setting-row">
        <span class="setting-left"><AppIcon name="refresh" />保持登录</span>
        <button
          class="switch"
          :class="{ 'is-on': keepAliveEnabled }"
          role="switch"
          :aria-checked="keepAliveEnabled"
          aria-label="保持登录"
          @click="toggleKeepAlive"
        >
          <span class="switch-dot"></span>
        </button>
      </div>
      <div v-if="keepAliveEnabled && batteryExempted === false" class="setting-row">
        <span class="setting-left"><AppIcon name="refresh" />后台稳定运行</span>
        <button class="widget-style" type="button" @click="allowBackground">去允许</button>
      </div>
      <p v-if="keepAliveEnabled && batteryExempted === false" class="setting-note">
        系统当前会限制本应用的后台运行，深睡期间可能推迟会话保活。允许后台稳定运行后，保持登录更可靠。小米 / 红米 / vivo
        还需在系统设置中把本应用的省电策略设为「无限制」，并允许「自启动」与「后台高耗电」；若开启了夜间睡眠模式，夜间会断网，仍可能掉线。
      </p>
      <p class="setting-note">
        默认开启，可随时关闭；开启后由应用约每 30
        分钟在后台维持一次教务会话，减少长时间不打开后的重新登录与短信验证。系统省电策略可能造成延迟；学校侧对连续在线约 3
        小时的会话会强制下线，届时仍需要重新登录一次，判定失效后本应用会退避到 3 小时才再检查。
      </p>
      <p v-if="keepAliveEnabled && keepAliveSummary" class="setting-note setting-note--status">
        {{ keepAliveSummary }}
      </p>
    </section>

    <p class="footnote tiny">退出登录请在「我的」页底部操作。</p>

    <h2 class="section-title">桌面组件</h2>
    <section class="card settings">
      <template v-if="widgets.length">
        <div v-for="widget in widgets" :key="widget.id" class="widget-item">
          <div class="setting-row">
            <span class="setting-left"><AppIcon name="calendar" />{{ widget.label }}</span>
            <span class="widget-actions">
              <button class="widget-color" type="button" @click="toggleColorPanel(widget)">
                <span
                  class="color-dot"
                  :class="{ 'is-auto': !widget.textColor }"
                  :style="widget.textColor ? { background: widget.textColor } : null"
                ></span>
                {{ widget.textColor ? '文字色' : '自动' }}
              </button>
              <button class="widget-style" type="button" @click="cycleWidgetStyle(widget)">
                {{ styleLabel(widget.style) }}
              </button>
            </span>
          </div>
          <div v-if="colorWidgetId === widget.id" class="color-panel">
            <button
              v-for="option in colorOptions"
              :key="option.label"
              class="color-option"
              type="button"
              :class="{ 'is-active': widget.textColor === option.value }"
              :aria-pressed="widget.textColor === option.value"
              @click="pickColor(widget, option.value)"
            >
              <span
                class="color-dot"
                :class="{ 'is-auto': !option.value }"
                :style="option.value ? { background: option.value } : null"
              ></span>
              {{ option.label }}
            </button>
            <!-- 同值选择不会触发 change（Chromium 不会派发事件），这是平台限制，
                 成功时统一用 toast 给出反馈 -->
            <label class="color-option color-custom">
              <input
                type="color"
                :value="widget.textColor || '#FFFFFF'"
                @change="pickColor(widget, $event.target.value)"
              />
              自定义
            </label>
          </div>
        </div>
        <button class="setting-row" type="button" @click="onRefreshWidgets">
          <span class="setting-left">刷新全部组件</span>
          <AppIcon name="refresh" />
        </button>
        <p class="setting-note">
          样式与文字色只影响对应组件实例；「自动」跟随主题深浅色；4×4
          课表网格内的课程块按填充色自带字色，不跟随此处设置。添加或删除请长按桌面空白处操作。
        </p>
      </template>
      <p v-else class="setting-note">
        暂无桌面组件。长按桌面空白处添加「教务助手」组件后，可在这里统一调整样式和刷新。
      </p>
    </section>

    <transition name="fade">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>

    <ConsentGate
      v-if="keepAliveOpen"
      :clauses="KEEPALIVE_CLAUSES"
      title="开启前请阅读"
      subtitle="教务助手 · 保持登录说明"
      :version-text="`说明版本 v${KEEPALIVE_CONSENT_VERSION}`"
      primary-text="同意并开启"
      decline-text="取消"
      @accept="onKeepAliveAccept"
      @decline="onKeepAliveDecline"
    />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { App as CapApp } from '@capacitor/app'
import {
  getBatteryOptimizationState,
  getKeepAliveState,
  requestBatteryOptimizationExemption,
  setKeepAlive as setKeepAliveNative
} from '../api/nativeHttp'
import {
  listWidgets,
  refreshWidgets as refreshWidgetsNative,
  setWidgetStyle,
  setWidgetTextColor
} from '../native/widgetBridge'
import {
  KEEPALIVE_CLAUSES,
  KEEPALIVE_CONSENT_VERSION,
  acceptKeepAliveConsent,
  declineKeepAliveConsent,
  isKeepAliveConsentAccepted
} from '../utils/keepAliveConsent'
import { describeKeepAlive } from '../utils/keepAliveSummary'
import AppIcon from '../components/AppIcon.vue'
import BackBar from '../components/BackBar.vue'
import ConsentGate from '../components/ConsentGate.vue'

const toast = ref('')
const keepAliveOpen = ref(false)
const keepAliveEnabled = ref(false)
// 保持登录的最近一次执行情况：原生侧持久化，冷启动后仍能看到上次结果
const keepAliveState = ref(null)
// null 表示还没查到，避免未就绪时误显示「去允许」
const batteryExempted = ref(null)
const widgets = ref([])
// 当前展开调色板的组件实例；0 = 全部收起
const colorWidgetId = ref(0)
const colorOptions = [
  { label: '自动', value: '' },
  { label: '白色', value: '#FFFFFF' },
  { label: '黑色', value: '#000000' }
]
let widgetRetryTimer = null
let appStateListener = null
let disposed = false

const widgetStyleLabels = {
  glass: '玻璃',
  transparent: '透明',
  opaque: '不透明'
}

function styleLabel(style) {
  return widgetStyleLabels[style] || '玻璃'
}

function showToast(message) {
  toast.value = message
  setTimeout(() => {
    toast.value = ''
  }, 1600)
}

async function loadKeepAlive() {
  try {
    const state = await getKeepAliveState()
    keepAliveState.value = state || null
    keepAliveEnabled.value = Boolean(state?.enabled)
  } catch (error) {
    keepAliveState.value = null
    keepAliveEnabled.value = false
  }
  await loadBatteryState()
}

const keepAliveSummary = computed(() => describeKeepAlive(keepAliveState.value))

async function loadBatteryState() {
  try {
    const state = await getBatteryOptimizationState()
    batteryExempted.value = Boolean(state?.ignoring)
  } catch (error) {
    // 查询失败时不展示引导，避免误导
    batteryExempted.value = null
  }
}

async function allowBackground() {
  try {
    await requestBatteryOptimizationExemption()
    showToast('请在系统弹窗中允许后台运行')
  } catch (error) {
    showToast(error?.message || '无法打开系统设置')
  }
}

async function toggleKeepAlive() {
  if (keepAliveEnabled.value) {
    await applyKeepAlive(false)
    return
  }
  if (!isKeepAliveConsentAccepted()) {
    keepAliveOpen.value = true
    return
  }
  await applyKeepAlive(true)
}

async function applyKeepAlive(enabled) {
  try {
    const state = await setKeepAliveNative(enabled)
    keepAliveState.value = state || null
    keepAliveEnabled.value = Boolean(state?.enabled)
    // 手动开启视为同意说明，手动关闭记录为主动拒绝，默认开启策略不再自动恢复
    if (enabled) acceptKeepAliveConsent()
    else declineKeepAliveConsent()
    showToast(enabled ? '已开启保持登录' : '已关闭保持登录')
  } catch (error) {
    showToast(error?.message || '保持登录设置失败')
  }
}

async function onKeepAliveAccept() {
  acceptKeepAliveConsent()
  keepAliveOpen.value = false
  await applyKeepAlive(true)
}

function onKeepAliveDecline() {
  declineKeepAliveConsent()
  keepAliveOpen.value = false
}

async function loadWidgets() {
  widgets.value = await listWidgets()
}

async function cycleWidgetStyle(widget) {
  const order = ['glass', 'transparent', 'opaque']
  const current = order.indexOf(widget.style)
  const next = order[(current + 1) % order.length]
  try {
    await setWidgetStyle(widget.id, next)
    widget.style = next
  } catch (error) {
    showToast(error?.message || '组件样式修改失败')
  }
}

function toggleColorPanel(widget) {
  colorWidgetId.value = colorWidgetId.value === widget.id ? 0 : widget.id
}

async function pickColor(widget, color) {
  try {
    // 以原生归一化后的值为准：非法颜色会被降级成空串（自动）
    const result = await setWidgetTextColor(widget.id, color || '')
    widget.textColor = result?.color || ''
    showToast(widget.textColor ? `文字色已设为 ${widget.textColor}` : '文字色已恢复自动')
  } catch (error) {
    showToast(error?.message || '组件文字色修改失败')
  }
}

async function onRefreshWidgets() {
  try {
    await refreshWidgetsNative()
    await loadWidgets()
    showToast('已刷新桌面组件')
  } catch (error) {
    showToast(error?.message || '桌面组件刷新失败')
  }
}

onMounted(async () => {
  loadKeepAlive()
  loadWidgets()
  // 组件刚添加/系统绑定有延迟时，首屏可能少列；短延时再读一次
  widgetRetryTimer = setTimeout(loadWidgets, 800)
  try {
    const listener = await CapApp.addListener('appStateChange', ({ isActive }) => {
      if (isActive) {
        loadWidgets()
        // 从系统白名单弹窗返回后重新读取，及时隐藏引导
        loadBatteryState()
      }
    })
    // 页面在监听注册完成前已卸载时立即移除，避免泄漏
    if (disposed) {
      listener.remove()
    } else {
      appStateListener = listener
    }
  } catch (error) {
    // Web 预览或无原生监听时忽略
  }
})

onBeforeUnmount(() => {
  disposed = true
  clearTimeout(widgetRetryTimer)
  widgetRetryTimer = null
  if (appStateListener) {
    appStateListener.remove()
    appStateListener = null
  }
})
</script>

<style scoped>
.settings {
  padding: 4px 16px;
}

.setting-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border);
  color: var(--text-tertiary);
}

.setting-row:last-child {
  border-bottom: none;
}

.setting-left {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  color: var(--text);
}

.setting-note {
  padding: 12px 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-tertiary);
}

/* 上次/下次保活时间：比说明文字更「实时」，用次要前景色区分 */
.setting-note--status {
  padding-top: 0;
  color: var(--text-secondary);
}

.switch {
  position: relative;
  flex: none;
  width: 46px;
  height: 26px;
  border-radius: 13px;
  background: var(--card-muted);
  transition: background 0.15s;
}

.switch.is-on {
  background: var(--brand);
}

.switch-dot {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.18);
  transition: transform 0.15s;
}

.switch.is-on .switch-dot {
  transform: translateX(20px);
}

.widget-style {
  flex: none;
  padding: 6px 10px;
  border-radius: 8px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 12px;
}

/* 每个组件实例一块：分隔线挂在外层，调色板展开时不重复画线 */
.widget-item {
  border-bottom: 1px solid var(--border);
}

.widget-item:last-of-type {
  border-bottom: none;
}

.widget-item .setting-row {
  border-bottom: none;
}

.widget-actions {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
}

.widget-color {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 8px;
  background: var(--card-muted);
  color: var(--text-secondary);
  font-size: 12px;
}

.color-dot {
  flex: none;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 1px solid var(--border);
}

/* 「自动」用黑白对角表示跟随主题 */
.color-dot.is-auto {
  background: linear-gradient(135deg, var(--text) 50%, var(--card) 50%);
}

.color-panel {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 2px 0 14px;
}

/* 每行两项、44px 高：选项太挤时手指容易点空 */
.color-option {
  flex: 1 1 40%;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 8px;
  background: var(--card-muted);
  color: var(--text-tertiary);
  font-size: 13px;
}

.color-option.is-active {
  color: var(--brand);
  outline: 1.5px solid var(--brand);
}

.color-custom {
  position: relative;
  cursor: pointer;
}

.color-custom input {
  width: 18px;
  height: 18px;
  padding: 0;
  border: 1px solid var(--border);
  border-radius: 4px;
  background: none;
}

.footnote {
  margin-top: 14px;
  text-align: center;
}

.toast {
  position: fixed;
  left: 50%;
  bottom: calc(env(safe-area-inset-bottom) + 32px);
  transform: translateX(-50%);
  background: rgba(20, 20, 20, 0.86);
  color: #fff;
  font-size: 13px;
  padding: 9px 16px;
  border-radius: 20px;
  z-index: 50;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
