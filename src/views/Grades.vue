<template>
  <div class="page">
    <div class="title-row">
      <h1 class="page-title">成绩</h1>
      <button class="term-pill" type="button" :disabled="loading" @click="openSheet">
        <span class="term-label">{{ selectedLabel }}</span>
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>
      </button>
    </div>

    <section v-if="summary && summary.count" class="card summary">
      <div class="stat">
        <span class="stat-value">{{ gpaLabel }}</span>
        <span class="tiny">平均学分绩点</span>
      </div>
      <div class="stat">
        <span class="stat-value">{{ creditsLabel }}</span>
        <span class="tiny">总学分</span>
      </div>
      <div class="stat">
        <span class="stat-value">{{ summary.count }}</span>
        <span class="tiny">课程门数</span>
      </div>
    </section>

    <div v-if="loading && !gradeList.length" class="card loading">正在加载成绩…</div>

    <section v-else-if="!gradeList.length" class="card">
      <EmptyState
        icon="book"
        :title="emptyTitle"
        :description="emptyDescription"
        :action-label="errors.grades ? '重试' : ''"
        @action="reload"
      />
    </section>

    <template v-else>
      <p v-if="errors.grades" class="error-text list-error">{{ errors.grades }}</p>

      <div class="section-head">
        <h2>课程成绩</h2>
        <span class="pill">{{ gradeList.length }} 门</span>
      </div>

      <section class="card list">
        <div v-for="item in gradeList" :key="item.id" class="grade-row">
          <div class="grade-main">
            <p class="grade-name">{{ item.courseName }}</p>
            <p class="grade-meta">{{ metaLine(item) }}</p>
          </div>
          <div class="grade-side">
            <span class="grade-score" :class="{ failed: isFailed(item) }">
              {{ item.score || '—' }}
            </span>
            <span v-if="item.gradePoint !== null" class="grade-point">
              绩点 {{ formatNumber(item.gradePoint) }}
            </span>
          </div>
        </div>
      </section>

      <p class="footnote tiny">
        数据来源：{{ grades.source || '教务系统' }}<template v-if="fetchedLabel"> · 更新于 {{ fetchedLabel }}</template>
      </p>
    </template>

    <div v-if="sheetOpen" class="sheet-mask" @click.self="closeSheet">
      <div class="sheet">
        <p class="sheet-title">选择学期</p>
        <div class="sheet-list">
          <button
            v-for="term in terms"
            :key="`${term.xnm}_${term.xqm}`"
            type="button"
            class="sheet-row"
            @click="selectTerm(term)"
          >
            <span>{{ term.label }}</span>
            <span v-if="isSelected(term)" class="check">✓</span>
          </button>
        </div>
        <p class="sheet-tip">第三学期为暑期短学期，通常没有课程安排。</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useAcademicStore } from '../stores/academic'
import { useUiStore } from '../stores/ui'
import EmptyState from '../components/EmptyState.vue'

const academic = useAcademicStore()
const ui = useUiStore()
const { terms, currentTerm, grades, loading, errors } = storeToRefs(academic)

const selectedTerm = ref(null)
const sheetOpen = ref(false)

const gradeList = computed(() => grades.value?.grades || [])
const summary = computed(() => grades.value?.summary || null)
const selectedLabel = computed(
  () => selectedTerm.value?.label || currentTerm.value?.label || '选择学期'
)
const gpaLabel = computed(() => {
  const gpa = summary.value?.gpa
  return gpa === null || gpa === undefined ? '—' : Number(gpa).toFixed(2)
})
const creditsLabel = computed(() => formatNumber(summary.value?.totalCredits ?? 0))
const emptyTitle = computed(() => (errors.value.grades ? '成绩暂不可用' : '暂无成绩'))
const emptyDescription = computed(
  () => errors.value.grades || '该学期还没有成绩记录。'
)
const fetchedLabel = computed(() => {
  const value = grades.value?.fetchedAt
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit'
  })
})

