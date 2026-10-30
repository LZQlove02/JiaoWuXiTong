<template>
  <div class="update-mask" role="dialog" aria-modal="true" aria-labelledby="update-title">
    <div class="update-card">
      <h2 id="update-title" class="update-title">发现新版本</h2>
      <p class="update-meta">
        v{{ displayTag }}
        <span v-if="update.currentVersion"> · 当前 v{{ update.currentVersion }}</span>
      </p>
      <pre v-if="update.releaseNotes" class="update-notes">{{ update.releaseNotes }}</pre>
      <p v-else class="update-notes is-empty">新版本包含若干修复与体验改进。</p>
      <div class="update-actions">
        <button class="primary" type="button" @click="goUpdate">去更新</button>
        <button class="ghost" type="button" @click="later">以后再说</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useUiStore } from '../stores/ui'
import { useUpdateStore } from '../stores/update'

const ui = useUiStore()
const update = useUpdateStore()

const displayTag = computed(() => (update.latest?.tag || '').replace(/^v/i, ''))

function close() {
  update.dismiss()
}

function later() {
  close()
}

async function goUpdate() {
  await update.openRelease()
  close()
}

onMounted(() => {
  ui.setOverlayCloser(later)
})

onBeforeUnmount(() => {
  ui.clearOverlayCloser()
})
</script>

<style scoped>
.update-mask {
  position: fixed;
  inset: 0;
  z-index: 110;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(10, 12, 15, 0.45);
}

.update-card {
  width: 100%;
  max-width: 380px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  padding: 20px;
  border-radius: 18px;
  background: var(--card);
  box-shadow: var(--shadow);
}

.update-title {
  font-size: 17px;
  font-weight: 600;
  text-align: center;
}

.update-meta {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-secondary);
  text-align: center;
}

.update-notes {
  margin: 14px 0 18px;
  padding: 12px;
  max-height: 34vh;
  overflow-y: auto;
  border-radius: 12px;
  background: var(--card-muted);
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

.update-notes.is-empty {
  text-align: center;
}

.update-actions {
  display: flex;
  gap: 10px;
}

.primary,
.ghost {
  flex: 1;
  height: 44px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 500;
}

.primary {
  color: #fff;
  background: var(--brand);
}

.ghost {
  color: var(--text-secondary);
  background: var(--card-muted);
}
</style>
