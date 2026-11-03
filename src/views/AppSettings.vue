<template>
  <div class="page page--plain">
    <BackBar title="应用与隐私" />

    <h2 class="section-title">外观</h2>
    <section class="card">
      <div class="theme-row">
        <button
          v-for="option in themeOptions"
          :key="option.value"
          class="theme-btn"
          :class="{ 'is-active': ui.theme === option.value }"
          @click="ui.setTheme(option.value)"
        >
          {{ option.label }}
        </button>
      </div>
    </section>

    <h2 class="section-title">隐私与数据</h2>
    <section class="card settings">
      <router-link class="setting-row" to="/privacy">
        <span class="setting-left"><AppIcon name="shield" />隐私政策</span>
        <span class="setting-right">
          <span class="tiny">v{{ privacyVersion }}</span>
          <AppIcon name="chevron-right" />
        </span>
      </router-link>
      <router-link class="setting-row" to="/licenses">
        <span class="setting-left"><AppIcon name="info" />开源许可</span>
        <AppIcon name="chevron-right" />
      </router-link>
      <button class="setting-row" @click="openDisclaimer">
        <span class="setting-left"><AppIcon name="notice" />免责声明</span>
        <span class="tiny">v{{ disclaimerVersion }}</span>
      </button>
      <button class="setting-row" @click="askClearData">
        <span class="setting-left"><AppIcon name="trash" />清除本机个人数据</span>
        <AppIcon name="chevron-right" />
      </button>
      <button class="setting-row" @click="askWithdrawPrivacy">
        <span class="setting-left"><AppIcon name="shield" />撤回隐私同意</span>
        <AppIcon name="chevron-right" />
      </button>
      <p class="setting-note">
        清除本机个人数据会退出登录并删除本机缓存；撤回隐私同意还会停止处理，下次启动需重新阅读并同意。
      </p>
    </section>

    <h2 class="section-title">通用与帮助</h2>
    <section class="card settings">
      <div class="setting-row">
        <span class="setting-left"><AppIcon name="info" />本地缓存</span>
        <span class="tiny">{{ storageLabel }}</span>
      </div>
      <button class="setting-row" @click="clearCache">
        <span class="setting-left"><AppIcon name="trash" />清除缓存</span>
        <AppIcon name="chevron-right" />
      </button>
      <button class="setting-row" :disabled="update.checking" @click="checkUpdate">
        <span class="setting-left"><AppIcon name="refresh" />检查更新</span>
        <span class="tiny">{{ updateLabel }}</span>
      </button>
      <router-link class="setting-row" to="/tutorial">
        <span class="setting-left"><AppIcon name="guide" />使用教程</span>
        <AppIcon name="chevron-right" />
      </router-link>
      <button class="setting-row" @click="showAbout">
        <span class="setting-left"><AppIcon name="info" />关于</span>
        <span v-if="update.currentVersion" class="tiny">v{{ update.currentVersion }}</span>
      </button>
      <router-link class="setting-row" to="/feedback">
        <span class="setting-left"><AppIcon name="mail" />意见反馈</span>
        <AppIcon name="chevron-right" />
      </router-link>
      <p class="setting-note">清除缓存不会退出登录，也不影响主题与提醒设置。</p>
    </section>

    <transition name="fade">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>

    <ConsentGate
      v-if="disclaimerOpen"
      mode="reader"
      :clauses="DISCLAIMER_CLAUSES"
      title="使用前请阅读"
      subtitle="教务助手 · 免责声明"
      :version-text="`声明版本 v${DISCLAIMER_VERSION} · ${DISCLAIMER_DATE}`"
      @close="closeDisclaimer"
    />
    <ConfirmDialog
      v-if="confirm"
      :title="confirm.title"
      :message="confirm.message"
      :confirm-text="confirm.confirmText"
      :danger="confirm.danger"
      @confirm="runConfirm"
      @cancel="confirm = null"
    />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUiStore } from '../stores/ui'
import { useAuthStore } from '../stores/auth'
import { useUpdateStore } from '../stores/update'
import {
  clearAuthDownloads,
  clearPublicDownloads,
  setKeepAlive as setKeepAliveNative
} from '../api/nativeHttp'
import { getStorageInfo } from '../native/supportBridge'
import { DISCLAIMER_CLAUSES, DISCLAIMER_DATE, DISCLAIMER_VERSION } from '../utils/disclaimer'
import { PRIVACY_VERSION, revokePrivacy } from '../utils/privacy'
import { revokeBackgroundConsent } from '../utils/backgroundConsent'
import { revokeKeepAliveConsent } from '../utils/keepAliveConsent'
import { estimateLocalStorageBytes, formatBytes } from '../utils/storage'
import AppIcon from '../components/AppIcon.vue'
import BackBar from '../components/BackBar.vue'
import ConsentGate from '../components/ConsentGate.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'

