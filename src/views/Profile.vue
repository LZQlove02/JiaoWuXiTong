<template>
  <div class="page">
    <h1 class="page-title">我的</h1>

    <section class="card user-card">
      <div class="avatar">{{ avatarText }}</div>
      <div class="user-main">
        <p class="user-name">{{ auth.user?.name || '未登录' }}</p>
        <p class="tiny">{{ auth.user?.id }} · {{ auth.user?.className }}</p>
        <p class="tiny">{{ auth.user?.college }}</p>
      </div>
    </section>

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
        <span class="tiny">v0.1.0 · M1</span>
      </button>
    </section>

    <button class="logout" @click="logout">
      <AppIcon name="logout" />
      退出登录
    </button>

    <p class="tiny footer">数据来源：柳州职业技术大学教务系统（M1 为模拟数据）</p>

    <transition name="fade">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import AppIcon from '../components/AppIcon.vue'

const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()

const themeOptions = [
  { value: 'system', label: '跟随系统' },
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' }
]

const avatarText = computed(() => (auth.user?.name || '我').slice(-1))
const toast = ref('')

function showToast(message) {
  toast.value = message
  setTimeout(() => {
    toast.value = ''
  }, 1600)
}

function clearCache() {
  Object.keys(localStorage)
    .filter((key) => key.startsWith('lzpu.cache'))
    .forEach((key) => localStorage.removeItem(key))
  showToast('已清除本地缓存')
}

function showAbout() {
  showToast('柳职教务 v0.1.0 · M1 演示版')
}

async function logout() {
  await auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.user-card {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 22px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-main {
  flex: 1;
}

.user-name {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 3px;
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

.logout {
  width: 100%;
  margin-top: 20px;
  height: 48px;
  border-radius: 12px;
  background: var(--card);
  color: var(--danger);
  font-size: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.footer {
  margin-top: 20px;
  text-align: center;
}

.toast {
  position: fixed;
  left: 50%;
  bottom: calc(var(--tab-height) + 40px);
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
