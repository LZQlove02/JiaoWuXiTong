<template>
  <div class="mask" role="dialog" aria-modal="true" @click.self="$emit('cancel')">
    <div class="dialog">
      <h2 class="dialog-title">{{ title }}</h2>
      <p class="dialog-text">{{ message }}</p>
      <button class="primary" :class="{ danger }" type="button" @click="$emit('confirm')">
        {{ confirmText }}
      </button>
      <button class="ghost" type="button" @click="$emit('cancel')">{{ cancelText }}</button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  message: { type: String, required: true },
  confirmText: { type: String, default: '确认' },
  cancelText: { type: String, default: '取消' },
  danger: { type: Boolean, default: false }
})

defineEmits(['confirm', 'cancel'])
</script>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 130;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.42);
}

.dialog {
  width: 100%;
  max-width: 420px;
  padding: 20px 18px 14px;
  border-radius: 16px;
  background: var(--card);
  box-shadow: var(--shadow);
}

.dialog-title {
  font-size: 17px;
  font-weight: 600;
  text-align: center;
}

.dialog-text {
  margin: 12px 0 18px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary);
  text-align: justify;
}

.primary {
  width: 100%;
  padding: 12px;
  border-radius: 12px;
  background: var(--brand);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.primary.danger {
  background: var(--danger);
}

.ghost {
  width: 100%;
  margin-top: 8px;
  padding: 9px;
  font-size: 13px;
  color: var(--text-tertiary);
}
</style>
