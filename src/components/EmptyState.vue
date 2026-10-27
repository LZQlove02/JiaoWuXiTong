<template>
  <div class="empty">
    <AppIcon :name="icon" />
    <p class="empty-title">{{ title }}</p>
    <p class="empty-desc">{{ description }}</p>
  </div>
</template>

<script setup>
import AppIcon from './AppIcon.vue'

defineProps({
  icon: { type: String, default: 'info' },
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' }
})
</script>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 48px 16px;
  color: var(--text-tertiary);
}

.empty-title {
  font-size: 15px;
  color: var(--text-secondary);
}

.empty-desc {
  font-size: 13px;
}
</style>
