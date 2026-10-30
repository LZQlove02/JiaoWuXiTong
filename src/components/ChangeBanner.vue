<template>
  <router-link
    v-if="changes.bannerVisible"
    to="/changes"
    class="change-banner"
    @click="changes.markAllRead()"
  >
    <span class="banner-icon"><AppIcon name="notice" /></span>
    <span class="banner-main">
      <span class="banner-title">本周课表有 {{ changes.bannerCount }} 处变更</span>
      <span class="banner-sub">{{ subtitle }}</span>
    </span>
    <AppIcon name="chevron-right" />
  </router-link>
</template>

<script setup>
import { computed } from 'vue'
import { useChangesStore } from '../stores/changes'
import { changeSubtitle } from '../utils/changeLabels'
import AppIcon from './AppIcon.vue'

const changes = useChangesStore()
const subtitle = computed(() => changeSubtitle(changes.latest))
</script>

<style scoped>
.change-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: var(--radius);
  border-left: 3px solid var(--warning);
  background: var(--card);
  box-shadow: var(--shadow);
  color: var(--text-tertiary);
}

.banner-icon {
  flex: none;
  display: flex;
  color: var(--warning);
}

.banner-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.banner-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.banner-sub {
  font-size: 12px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>