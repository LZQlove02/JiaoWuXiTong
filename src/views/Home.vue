<template>
  <div class="page">
    <header class="row header">
      <div>
        <p class="tiny">{{ greetingText }}，{{ auth.user?.name || '同学' }}</p>
        <div class="title-line">
          <h1 class="page-title">柳职教务</h1>
          <span v-if="isOffline" class="pill pill--warning">课表离线缓存</span>
        </div>
        <p class="tiny">{{ currentTerm?.label }} · {{ weekLabel }}</p>
      </div>
      <router-link to="/profile" class="avatar">{{ avatarText }}</router-link>
    </header>

    <section class="card today-card">
      <div class="row">
        <h2 class="card-title">今日课程</h2>
        <router-link to="/schedule" class="link">查看课表</router-link>
      </div>
      <div v-if="academic.loading && !schedule" class="loading">正在加载今日课程…</div>
      <div v-else-if="todayCourses.length" class="today-list">
        <div v-for="item in todayCourses" :key="item.courseId + item.startSection" class="course-row">
          <span class="course-time">{{ sectionRange(item.startSection, item.endSection) }}</span>
          <div class="course-main">
            <p class="course-name">{{ courseName(item.courseId) }}</p>
            <p class="tiny">{{ item.room }}</p>
          </div>
        </div>
      </div>
      <EmptyState
        v-else-if="!hasSchedule"
        icon="calendar"
        :title="errors.schedule ? '课表加载失败' : '课表暂无课程'"
        :description="errors.schedule || '课表同步后会显示在这里。'"
        :action-label="errors.schedule ? '重试' : ''"
        @action="academic.loadSchedule()"
      />
      <EmptyState v-else icon="calendar" title="今天没有课" description="好好休息，或者去图书馆看看。" />
    </section>

    <section v-if="nextExam" class="card exam-card">
      <div class="row">
        <div>
          <p class="tiny">最近考试</p>
          <p class="exam-name">{{ nextExam.courseName }}</p>
          <p class="tiny">{{ nextExam.date }} {{ nextExam.startTime }} · {{ nextExam.room }}</p>
        </div>
        <div class="countdown">
          <span class="countdown-num">{{ nextExam.days }}</span>
          <span class="tiny">天后</span>
        </div>
      </div>
    </section>

    <h2 class="section-title">快捷入口</h2>
    <div class="grid">
      <button v-for="entry in entries" :key="entry.path" class="entry card" @click="go(entry.path)">
        <span class="entry-icon" :style="{ color: entry.color }">
          <AppIcon :name="entry.icon" />
        </span>
        <span class="entry-label">{{ entry.label }}</span>
        <span class="tiny">{{ entry.desc }}</span>
      </button>
    </div>

    <section class="card notice-card">
      <div class="row">
        <h2 class="card-title">最新通知</h2>
        <router-link to="/notices" class="link">全部</router-link>
      </div>
      <p class="tiny source">
        来源：教务处官网<template v-if="noticesOffline">（网络不可用，显示缓存）</template>
        <template v-else-if="noticesUpdated">· 更新于 {{ noticesUpdated }}</template>
      </p>
      <button
        v-for="notice in latestNotices"
        :key="notice.id"
        class="notice-row"
        @click="go(`/notices/${notice.id}`)"
      >
        <span class="notice-title">{{ notice.title }}</span>
        <span class="tiny">{{ notice.date }}</span>
      </button>
      <p v-if="noticesError" class="tiny notice-error">{{ noticesError }}</p>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useAcademicStore } from '../stores/academic'
import { usePublicList } from '../composables/usePublicContent'
import { daysUntil, formatDate, getWeekNumber, getWeekday, greeting, sectionRange } from '../utils/date'
import AppIcon from '../components/AppIcon.vue'
import EmptyState from '../components/EmptyState.vue'

const auth = useAuthStore()
const academic = useAcademicStore()
const router = useRouter()
const { schedule, exams, currentTerm, errors } = storeToRefs(academic)
const isOffline = computed(() => Boolean(schedule.value?.source?.includes('离线')))
const hasSchedule = computed(() => Boolean(schedule.value?.sessions?.length))

