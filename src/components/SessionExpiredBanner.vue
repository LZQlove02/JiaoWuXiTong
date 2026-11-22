<template>
  <div class="expired-bar" role="status" aria-live="polite">
    <span class="expired-bar-text">{{ text }}</span>
    <button class="expired-bar-action" type="button" @click="$emit('relogin')">
      重新登录
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  // 静默换票失败的原因：只用来区分「学校认证到期」与其他情况，不把内部代号展示给用户
  reason: { type: String, default: null }
})

defineEmits(['relogin'])

const AUTH_EXPIRED_REASONS = new Set([
  'no-ticket',
  'login-form',
  'no-cookie',
  'verify',
  'unsupported'
])

const text = computed(() =>
  AUTH_EXPIRED_REASONS.has(props.reason)
    ? '学校统一认证已到期，当前显示离线缓存'
    : '登录已过期，当前显示离线缓存'
)
</script>

<style scoped>
.expired-bar {
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: calc(env(safe-area-inset-top) + 10px) 14px 10px;
  background: var(--card);
  border-bottom: 1px solid var(--border);
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.4;
}

.expired-bar-text {
  flex: 1;
  min-width: 0;
}

.expired-bar-action {
  flex: none;
  border: 0;
  border-radius: 999px;
  padding: 6px 14px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.expired-bar-action:active {
  opacity: 0.7;
}
</style>
