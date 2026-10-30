<template>
  <nav class="tab-bar">
    <router-link
      v-for="item in items"
      :key="item.path"
      :to="item.path"
      class="tab-item"
      active-class="is-active"
    >
      <span class="tab-icon" v-html="item.icon"></span>
      <span class="tab-label">{{ item.label }}</span>
      <span
        v-if="item.path === '/schedule' && changes.badgeText > 0"
        class="tab-badge"
      >{{ badgeText }}</span>
    </router-link>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useChangesStore } from '../stores/changes'
import { unreadBadgeText } from '../utils/changeLabels'

const changes = useChangesStore()
const badgeText = computed(() => unreadBadgeText(changes.badgeText))

const items = [
  {
    path: '/',
    label: '首页',
    icon: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/></svg>'
  },
  {
    path: '/schedule',
    label: '课表',
    icon: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4.5" width="18" height="16" rx="2"/><path d="M3 9.5h18M8 3v3M16 3v3"/></svg>'
  },
  {
    path: '/grades',
    label: '成绩',
    icon: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/></svg>'
  },
  {
    path: '/profile',
    label: '我的',
    icon: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21c1.6-3.6 4.5-5.5 8-5.5s6.4 1.9 8 5.5"/></svg>'
  }
]
</script>

<style scoped>
.tab-bar {
  flex: none;
  width: 100%;
  display: flex;
  height: calc(var(--tab-height) + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  background: var(--card);
  border-top: 1px solid var(--border);
}

.tab-item {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  color: var(--text-tertiary);
  font-size: 11px;
}

.tab-badge {
  position: absolute;
  top: 4px;
  left: 50%;
  transform: translateX(8px);
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--danger);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
  font-weight: 600;
}

.tab-item.is-active {
  color: var(--brand);
}

.tab-icon {
  display: flex;
}

@media (min-width: 768px) {
  .tab-bar {
    max-width: 760px;
    margin: 0 auto;
    border-radius: 16px 16px 0 0;
  }
}
</style>
