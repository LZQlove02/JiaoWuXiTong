<template>
  <header class="back-bar">
    <button class="back-btn" aria-label="返回" @click="goBack">
      <AppIcon name="arrow-left" />
    </button>
    <h1 class="bar-title">{{ title }}</h1>
  </header>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'

defineProps({
  title: { type: String, required: true }
})

const router = useRouter()

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/')
}
</script>

<style scoped>
.back-bar {
  display: flex;
  align-items: center;
  height: 52px;
  margin: 0 -16px 8px;
  padding: 0 8px;
}

.back-bar::after {
  content: '';
  width: 40px;
  flex: none;
}

.back-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text);
}

.bar-title {
  flex: 1;
  text-align: center;
  font-size: 17px;
  font-weight: 600;
}
</style>
