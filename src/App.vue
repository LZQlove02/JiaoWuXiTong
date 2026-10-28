<template>
  <div v-if="auth.restoring" class="boot">
    <div class="boot-logo">柳</div>
    <p class="boot-text">正在恢复登录状态…</p>
  </div>
  <div v-else class="app-shell">
    <router-view :key="$route.fullPath" />
    <TabBar v-if="showTabBar" />
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Capacitor } from '@capacitor/core'
import { App as CapApp } from '@capacitor/app'
import TabBar from './components/TabBar.vue'
import { useAuthStore } from './stores/auth'
import { useUiStore } from './stores/ui'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const auth = useAuthStore()
const showTabBar = computed(() => route.meta.tab === true)

onMounted(() => {
  ui.initTheme()
  if (!Capacitor.isNativePlatform()) return

  CapApp.addListener('backButton', () => {
    const current = router.currentRoute.value
    if (ui.hasOverlay) {
      ui.closeOverlay()
      return
    }
    if (current.meta.tab) {
      if (current.path === '/') CapApp.exitApp()
      else router.replace('/')
      return
    }
    if (current.name === 'login') {
      CapApp.exitApp()
      return
    }
    router.back()
  })
})
</script>

<style scoped>
.boot {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  background: var(--bg);
}

.boot-logo {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  background: var(--brand);
  color: #fff;
  font-size: 30px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.boot-text {
  font-size: 13px;
  color: var(--text-secondary);
}
</style>
