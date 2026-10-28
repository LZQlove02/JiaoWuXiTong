<template>
  <div class="page">
    <header class="row">
      <h1 class="page-title">成绩</h1>
      <select v-model="termIndex" class="term-select" @change="changeTerm">
        <option v-for="(term, index) in terms" :key="term.label" :value="index">
          {{ term.label }}
        </option>
      </select>
    </header>

    <section v-if="summary" class="card summary">
      <div class="stat">
        <span class="stat-value">{{ summary.gpa.toFixed(2) }}</span>
        <span class="tiny">平均学分绩点</span>
      </div>
      <div class="stat">
        <span class="stat-value">{{ summary.totalCredits }}</span>
        <span class="tiny">总学分</span>
      </div>
      <div class="stat">
        <span class="stat-value">{{ summary.count }}</span>
        <span class="tiny">课程门数</span>
      </div>
    </section>

    <div v-if="loading && !grades" class="card loading">正在加载成绩…</div>

    <section v-else class="card list">
      <div v-for="item in gradeList" :key="item.courseId" class="grade-row">
        <div class="grade-main">
          <p class="grade-name">{{ item.courseName }}</p>
          <p class="tiny">{{ item.examType }} · {{ item.credit }} 学分</p>
        </div>
        <span class="grade-score" :class="{ failed: Number(item.score) < 60 }">
          {{ item.score }}
        </span>
      </div>
      <EmptyState v-if="!gradeList.length" title="暂无成绩" description="该学期还没有成绩记录。" />
    </section>

    <p v-if="errors.grades" class="tiny footnote">{{ errors.grades }}</p>
    <p v-else class="tiny footnote">
      {{
        grades?.source
          ? '真实数据（网页导入）'
          : '成绩为模拟数据，可在「我的 → 导入成绩」接入网页数据。'
      }}
    </p>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useAcademicStore } from '../stores/academic'
import EmptyState from '../components/EmptyState.vue'

const academic = useAcademicStore()
const { terms, grades, loading, errors } = storeToRefs(academic)
const termIndex = ref(0)

const gradeList = computed(() => grades.value?.grades || [])
const summary = computed(() => grades.value?.summary || null)

function changeTerm() {
  const term = terms.value[termIndex.value]
  if (term) academic.loadGrades(term)
}

onMounted(async () => {
  await academic.initTerms()
  const index = terms.value.findIndex((item) => item.isCurrent)
  termIndex.value = index >= 0 ? index : 0
  if (!grades.value) await academic.loadGrades(terms.value[termIndex.value])
})
</script>

<style scoped>
.term-select {
  height: 34px;
  padding: 0 10px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text);
  font-size: 12px;
  outline: none;
  max-width: 180px;
}

.summary {
  margin-top: 14px;
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

.list {
  margin-top: 12px;
  padding: 4px 16px;
}

.grade-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}

.grade-row:last-child {
  border-bottom: none;
}

.grade-name {
  font-size: 15px;
}

.grade-score {
  font-size: 20px;
  font-weight: 700;
  color: var(--success);
  font-variant-numeric: tabular-nums;
}

.grade-score.failed {
  color: var(--danger);
}

.footnote {
  margin-top: 14px;
  text-align: center;
}
</style>
