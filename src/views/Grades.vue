<template>
  <div class="page">
    <h1 class="page-title">成绩</h1>

    <section v-if="summary && summary.count" class="card summary">
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
      <EmptyState
        v-if="!gradeList.length"
        icon="book"
        :title="emptyTitle"
        :description="emptyDescription"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useAcademicStore } from '../stores/academic'
import EmptyState from '../components/EmptyState.vue'

const academic = useAcademicStore()
const { grades, loading, errors } = storeToRefs(academic)

const gradeList = computed(() => grades.value?.grades || [])
const summary = computed(() => grades.value?.summary || null)
const emptyTitle = computed(() => (errors.value.grades ? '成绩暂不可用' : '暂无成绩'))
const emptyDescription = computed(
  () => errors.value.grades || '该学期还没有成绩记录。'
)

onMounted(async () => {
  await academic.initTerms()
  await academic.loadGrades()
})
</script>

<style scoped>
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
</style>
