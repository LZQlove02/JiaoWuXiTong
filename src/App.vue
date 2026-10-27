<template>
  <div class="app-shell">
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
import { useUiStore } from './stores/ui'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
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
