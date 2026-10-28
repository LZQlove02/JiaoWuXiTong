<template>
  <div class="page page--plain">
    <BackBar title="考试安排" />

    <div v-if="loading && !exams.length" class="card loading">正在加载考试…</div>

    <div v-else class="exam-list">
      <article v-for="item in examList" :key="item.id" class="card exam-item">
        <div class="date-block">
          <span class="date-month">{{ monthOf(item) }}</span>
          <span class="date-day">{{ dayOf(item) }}</span>
        </div>
        <div class="exam-body">
          <div class="row">
            <p class="exam-name">{{ item.courseName }}</p>
            <span class="days-tag" :class="{ soon: item.days >= 0 && item.days <= 7, over: item.days < 0 }">
              {{ countdownText(item.days) }}
            </span>
          </div>
          <p class="tiny">{{ item.type }} · {{ item.date }}</p>
          <p class="meta">
            <span class="meta-item"><AppIcon name="clock" />{{ item.startTime }}-{{ item.endTime }}</span>
            <span class="meta-item"><AppIcon name="pin" />{{ item.room }} · {{ item.seat }} 号</span>
          </p>
        </div>
      </article>
      <EmptyState v-if="!examList.length" title="暂无考试安排" description="考试安排发布后会显示在这里。" />
    </div>

    <p v-if="errors.exams" class="tiny footnote">{{ errors.exams }}</p>
    <p v-else class="tiny footnote">
      {{
        exams.source
          ? '真实数据（网页导入）'
          : '模拟数据，真实安排以教务处通知为准。'
      }}
    </p>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useAcademicStore } from '../stores/academic'
import { daysUntil } from '../utils/date'
import BackBar from '../components/BackBar.vue'
import AppIcon from '../components/AppIcon.vue'
import EmptyState from '../components/EmptyState.vue'

const academic = useAcademicStore()
const { exams, loading, errors } = storeToRefs(academic)

const examList = computed(() =>
  exams.value
    .map((item) => {
      const days = daysUntil(item.date)
      return { ...item, days, dated: !Number.isNaN(days) }
    })
    .sort((a, b) => {
      if (a.dated !== b.dated) return a.dated ? -1 : 1
      return a.dated ? a.days - b.days : 0
    })
)

function monthOf(item) {
  return item.dated ? `${Number(item.date.slice(5, 7))} 月` : '日期'
}

function dayOf(item) {
  return item.dated ? item.date.slice(8, 10) : '待定'
}

function countdownText(days) {
  if (Number.isNaN(days)) return '待定'
  if (days < 0) return '已结束'
  if (days === 0) return '今天'
  if (days === 1) return '明天'
  return `${days} 天后`
}

onMounted(async () => {
  await academic.initTerms()
  if (!exams.value.length) await academic.loadExams()
})
</script>

<style scoped>
.loading {
  text-align: center;
  color: var(--text-secondary);
}

.exam-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.exam-item {
  display: flex;
  gap: 14px;
}

.date-block {
  flex: none;
  width: 54px;
  height: 58px;
  border-radius: 10px;
  background: var(--brand-soft);
  color: var(--brand);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.date-month {
  font-size: 11px;
}

.date-day {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.exam-body {
  flex: 1;
  min-width: 0;
}

.exam-name {
  font-size: 16px;
  font-weight: 600;
}

.days-tag {
  flex: none;
  font-size: 12px;
  color: var(--text-secondary);
}

.days-tag.soon {
  color: var(--danger);
  font-weight: 600;
}

.days-tag.over {
  color: var(--text-tertiary);
}

.meta {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.footnote {
  margin-top: 16px;
  text-align: center;
}
</style>
