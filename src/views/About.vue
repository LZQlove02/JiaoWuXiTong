<template>
  <div class="page page--plain">
    <BackBar title="关于" />

    <div class="brand">
      <img class="logo" src="../assets/app-icon.png" alt="柳职教务" />
      <h1 class="app-name">柳职教务</h1>
      <p class="app-version">{{ versionText }}</p>
      <p class="app-sub">柳州职业技术大学 · 教务助手（非官方）</p>
    </div>

    <section class="card rows">
      <button class="row" type="button" :disabled="update.checking" @click="check">
        <span class="row-left"><AppIcon name="refresh" />检查更新</span>
        <span class="tiny">{{ checkLabel }}</span>
      </button>
      <router-link class="row" to="/feedback">
        <span class="row-left"><AppIcon name="mail" />意见反馈</span>
        <AppIcon name="chevron-right" />
      </router-link>
    </section>

    <p class="note">
      更新检查每天最多一次，只读取发布仓库的版本号；网络不可达时静默跳过，可随时手动重试。
      更新包请到发布页用系统浏览器下载，App 内不下载、不安装。
    </p>

    <transition name="fade">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import BackBar from '../components/BackBar.vue'
import AppIcon from '../components/AppIcon.vue'
import { useUpdateStore } from '../stores/update'

const update = useUpdateStore()
const toast = ref('')
let toastTimer = null

const versionText = computed(() =>
  update.currentVersion ? `v${update.currentVersion}` : '版本读取中…'
)

const checkLabel = computed(() => {
  if (update.checking) return '检查中…'
  if (update.latest?.tag) return `最新 ${update.latest.tag}`
  return ''
})

function showToast(message) {
  toast.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toast.value = ''
  }, 1800)
}

async function check() {
  const status = await update.checkNow()
  const messages = {
    latest: '已是最新版本',
    empty: '发布仓库暂未发布版本',
    error: update.error || '检查更新失败',
    busy: '正在检查，请稍候'
  }
  if (status === 'update') return
  showToast(messages[status] || '')
}

onMounted(async () => {
  await update.loadVersion()
})

onBeforeUnmount(() => {
  clearTimeout(toastTimer)
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

.rows {
  padding: 0 16px;
}

.row {
  width: 100%;
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text);
  font-size: 15px;
  text-align: left;
  background: none;
}

.row + .row {
  border-top: 1px solid var(--border);
}

.row-left {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.note {
  margin: 14px 4px 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-tertiary);
}

.toast {
  position: fixed;
  left: 50%;
  bottom: calc(env(safe-area-inset-bottom) + 72px);
  transform: translateX(-50%);
  padding: 8px 16px;
  border-radius: 18px;
  background: rgba(0, 0, 0, 0.72);
  color: #fff;
  font-size: 13px;
  z-index: 108;
}
</style>
