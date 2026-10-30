<template>
  <div class="page page--plain">
    <BackBar title="关于" />

    <div class="brand">
      <img class="logo" src="../assets/app-icon.png" alt="柳职教务" />
      <h1 class="app-name">柳职教务</h1>
      <p class="app-version">{{ versionText }}</p>
      <p class="app-sub">柳州职业技术大学 · 教务助手（非官方）</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import BackBar from '../components/BackBar.vue'
import { useUpdateStore } from '../stores/update'

const update = useUpdateStore()

const versionText = computed(() =>
  update.currentVersion ? `v${update.currentVersion}` : '版本读取中…'
)

onMounted(async () => {
  await update.loadVersion()
})
</script>

<style scoped>
.brand {
  text-align: center;
  padding: 18px 0 24px;
}

.logo {
  width: 72px;
  height: 72px;
  margin: 0 auto 12px;
  border-radius: 20px;
  display: block;
}

.app-name {
  font-size: 20px;
  font-weight: 600;
}

.app-version {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}

.app-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-tertiary);
}
</style>
