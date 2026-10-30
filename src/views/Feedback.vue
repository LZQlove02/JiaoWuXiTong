<template>
  <div class="page page--plain">
    <BackBar title="意见反馈" />

    <section class="card form">
      <label class="label" for="feedback-desc">问题描述</label>
      <textarea
        id="feedback-desc"
        v-model="description"
        class="textarea"
        maxlength="2000"
        rows="6"
        placeholder="遇到了什么问题？在哪个页面？可以这样复现："
      ></textarea>

      <label class="label" for="feedback-contact">联系方式（选填）</label>
      <input
        id="feedback-contact"
        v-model="contact"
        class="input"
        maxlength="80"
        type="text"
        placeholder="QQ / 邮箱，方便回复你"
      />

      <div class="row">
        <span class="row-left">附上诊断信息</span>
        <button
          class="switch"
          :class="{ 'is-on': withDiagnostics }"
          role="switch"
          :aria-checked="withDiagnostics"
          aria-label="附上诊断信息"
          @click="withDiagnostics = !withDiagnostics"
        >
          <span class="switch-dot"></span>
        </button>
      </div>

      <pre v-if="withDiagnostics" class="diagnostics">{{ diagnosticsText }}</pre>
      <p class="note">诊断信息只含版本、机型与系统版本，不含学号、姓名、成绩或登录会话。</p>
    </section>

    <div class="actions">
      <button class="primary" type="button" :disabled="busy" @click="send">
        {{ busy ? '正在打开邮件…' : '发送邮件' }}
      </button>
      <button class="ghost" type="button" @click="copy">复制内容</button>
    </div>

    <p v-if="hint" class="hint">{{ hint }}</p>

    <transition name="fade">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Capacitor } from '@capacitor/core'
import BackBar from '../components/BackBar.vue'
import { useUiStore } from '../stores/ui'
import { useUpdateStore } from '../stores/update'
import { composeFeedback, copyText, errorCode, getDeviceInfo } from '../native/supportBridge'
import { buildDiagnostics } from '../utils/diagnostics'
import {
  FEEDBACK_EMAIL,
  buildFeedbackBody,
  buildFeedbackSubject
} from '../utils/feedback'

const ui = useUiStore()
const update = useUpdateStore()

const description = ref('')
const contact = ref('')
const withDiagnostics = ref(true)
const device = ref(null)
const busy = ref(false)
const hint = ref('')
const toast = ref('')
let toastTimer = null

const platform = Capacitor.isNativePlatform()
  ? `Android（${Capacitor.getPlatform()}）`
  : 'Web 预览'

const diagnosticsText = computed(() =>
  buildDiagnostics({
    version: update.currentVersion,
    device: device.value,
    theme: ui.theme,
    platform,
    online: navigator.onLine
  })
)

function showToast(message) {
  toast.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toast.value = ''
  }, 1800)
}

function buildBody() {
  return buildFeedbackBody({
    description: description.value,
    diagnostics: withDiagnostics.value ? diagnosticsText.value : '',
    contact: contact.value
  })
}

async function send() {
  hint.value = ''
  if (!description.value.trim()) {
    hint.value = '请先简单描述遇到的问题。'
    return
  }
  busy.value = true
  try {
    await composeFeedback(
      buildFeedbackSubject(update.currentVersion),
      buildBody()
    )
    showToast('已打开邮件 App，请确认后发送')
  } catch (error) {
    const code = errorCode(error)
    if (code === 'NO_EMAIL_APP') {
      hint.value = `未找到邮件 App，可点「复制内容」后发送到 ${FEEDBACK_EMAIL}`
    } else if (code === 'BODY_TOO_LONG') {
      hint.value = '内容过长，请精简问题描述后重试。'
    } else {
      hint.value = '打开邮件失败，可复制内容后手动发送。'
    }
  } finally {
    busy.value = false
  }
}

async function copy() {
  hint.value = ''
  if (!description.value.trim()) {
    hint.value = '请先简单描述遇到的问题。'
    return
  }
  try {
    const text = `收件人：${FEEDBACK_EMAIL}\n\n${buildBody()}`
    await copyText(text)
    showToast('已复制，可粘贴到邮件或聊天窗口')
  } catch (error) {
    hint.value = '复制失败，请检查系统剪贴板权限。'
  }
}

onMounted(async () => {
  await update.loadVersion()
  try {
    device.value = await getDeviceInfo()
  } catch (error) {
    // 取不到设备信息时诊断文本自动降级为「未知」
  }
})

onBeforeUnmount(() => {
  clearTimeout(toastTimer)
})
</script>

<style scoped>
.form {
  padding: 16px;
}

.label {
  display: block;
  margin: 4px 0 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.textarea,
.input {
  width: 100%;
  margin-bottom: 16px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--card-muted);
  color: var(--text);
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
}

.textarea {
  resize: vertical;
  min-height: 120px;
}

.input {
  height: 42px;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
}

.row-left {
  font-size: 14px;
  color: var(--text);
}

.switch {
  width: 46px;
  height: 28px;
  padding: 2px;
  border-radius: 14px;
  background: var(--card-muted);
  transition: background 0.18s ease;
}

.switch.is-on {
  background: var(--brand);
}

.switch-dot {
  display: block;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #fff;
  transform: translateX(0);
  transition: transform 0.18s ease;
}

.switch.is-on .switch-dot {
  transform: translateX(18px);
}

.diagnostics {
  margin: 8px 0 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--card-muted);
  font-family: inherit;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

.note {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-tertiary);
}

.actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}

.primary,
.ghost {
  flex: 1;
  height: 46px;
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

.hint {
  margin: 12px 4px 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--warning);
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
