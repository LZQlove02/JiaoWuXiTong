<template>
  <div class="empty">
    <AppIcon :name="icon" />
    <p class="empty-title">{{ title }}</p>
    <p v-if="description" class="empty-desc">{{ description }}</p>
    <button v-if="actionLabel" class="empty-action" type="button" @click="$emit('action')">
      {{ actionLabel }}
    </button>
  </div>
</template>

<script setup>
import AppIcon from './AppIcon.vue'

defineProps({
  icon: { type: String, default: 'info' },
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
  actionLabel: { type: String, default: '' }
})

defineEmits(['action'])
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

.empty-action {
  margin-top: 10px;
  padding: 8px 18px;
  border-radius: 20px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 13px;
  font-weight: 600;
}
</style>