const {
  items: publicNotices,
  offline: noticesOffline,
  error: noticesError,
  updatedLabel: noticesUpdated,
  hydrate: hydrateNotices,
  load: loadNotices
} = usePublicList('tzgg')

const now = ref(new Date())
let clockTimer = null
const greetingText = computed(() => greeting(now.value.getHours()))
const weekday = computed(() => getWeekday(now.value))

const entries = [
  { path: '/exams', label: '考试', desc: '考试安排', icon: 'exam', color: '#C0392B' },
  { path: '/notices', label: '通知', desc: '教务公告', icon: 'notice', color: '#1B5FAA' },
  { path: '/courses', label: '选课', desc: '课程选择', icon: 'select', color: '#B7791F' },
  { path: '/evaluation', label: '评教', desc: '教学评价', icon: 'evaluate', color: '#8E44AD' },
  { path: '/calendar', label: '校历', desc: '学期安排', icon: 'calendar', color: '#0E7490' },
  { path: '/guide', label: '办事指南', desc: '流程说明', icon: 'guide', color: '#2E8B57' }
]

const avatarText = computed(() => (auth.user?.name || '我').slice(-1))

const week = computed(() =>
  currentTerm.value ? getWeekNumber(currentTerm.value.startDate, now.value) : 1
)

const weekLabel = computed(() => {
  const date = formatDate(now.value)
  return `${date} 周${['一', '二', '三', '四', '五', '六', '日'][weekday.value - 1]} · 第 ${week.value} 周`
})

const todayCourses = computed(() => {
  const sessions = schedule.value?.sessions || []
  return sessions
    .filter(
      (item) => item.weekday === weekday.value && item.weeks.includes(week.value)
    )
    .sort((a, b) => a.startSection - b.startSection)
})

function courseName(courseId) {
  const course = schedule.value?.courses?.find((item) => item.id === courseId)
  return course ? course.name : '课程'
}

const nextExam = computed(() => {
  const list = (exams.value || [])
    .map((item) => ({ ...item, days: daysUntil(item.date) }))
    .filter((item) => item.days >= 0)
    .sort((a, b) => a.days - b.days)
  return list[0] || null
})

const latestNotices = computed(() => publicNotices.value.slice(0, 3))

function go(path) {
  router.push(path)
}

onMounted(async () => {
  clockTimer = setInterval(() => {
    now.value = new Date()
  }, 60 * 1000)
  await academic.initTerms()
  academic.loadSchedule()
  academic.loadExams()
  hydrateNotices()
  await loadNotices()
})

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<style scoped>
.header {
  margin-bottom: 16px;
}

.title-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.link {
  font-size: 13px;
  color: var(--brand);
}

.today-card {
  margin-bottom: 12px;
}

.today-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.loading {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--text-secondary);
}

.course-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.course-time {
  flex: none;
  width: 88px;
  font-size: 12px;
  color: var(--brand);
  font-variant-numeric: tabular-nums;
  padding-top: 2px;
}

.course-main {
  flex: 1;
}

.course-name {
  font-size: 15px;
  font-weight: 500;
}

.exam-card {
  margin-bottom: 4px;
}

.exam-name {
  font-size: 16px;
  font-weight: 600;
  margin: 2px 0;
}

.countdown {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  color: var(--danger);
}

.countdown-num {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.entry {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 14px 12px;
  text-align: left;
}

.entry-icon {
  margin-bottom: 4px;
}

.entry-label {
  font-size: 14px;
  font-weight: 600;
}

.notice-card {
  margin-top: 16px;
}

.notice-row {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
  text-align: left;
}

.notice-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.notice-title {
  font-size: 14px;
  line-height: 1.45;
}

.source {
  margin-top: 2px;
  margin-bottom: 4px;
}

.notice-error {
  margin-top: 8px;
  color: var(--danger);
}
</style>