const ui = useUiStore()
const auth = useAuthStore()
const update = useUpdateStore()
const router = useRouter()

const themeOptions = [
  { value: 'system', label: '跟随系统' },
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' }
]

const toast = ref('')
const disclaimerOpen = ref(false)
const disclaimerVersion = DISCLAIMER_VERSION
const privacyVersion = PRIVACY_VERSION
const confirm = ref(null)
const localBytes = ref(0)
const nativeBytes = ref(0)

const storageLabel = computed(() => formatBytes(localBytes.value + nativeBytes.value))

const updateLabel = computed(() => {
  if (update.checking) return '检查中…'
  if (update.latest?.tag) return `最新 ${update.latest.tag}`
  return ''
})

function showToast(message) {
  toast.value = message
  setTimeout(() => {
    toast.value = ''
  }, 1600)
}

async function refreshStorage() {
  localBytes.value = estimateLocalStorageBytes()
  try {
    const info = await getStorageInfo()
    nativeBytes.value = Number(info?.totalBytes) || 0
  } catch (error) {
    nativeBytes.value = 0
  }
}

// 只读重读声明，不改变同意状态；返回键交给 ui store 关闭浮层
function openDisclaimer() {
  disclaimerOpen.value = true
  ui.setOverlayCloser(closeDisclaimer)
}

function closeDisclaimer() {
  disclaimerOpen.value = false
  ui.clearOverlayCloser()
}

onBeforeUnmount(() => {
  if (disclaimerOpen.value) ui.clearOverlayCloser()
})

function askClearData() {
  confirm.value = {
    title: '清除本机个人数据',
    message:
      '将退出登录并删除本机保存的会话、课表、成绩、通知缓存与桌面组件数据。学校服务器上的数据不受影响。',
    confirmText: '清除并退出',
    danger: true,
    action: 'clear'
  }
}

function askWithdrawPrivacy() {
  confirm.value = {
    title: '撤回隐私同意',
    message: '撤回后将停止处理并清除本机个人数据，下次启动需重新阅读并同意隐私政策。',
    confirmText: '撤回并清除',
    danger: true,
    action: 'withdraw'
  }
}

async function runConfirm() {
  const action = confirm.value?.action
  confirm.value = null
  if (!action) return
  try {
    await auth.logout()
    if (action === 'withdraw') {
      revokePrivacy()
      revokeBackgroundConsent()
      revokeKeepAliveConsent()
      try {
        await setKeepAliveNative(false)
      } catch (error) {
        // 撤回同意后即使原生取消失败也不阻断清除流程
      }
      window.location.reload()
      return
    }
    router.replace('/login')
    showToast('已清除本机个人数据')
  } catch (error) {
    showToast(error?.message || '操作失败，请重试')
  }
}

// 清理范围：公开内容缓存 + 个人数据缓存 + 已下载附件/图片，不动登录态与主题
async function clearCache() {
  Object.keys(localStorage)
    .filter(
      (key) =>
        key.startsWith('jiaowu.cache') ||
        key.startsWith('jiaowu.portal.v1.') ||
        key.startsWith('jiaowu.data.v1.')
    )
    .forEach((key) => localStorage.removeItem(key))
  try {
    await Promise.all([clearAuthDownloads(), clearPublicDownloads()])
  } catch (error) {
    // 原生缓存清理失败不影响 localStorage 清理结果
  }
  await refreshStorage()
  showToast('已清除本地缓存')
}

function showAbout() {
  router.push('/about')
}

async function checkUpdate() {
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
  refreshStorage()
  await update.loadVersion()
})
</script>

<style scoped>
.theme-row {
  display: flex;
  gap: 8px;
}

.theme-btn {
  flex: 1;
  padding: 10px 0;
  border-radius: 10px;
  background: var(--card-muted);
  color: var(--text-secondary);
  font-size: 14px;
}

.theme-btn.is-active {
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 600;
}

.settings {
  padding: 4px 16px;
}

.setting-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border);
  color: var(--text-tertiary);
}

.setting-row:last-child {
  border-bottom: none;
}

.setting-left {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  color: var(--text);
}

.setting-right {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.setting-note {
  padding: 0 0 12px;
  border-bottom: 1px solid var(--border);
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-tertiary);
}

.toast {
  position: fixed;
  left: 50%;
  bottom: calc(env(safe-area-inset-bottom) + 32px);
  transform: translateX(-50%);
  background: rgba(20, 20, 20, 0.86);
  color: #fff;
  font-size: 13px;
  padding: 9px 16px;
  border-radius: 20px;
  z-index: 50;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
