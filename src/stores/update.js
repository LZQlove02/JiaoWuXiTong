import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { Capacitor } from '@capacitor/core'
import { App as CapApp } from '@capacitor/app'
import { useAuthStore } from './auth'
import { checkLatest, openReleasePage } from '../native/supportBridge'
import { isDisclaimerAccepted } from '../utils/disclaimer'
import { formatReleaseNotes, isNewer, shouldAutoCheck } from '../utils/update'

const LAST_CHECK_KEY = 'lzpu.update.lastCheckAt'
const PROMPTED_KEY = 'lzpu.update.promptedTag'

function readNumber(key) {
  try {
    return Number(localStorage.getItem(key)) || 0
  } catch (error) {
    return 0
  }
}

function readText(key) {
  try {
    return localStorage.getItem(key) || ''
  } catch (error) {
    return ''
  }
}

function write(key, value) {
  try {
    localStorage.setItem(key, String(value))
  } catch (error) {
    // 存储不可用时只在本次会话生效，不阻塞更新检查
  }
}

export const useUpdateStore = defineStore('update', () => {
  const auth = useAuthStore()
  const latest = ref(null)
  const checking = ref(false)
  const error = ref('')
  const currentVersion = ref('')
  const dialogOpen = ref(false)
  const lastCheckAt = ref(readNumber(LAST_CHECK_KEY))
  const promptedTag = ref(readText(PROMPTED_KEY))

  const hasUpdate = computed(
    () => Boolean(latest.value) && isNewer(latest.value.tag, currentVersion.value)
  )
  const releaseNotes = computed(() => formatReleaseNotes(latest.value?.notes || ''))

  async function loadVersion() {
    if (currentVersion.value) return currentVersion.value
    if (Capacitor.isNativePlatform()) {
      try {
        const info = await CapApp.getInfo()
        currentVersion.value = info.version || ''
      } catch (error) {
        // 读不到版本号时按「无法比较」处理，不弹更新
      }
    }
    return currentVersion.value
  }

  /**
   * manual=true 时忽略 24h 节流与「已提示」记忆，失败也写 error；
   * 自动检查则完全静默，不改变 error。
   */
  async function runCheck({ manual = false } = {}) {
    if (checking.value) return 'busy'
    await loadVersion()
    checking.value = true
    if (manual) error.value = ''
    try {
      const info = await checkLatest()
      error.value = ''
      if (!info || !info.found) {
        latest.value = null
        return 'empty'
      }
      latest.value = info
      if (!isNewer(info.tag, currentVersion.value)) return 'latest'
      if (manual || promptedTag.value !== info.tag) {
        dialogOpen.value = true
      }
      return 'update'
    } catch (err) {
      if (manual) error.value = '检查更新失败，请稍后重试'
      return 'error'
    } finally {
      // 无论成功失败都记录本次尝试，避免断网时每次回前台都请求
      lastCheckAt.value = Date.now()
      write(LAST_CHECK_KEY, lastCheckAt.value)
      checking.value = false
    }
  }

  /** 已同意免责声明且已登录时才自动检查；每天最多一次。 */
  async function autoCheck() {
    if (!isDisclaimerAccepted() || !auth.isLoggedIn) return
    if (!shouldAutoCheck(lastCheckAt.value)) return
    await runCheck()
  }

  function checkNow() {
    return runCheck({ manual: true })
  }

  /** 「以后再说」：记住该版本，自动检查不再打扰。 */
  function dismiss() {
    dialogOpen.value = false
    if (!latest.value) return
    promptedTag.value = latest.value.tag
    write(PROMPTED_KEY, promptedTag.value)
  }

  async function openRelease() {
    const tag = latest.value?.tag
    if (!tag) return
    try {
      await openReleasePage(tag)
    } catch (err) {
      error.value = '无法打开发布页，请稍后重试'
    }
  }

  return {
    latest,
    checking,
    error,
    currentVersion,
    dialogOpen,
    lastCheckAt,
    promptedTag,
    hasUpdate,
    releaseNotes,
    loadVersion,
    autoCheck,
    checkNow,
    dismiss,
    openRelease
  }
})
