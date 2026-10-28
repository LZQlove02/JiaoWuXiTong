<template>
  <div v-if="loading" class="card state">{{ loadingText }}</div>
  <div v-else-if="error" class="card state">
    <p class="error-text">{{ error }}</p>
    <button class="retry" type="button" @click="$emit('retry')">重新加载</button>
  </div>
</template>

<script setup>
defineProps({
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  loadingText: { type: String, default: '正在加载…' }
})

defineEmits(['retry'])
</script>
