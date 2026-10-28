<template>
  <div class="page page--plain">
    <BackBar :title="meta.title" />

    <section class="card placeholder">
      <span class="icon-wrap">
        <AppIcon :name="meta.icon" />
      </span>
      <p class="badge">建设中</p>
      <p class="desc">{{ meta.desc }}</p>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import BackBar from '../components/BackBar.vue'
import AppIcon from '../components/AppIcon.vue'

const route = useRoute()
const meta = computed(() => ({
  title: route.meta.title || '功能',
  icon: route.meta.icon || 'info',
  desc: route.meta.desc || '该功能将在后续里程碑接入。'
}))
</script>

<style scoped>
.placeholder {
  margin-top: 32px;
  padding: 40px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  text-align: center;
}

.icon-wrap {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: var(--brand-soft);
  color: var(--brand);
  display: flex;
  align-items: center;
  justify-content: center;
}

.badge {
  font-size: 12px;
  color: var(--warning);
  background: var(--card-muted);
  padding: 3px 10px;
  border-radius: 20px;
}

.desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.7;
  max-width: 260px;
}
</style>
