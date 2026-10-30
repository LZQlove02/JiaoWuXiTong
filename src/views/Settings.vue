<template>
  <div class="page page--plain">
    <BackBar title="设置" />

    <h2 class="section-title">外观</h2>
    <section class="card">
      <div class="theme-row">
        <button
          v-for="option in themeOptions"
          :key="option.value"
          class="theme-btn"
          :class="{ 'is-active': ui.theme === option.value }"
          @click="ui.setTheme(option.value)"
        >
          {{ option.label }}
        </button>
      </div>
    </section>

    <h2 class="section-title">课表变更提醒</h2>
    <section class="card settings">
      <div class="setting-row">
        <span class="setting-left"><AppIcon name="notice" />课表变更提醒</span>
        <button
          class="switch"
          :class="{ 'is-on': changes.enabled }"
          role="switch"
          :aria-checked="changes.enabled"
          aria-label="课表变更提醒"
          @click="toggleChanges"
        >
          <span class="switch-dot"></span>
        </button>
      </div>
      <p class="setting-note">
        每天约 07:10、12:50 自动检查（系统省电策略下可能略有延迟）；只提醒本周的停课、调课与新增。系统清理后台后，重新打开 App 会自动恢复检查。
      </p>
      <button class="setting-row" @click="openNotificationSettings">
        <span class="setting-left">通知权限</span>
        <span class="tiny">{{ changes.notificationsEnabled ? '已开启' : '系统已关闭' }}</span>
      </button>
      <router-link class="setting-row" to="/changes">
        <span class="setting-left">查看变更记录</span>
        <AppIcon name="chevron-right" />
      </router-link>
    </section>

    <h2 class="section-title">通用</h2>
    <section class="card settings">
      <button class="setting-row" @click="clearCache">
        <span class="setting-left"><AppIcon name="trash" />清除缓存</span>
        <AppIcon name="chevron-right" />
      </button>
      <button class="setting-row" @click="showAbout">
        <span class="setting-left"><AppIcon name="info" />关于</span>
        <span v-if="version" class="tiny">v{{ version }}</span>
      </button>
      <button class="setting-row" @click="openDisclaimer">
        <span class="setting-left"><AppIcon name="shield" />免责声明</span>
        <span class="tiny">v{{ disclaimerVersion }}</span>
      </button>
    </section>

    <transition name="fade">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>

    <DisclaimerGate v-if="disclaimerOpen" mode="reader" @close="closeDisclaimer" />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Capacitor } from '@capacitor/core'
import { App as CapApp } from '@capacitor/app'
import { useUiStore } from '../stores/ui'
import { useChangesStore } from '../stores/changes'
import { DISCLAIMER_VERSION } from '../utils/disclaimer'
import AppIcon from '../components/AppIcon.vue'
import BackBar from '../components/BackBar.vue'
import DisclaimerGate from '../components/DisclaimerGate.vue'

const ui = useUiStore()
const changes = useChangesStore()

const themeOptions = [
  { value: 'system', label: '跟随系统' },
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' }
]

const toast = ref('')
const version = ref('')
const disclaimerOpen = ref(false)
const disclaimerVersion = DISCLAIMER_VERSION

function showToast(message) {
  toast.value = message
  setTimeout(() => {
    toast.value = ''
  }, 1600)
}

// 只读重读声明，不改变同意状态；返回键交给 ui store 关闭浮层
function openDisclaimer() {
  disclaimerOpen.value = true
  ui.setOverlayCloser(closeDisclaimer)
}

function closeDisclaimer() {
  disclaimerOpen.value = false
  ui.clearOverlayCloser()
}

onBeforeUnmount(() => {
  if (disclaimerOpen.value) ui.clearOverlayCloser()
})

// 清理范围：公开内容缓存 + 个人数据缓存，不动登录态与主题
function clearCache() {
  Object.keys(localStorage)
    .filter(
      (key) =>
        key.startsWith('lzpu.cache') ||
        key.startsWith('lzpu.portal.v1.') ||
        key.startsWith('lzpu.data.v1.')
    )
    .forEach((key) => localStorage.removeItem(key))
  showToast('已清除本地缓存')
}

function showAbout() {
  showToast(version.value ? `柳职教务 v${version.value}` : '柳职教务')
}

async function toggleChanges() {
  const next = !changes.enabled
  if (next) {
    const granted = await changes.requestNotificationPermission()
    await changes.setEnabled(true)
    showToast(granted ? '已开启课表变更提醒' : '已开启（系统通知未授权）')
    return
  }
  await changes.setEnabled(false)
  showToast('已关闭课表变更提醒')
}

function openNotificationSettings() {
  changes.openNotificationSettings()
}

onMounted(async () => {
  changes.init()
  if (!Capacitor.isNativePlatform()) return
  try {
    const info = await CapApp.getInfo()
    version.value = info.version || ''
  } catch (error) {
    // 读取不到版本信息时不显示版本号
  }
})
</script>

<style scoped>
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-secondary);
  margin: 20px 0 10px;
}

.theme-row {
  display: flex;
  gap: 8px;
}

.theme-btn {
  flex: 1;
  padding: 10px 0;
  border-radius: 10px;
  background: var(--card-muted);
  color: var(--text-secondary);
  font-size: 14px;
}

.theme-btn.is-active {
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 600;
}

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
  padding: 0 0 12px;
  border-bottom: 1px solid var(--border);
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-tertiary);
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