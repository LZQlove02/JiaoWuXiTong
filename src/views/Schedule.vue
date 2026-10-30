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

    <div v-if="hasSchedule" class="week-bar card">
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

    <div v-if="scheduleLoading && !schedule" class="card loading">正在加载课表…</div>

    <div v-else-if="!hasSchedule" class="card empty-card">
      <EmptyState
        icon="calendar"
        :title="emptyTitle"
        :description="emptyDescription"
        :action-label="emptyActionLabel"
        @action="emptyAction"
      />
    </div>

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

        <template v-for="section in 12" :key="`t${section}`">
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
          <span
            v-if="changeMarkOf(item)"
            class="change-badge"
            :class="`change-badge--${changeMarkOf(item)}`"
          >{{ changeMarkLabel(changeMarkOf(item)) }}</span>
          <span class="block-name">{{ courseName(item.courseId) }}</span>
          <span class="block-room">{{ item.room }}</span>
        </button>

        <button
          v-for="entry in changeGhosts"
          :key="'ghost-' + entry.id"
          class="course-block ghost"
          :style="{
            gridColumn: entry.from.weekday + 1,
            gridRow: `${entry.from.startSection + 1} / span ${entry.from.endSection - entry.from.startSection + 1}`
          }"
          @click="openCourse(entry.courseId)"
        >
          <span class="change-badge change-badge--cancel">停</span>
          <span class="block-name">{{ entry.courseName }}</span>
          <span class="block-room">{{ entry.from.room }}</span>
        </button>
      </div>
    </div>

    <p v-if="errors.schedule && hasSchedule" class="load-error">
      {{ errors.schedule }}
      <button class="retry" @click="reloadSchedule">重试</button>
    </p>

    <p v-if="hasSchedule" class="tiny legend">
      左右滑动查看周末 · 点击课程查看详情
    </p>
    <p v-if="hasChangeMarks" class="tiny legend change-legend">
      本周变化已标出：停课 · 调课 · 新增
    </p>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useAcademicStore } from '../stores/academic'
import { useChangesStore } from '../stores/changes'
import { SECTION_TIMES, WEEKDAY_LABELS, formatDate, getWeekNumber, getWeekday } from '../utils/date'
import { buildChangeMarks, markOf } from '../utils/changeMarks'
import { changeMeta } from '../utils/changeLabels'
import AppIcon from '../components/AppIcon.vue'
import EmptyState from '../components/EmptyState.vue'

const academic = useAcademicStore()
const changes = useChangesStore()
const router = useRouter()
const { schedule, currentTerm, scheduleLoading, errors } = storeToRefs(academic)

const weekdayLabels = WEEKDAY_LABELS.map((item) => `周${item}`)
const now = ref(new Date())
let clockTimer = null
const todayWeekday = computed(() => getWeekday(now.value))
const currentWeek = computed(() =>
  currentTerm.value
    ? Math.min(Math.max(getWeekNumber(currentTerm.value.startDate, now.value), 1), 20)
    : 1
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

const hasSchedule = computed(() => Boolean(schedule.value?.sessions?.length))

const changeMarks = computed(() => buildChangeMarks(changes.entries))
const showChangeMarks = computed(() => selectedWeek.value === currentWeek.value)
const occupiedSlots = computed(
  () => new Set(visibleSessions.value.map((item) => `${item.weekday}-${item.startSection}`))
)
const changeGhosts = computed(() => {
  if (!showChangeMarks.value) return []
  return changeMarks.value.ghosts.filter(
    (entry) => entry.from && !occupiedSlots.value.has(`${entry.from.weekday}-${entry.from.startSection}`)
  )
})
const hasChangeMarks = computed(
  () => showChangeMarks.value
    && (Object.keys(changeMarks.value.marks).length > 0 || changeGhosts.value.length > 0)
)

function changeMarkOf(item) {
  if (!showChangeMarks.value) return ''
  return markOf(changeMarks.value.marks, item)
}

function changeMarkLabel(type) {
  return changeMeta(type).mark
}

const emptyTitle = computed(() => (errors.value.schedule ? '课表加载失败' : '课表暂无课程'))

const emptyDescription = computed(
  () => errors.value.schedule || '课表同步后会显示在这里。'
)

const emptyActionLabel = computed(() => (errors.value.schedule ? '重试' : ''))

function emptyAction() {
  reloadSchedule()
}

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

function reloadSchedule() {
  academic.loadSchedule(currentTerm.value)
}

onMounted(async () => {
  clockTimer = setInterval(() => {
    now.value = new Date()
  }, 60 * 1000)
  await Promise.all([academic.initTerms(), changes.init()])
  if (!schedule.value) await academic.loadSchedule(currentTerm.value)
  selectedWeek.value = currentWeek.value
})

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
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
  grid-template-rows: 44px repeat(12, 56px);
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
  position: relative;
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

.change-badge {
  position: absolute;
  top: 3px;
  right: 3px;
  padding: 1px 4px;
  border-radius: 4px;
  font-size: 9px;
  line-height: 1.2;
  font-weight: 700;
  background: rgba(0, 0, 0, 0.38);
  color: #fff;
}

.change-badge--cancel {
  background: rgba(0, 0, 0, 0.5);
}

.change-badge--move {
  background: rgba(183, 121, 31, 0.95);
}

.change-badge--add {
  background: rgba(30, 142, 90, 0.95);
}

/* 深色主题压暗一档，白字对比度更高 */
[data-theme='dark'] .change-badge--move {
  background: rgba(146, 96, 24, 0.95);
}

[data-theme='dark'] .change-badge--add {
  background: rgba(22, 110, 70, 0.95);
}

.course-block.ghost {
  background: transparent;
  border: 1px dashed var(--text-tertiary);
  color: var(--text-tertiary);
  opacity: 0.9;
}

.course-block.ghost .block-name {
  text-decoration: line-through;
}

.change-legend {
  margin-top: 2px;
}

.legend {
  margin-top: 10px;
  text-align: center;
}

.empty-card {
  margin-top: 12px;
}

.load-error {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: 13px;
  color: var(--danger);
}

.retry {
  padding: 4px 12px;
  border-radius: 14px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 13px;
}
</style>
