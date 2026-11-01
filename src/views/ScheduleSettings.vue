<template>
  <div class="page page--plain">
    <BackBar title="课表时间" />

    <section class="card">
      <div class="row">
        <span class="row-left">每日显示节数</span>
        <div class="stepper">
          <button type="button" aria-label="减少节数" @click="changeCount(-1)">−</button>
          <input
            v-model.number="draftCount"
            type="number"
            :min="MIN_PERIODS"
            :max="MAX_PERIODS"
            inputmode="numeric"
            aria-label="每日显示节数"
          />
          <button type="button" aria-label="增加节数" @click="changeCount(1)">＋</button>
        </div>
      </div>
      <p class="note">
        默认 12 节。设为较少的节数时，如果某周仍有更晚的课，课表会自动多显示几行，不会隐藏课程。
      </p>
    </section>

    <h2 class="section-title">各节时间</h2>
    <section class="card">
      <div v-for="section in draftCount" :key="section" class="time-row">
        <span class="section-label">第 {{ section }} 节</span>
        <div class="time-inputs">
          <input v-model="draftTimes[section].start" type="time" :aria-label="`第 ${section} 节开始时间`" />
          <span class="dash">—</span>
          <input v-model="draftTimes[section].end" type="time" :aria-label="`第 ${section} 节结束时间`" />
        </div>
      </div>
    </section>

    <p v-if="error" class="hint">{{ error }}</p>
    <div class="actions">
      <button class="primary" type="button" :disabled="Boolean(error)" @click="save">保存</button>
      <button class="ghost" type="button" @click="reset">恢复默认</button>
    </div>

    <p class="footnote">
      只影响 App 的课表显示、桌面组件与变更提醒时间，不修改教务系统原始课表。
    </p>

    <transition name="fade">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import BackBar from '../components/BackBar.vue'
import {
  MAX_PERIODS,
  MIN_PERIODS,
  useScheduleSettingsStore,
  validateScheduleDraft
} from '../stores/scheduleSettings'

const settings = useScheduleSettingsStore()
const draftCount = ref(settings.periodCount)
const draftTimes = ref(cloneTimes(settings.times))
const toast = ref('')
let toastTimer = null

const error = computed(() => validateScheduleDraft(draftCount.value, draftTimes.value))

function cloneTimes(source) {
  const result = {}
  for (let section = 1; section <= MAX_PERIODS; section += 1) {
    const item = source?.[section] || {}
    result[section] = { start: item.start || '', end: item.end || '' }
  }
  return result
}

function changeCount(step) {
  const next = Number(draftCount.value) + step
  draftCount.value = Math.min(Math.max(next, MIN_PERIODS), MAX_PERIODS)
}

function showToast(message) {
  toast.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toast.value = ''
  }, 1800)
}

function save() {
  if (error.value) {
    showToast(error.value)
    return
  }
  const message = settings.save(draftCount.value, draftTimes.value)
  if (message) {
    showToast(message)
    return
  }
  draftCount.value = settings.periodCount
  draftTimes.value = cloneTimes(settings.times)
  showToast('课表时间已保存')
}

function reset() {
  if (!window.confirm('恢复默认节次时间与每日 12 节？')) return
  settings.reset()
  draftCount.value = settings.periodCount
  draftTimes.value = cloneTimes(settings.times)
  showToast('已恢复默认')
}

onBeforeUnmount(() => {
  clearTimeout(toastTimer)
})
</script>

<style scoped>
.card {
  padding: 4px 16px;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 54px;
}

.row-left {
  font-size: 14px;
  color: var(--text);
}

.stepper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stepper button {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: var(--card-muted);
  color: var(--text);
  font-size: 18px;
  line-height: 1;
}

.stepper input {
  width: 54px;
  height: 34px;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: var(--card-muted);
  color: var(--text);
  text-align: center;
  font-size: 15px;
}

.note,
.hint,
.footnote {
  margin: 4px 0 12px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-tertiary);
}

.hint {
  margin: 12px 4px 0;
  color: var(--warning);
}

.time-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  border-bottom: 1px solid var(--border);
}

.time-row:last-child {
  border-bottom: 0;
}

.section-label {
  width: 62px;
  font-size: 13px;
  color: var(--text-secondary);
}

.time-inputs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.time-inputs input {
  width: 112px;
  height: 36px;
  padding: 0 8px;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: var(--card-muted);
  color: var(--text);
  font-family: inherit;
  font-size: 14px;
}

.dash {
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
