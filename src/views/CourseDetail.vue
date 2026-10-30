<template>
  <div class="page page--plain">
    <BackBar title="课程详情" />

    <template v-if="course">
      <section class="card header-card" :style="{ borderTopColor: course.color }">
        <h1 class="course-name">{{ course.name }}</h1>
        <p class="tiny">{{ course.code }} · {{ course.teacher }}</p>
        <div class="chips">
          <span class="chip">{{ course.credits }} 学分</span>
          <span class="chip">{{ course.type }}</span>
        </div>
      </section>

      <div class="section-head">
        <h2 class="section-title">上课安排</h2>
        <span v-if="selectedWeek" class="week-hint">第 {{ selectedWeek }} 周</span>
      </div>
      <section class="card">
        <div
          v-for="(item, index) in sessionRows"
          :key="index"
          class="session-row"
          :class="{ 'is-current': item.inWeek }"
        >
          <span class="session-day">周{{ WEEKDAY_LABELS[item.weekday - 1] }}</span>
          <div class="session-main">
            <p class="session-time">
              第 {{ item.startSection }}-{{ item.endSection }} 节
              <span class="tiny">（{{ sectionRange(item.startSection, item.endSection) }}）</span>
              <span v-if="item.inWeek" class="week-pill">本周</span>
            </p>
            <p class="tiny">{{ item.room }} · {{ formatWeeks(item.weeks) }}</p>
          </div>
        </div>
        <p v-if="selectedWeek && !hasCurrentWeekSession" class="tiny week-empty">
          第 {{ selectedWeek }} 周没有这门课的安排。
        </p>
        <EmptyState v-if="!course.sessions?.length" title="暂无排课信息" />
      </section>
    </template>

    <EmptyState v-else title="课程不存在" description="该课程可能已调整。" />
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { useAcademicStore } from '../stores/academic'
import { WEEKDAY_LABELS, sectionRange } from '../utils/date'
import BackBar from '../components/BackBar.vue'
import EmptyState from '../components/EmptyState.vue'

const route = useRoute()
const academic = useAcademicStore()
const { course } = storeToRefs(academic)

const selectedWeek = computed(() => {
  const value = Number(route.query.week)
  return Number.isFinite(value) && value > 0 ? value : null
})

const sessionRows = computed(() =>
  (course.value?.sessions || []).map((item) => ({
    ...item,
    inWeek: Boolean(
      selectedWeek.value
      && Array.isArray(item.weeks)
      && item.weeks.includes(selectedWeek.value)
    )
  }))
)

const hasCurrentWeekSession = computed(() =>
  sessionRows.value.some((item) => item.inWeek)
)

function formatWeeks(weeks) {
  if (!weeks || !weeks.length) return '周次待定'
  const isOdd = weeks.every((week) => week % 2 === 1)
  const isEven = weeks.every((week) => week % 2 === 0)
  const isContinuous =
    !isOdd && !isEven && weeks[weeks.length - 1] - weeks[0] === weeks.length - 1
  if (isContinuous) return `${weeks[0]}-${weeks[weeks.length - 1]} 周`
  if (isOdd) return `单周（${weeks[0]}-${weeks[weeks.length - 1]} 周）`
  if (isEven) return `双周（${weeks[0]}-${weeks[weeks.length - 1]} 周）`
  return `${weeks.length} 个教学周`
}

onMounted(() => {
  academic.loadCourse(route.params.id)
})
</script>

<style scoped>
.header-card {
  border-top: 4px solid var(--brand);
  border-radius: var(--radius);
}

.course-name {
  font-size: 21px;
  font-weight: 600;
  line-height: 1.4;
}

.chips {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.chip {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 20px;
  background: var(--card-muted);
  color: var(--text-secondary);
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.week-hint {
  font-size: 12px;
  color: var(--brand);
}

.session-row {
  display: flex;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}

.session-row.is-current {
  padding-left: 8px;
  padding-right: 8px;
  border-radius: 10px;
  background: var(--brand-soft);
}

.session-row:last-of-type {
  border-bottom: none;
}

.week-pill {
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 10px;
  background: var(--brand);
  color: #fff;
  font-size: 10px;
}

.week-empty {
  padding: 12px 0 4px;
}

.session-day {
  flex: none;
  width: 44px;
  font-size: 14px;
  font-weight: 600;
  color: var(--brand);
}

.session-main {
  flex: 1;
}

.session-time {
  font-size: 14px;
}
</style>
