<template>
  <div class="page schedule-page">
    <header class="row">
      <div>
        <h1 class="page-title">课表</h1>
        <p class="tiny">{{ currentTerm?.label }}</p>
      </div>
      <button v-if="selectedWeek !== currentWeek" class="back-week" @click="selectedWeek = currentWeek">
        回到本周
      </button>
    </header>

    <div class="week-bar card">
      <button class="week-btn" aria-label="上一周" @click="changeWeek(-1)">
        <AppIcon name="chevron-left" />
      </button>
      <div class="week-center">
        <span class="week-num">第 {{ selectedWeek }} 周</span>
        <span class="tiny">{{ weekRange }}</span>
      </div>
      <button class="week-btn" aria-label="下一周" @click="changeWeek(1)">
        <AppIcon name="chevron-right" />
      </button>
    </div>

    <div v-if="academic.loading && !schedule" class="card loading">正在加载课表…</div>

    <div v-else class="timetable-wrap card">
      <div class="timetable">
        <div class="corner"></div>
        <div
          v-for="(label, index) in weekdayLabels"
          :key="label"
          class="day-head"
          :class="{ 'is-today': index + 1 === todayWeekday && selectedWeek === currentWeek }"
        >
          <span class="day-name">{{ label }}</span>
          <span class="day-date">{{ dayDates[index] }}</span>
        </div>

        <template v-for="section in 10" :key="`t${section}`">
          <div class="time-cell" :style="{ gridRow: section + 1 }">
            <span class="section-num">{{ section }}</span>
            <span class="section-time">{{ SECTION_TIMES[section].start }}</span>
          </div>
        </template>

        <button
          v-for="item in visibleSessions"
          :key="item.courseId + '-' + item.weekday + '-' + item.startSection"
          class="course-block"
          :style="{
            gridColumn: item.weekday + 1,
            gridRow: `${item.startSection + 1} / span ${item.endSection - item.startSection + 1}`,
            backgroundColor: courseColor(item.courseId)
          }"
          @click="openCourse(item.courseId)"
        >
          <span class="block-name">{{ courseName(item.courseId) }}</span>
          <span class="block-room">{{ item.room }}</span>
        </button>
      </div>
    </div>

    <p class="tiny legend">
      左右滑动查看周末 · 点击课程查看详情 ·
      {{ schedule?.source ? '真实数据（网页导入）' : '模拟数据' }}
    </p>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useAcademicStore } from '../stores/academic'
import { SECTION_TIMES, WEEKDAY_LABELS, formatDate, getWeekNumber, getWeekday } from '../utils/date'
import AppIcon from '../components/AppIcon.vue'

const academic = useAcademicStore()
const router = useRouter()
const { schedule, currentTerm, loading } = storeToRefs(academic)

const weekdayLabels = WEEKDAY_LABELS.map((item) => `周${item}`)
const todayWeekday = getWeekday()
const currentWeek = computed(() =>
  currentTerm.value ? Math.min(getWeekNumber(currentTerm.value.startDate), 20) : 1
)
const selectedWeek = ref(1)

const weekRange = computed(() => {
  if (!currentTerm.value) return ''
  const start = new Date(currentTerm.value.startDate)
  start.setDate(start.getDate() + (selectedWeek.value - 1) * 7)
  const end = new Date(start)
  end.setDate(end.getDate() + 6)
  return `${start.getMonth() + 1}/${start.getDate()} - ${end.getMonth() + 1}/${end.getDate()}`
})

const dayDates = computed(() => {
  if (!currentTerm.value) return weekdayLabels.map(() => '')
  const base = new Date(currentTerm.value.startDate)
  base.setDate(base.getDate() + (selectedWeek.value - 1) * 7)
  return weekdayLabels.map((_, index) => {
    const d = new Date(base)
    d.setDate(d.getDate() + index)
    return formatDate(d).replace(/\s/g, '')
  })
})

const visibleSessions = computed(() => {
  const sessions = schedule.value?.sessions || []
  return sessions.filter((item) => item.weeks.includes(selectedWeek.value))
})

function courseName(courseId) {
  const course = schedule.value?.courses?.find((item) => item.id === courseId)
  return course ? course.name : '课程'
}

function courseColor(courseId) {
  const course = schedule.value?.courses?.find((item) => item.id === courseId)
  return course?.color || '#1B5FAA'
}

function changeWeek(step) {
  const next = selectedWeek.value + step
  if (next < 1 || next > 20) return
  selectedWeek.value = next
}

function openCourse(id) {
  router.push(`/course/${id}?week=${selectedWeek.value}`)
}

onMounted(async () => {
  await academic.initTerms()
  if (!schedule.value) await academic.loadSchedule()
  selectedWeek.value = currentWeek.value
})
</script>

<style scoped>
.schedule-page {
  padding-left: 12px;
  padding-right: 12px;
}

.back-week {
  font-size: 13px;
  color: var(--brand);
  padding: 6px 10px;
  border-radius: 8px;
  background: var(--brand-soft);
}

.week-bar {
  margin-top: 14px;
  padding: 8px 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.week-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
}

.week-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
}

.week-num {
  font-size: 15px;
  font-weight: 600;
}

.loading {
  margin-top: 12px;
  text-align: center;
  color: var(--text-secondary);
}

.timetable-wrap {
  margin-top: 12px;
  padding: 8px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.timetable {
  display: grid;
  grid-template-columns: 40px repeat(7, 72px);
  grid-template-rows: 44px repeat(10, 56px);
  gap: 3px;
  min-width: 564px;
  position: relative;
}

.corner {
  grid-column: 1;
  grid-row: 1;
}

.day-head {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  border-radius: 8px;
}

.day-head.is-today {
  background: var(--brand-soft);
  color: var(--brand);
}

.day-name {
  font-size: 12px;
  font-weight: 600;
}

.day-date {
  font-size: 10px;
  color: var(--text-tertiary);
}

.day-head.is-today .day-date {
  color: var(--brand);
}

.time-cell {
  grid-column: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  background: var(--card-muted);
  border-radius: 6px;
}

.section-num {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.section-time {
  font-size: 9px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

.course-block {
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 2px;
  padding: 5px 4px;
  border-radius: 8px;
  color: #fff;
  overflow: hidden;
  text-align: left;
}

.block-name {
  font-size: 11px;
  line-height: 1.25;
  font-weight: 600;
}

.block-room {
  font-size: 9px;
  opacity: 0.85;
}

.legend {
  margin-top: 10px;
  text-align: center;
}
</style>
