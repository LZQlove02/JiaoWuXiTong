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
    </section>

    <transition name="fade">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Capacitor } from '@capacitor/core'
import { App as CapApp } from '@capacitor/app'
import { useUiStore } from '../stores/ui'
import AppIcon from '../components/AppIcon.vue'
import BackBar from '../components/BackBar.vue'

const ui = useUiStore()

const themeOptions = [
  { value: 'system', label: '跟随系统' },
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' }
]

const toast = ref('')
const version = ref('')

function showToast(message) {
  toast.value = message
  setTimeout(() => {
    toast.value = ''
  }, 1600)
}

// 清理范围与「我的」页时代一致：只清公开内容的本地缓存，不动登录态与主题
function clearCache() {
  Object.keys(localStorage)
    .filter((key) => key.startsWith('lzpu.cache') || key.startsWith('lzpu.portal.v1.'))
    .forEach((key) => localStorage.removeItem(key))
  showToast('已清除本地缓存')
}

function showAbout() {
  showToast(version.value ? `柳职教务 v${version.value}` : '柳职教务')
}

onMounted(async () => {
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