function formatNumber(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return String(value ?? '')
  return String(Math.round(num * 100) / 100)
}

function metaLine(item) {
  const parts = []
  if (item.category && item.category !== '无') parts.push(item.category)
  if (item.credit !== null && item.credit > 0) {
    parts.push(`${formatNumber(item.credit)} 学分`)
  }
  if (item.teacher && item.teacher !== '无') parts.push(item.teacher)
  return parts.join(' · ')
}

function isFailed(item) {
  const score = Number(item.score)
  return Number.isFinite(score) && score < 60
}

function sameTerm(a, b) {
  return String(a?.xnm) === String(b?.xnm) && String(a?.xqm) === String(b?.xqm)
}

function isSelected(term) {
  return selectedTerm.value ? sameTerm(selectedTerm.value, term) : false
}

async function openSheet() {
  if (!terms.value.length) await academic.initTerms()
  if (terms.value.length) sheetOpen.value = true
}

function closeSheet() {
  sheetOpen.value = false
}

function selectTerm(term) {
  closeSheet()
  if (selectedTerm.value && sameTerm(selectedTerm.value, term)) return
  selectedTerm.value = term
  academic.loadGrades(term)
}

function reload() {
  academic.loadGrades(selectedTerm.value || currentTerm.value)
}

watch(sheetOpen, (open) => {
  if (open) {
    ui.setOverlayCloser(closeSheet)
  } else {
    ui.clearOverlayCloser()
  }
})

onMounted(async () => {
  await academic.initTerms()
  selectedTerm.value = currentTerm.value || null
  await academic.loadGrades(selectedTerm.value)
  if (!selectedTerm.value && grades.value?.term) {
    selectedTerm.value = grades.value.term
  }
})

onUnmounted(() => ui.clearOverlayCloser())
</script>

<style scoped>
.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.term-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 62vw;
  padding: 7px 12px;
  border-radius: 20px;
  border: 1px solid var(--border);
  background: var(--card);
  box-shadow: var(--shadow);
  font-size: 12px;
  color: var(--text-secondary);
}

.term-pill:disabled {
  opacity: 0.6;
}

.term-pill svg {
  flex-shrink: 0;
  color: var(--brand);
}

.term-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary {
  margin-top: 16px;
  display: flex;
  justify-content: space-around;
  padding: 18px 8px;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--brand);
  font-variant-numeric: tabular-nums;
}

.loading {
  margin-top: 12px;
  text-align: center;
  color: var(--text-secondary);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20px 0 10px;
}

.section-head h2 {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-secondary);
}

.list {
  padding: 4px 16px;
}

.grade-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 0;
  border-bottom: 1px solid var(--border);
}

.grade-row:last-child {
  border-bottom: none;
}

.grade-main {
  min-width: 0;
}

.grade-name {
  font-size: 15px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.grade-meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.grade-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 1px;
  flex-shrink: 0;
}

.grade-score {
  font-size: 21px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--success);
  font-variant-numeric: tabular-nums;
}

.grade-score.failed {
  color: var(--danger);
}

.grade-point {
  font-size: 11px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

.list-error {
  margin-top: 14px;
  text-align: center;
}

.footnote {
  margin-top: 14px;
  text-align: center;
}

.sheet-mask {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(0, 0, 0, 0.38);
}

.sheet {
  width: 100%;
  max-width: 760px;
  max-height: 70vh;
  overflow-y: auto;
  background: var(--card);
  border-radius: 16px 16px 0 0;
  padding: 18px 16px calc(env(safe-area-inset-bottom) + 20px);
}

.sheet-title {
  margin-bottom: 6px;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
}

.sheet-tip {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-tertiary);
  text-align: center;
}

.sheet-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 14px 8px;
  border-bottom: 1px solid var(--border);
  font-size: 14px;
  text-align: left;
}

.sheet-row:last-child {
  border-bottom: none;
}

.check {
  color: var(--brand);
  font-weight: 700;
}
</style>
