import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '../api'

export const useAcademicStore = defineStore('academic', () => {
  const terms = ref([])
  const currentTerm = ref(null)
  const schedule = ref(null)
  const grades = ref(null)
  const exams = ref([])
  const notices = ref([])
  const notice = ref(null)
  const course = ref(null)
  const evaluations = ref([])
  const calendar = ref([])
  const guides = ref([])
  const loading = ref(false)
  const error = ref('')

  async function initTerms() {
    if (terms.value.length) return
    terms.value = await api.getTerms()
    currentTerm.value =
      terms.value.find((item) => item.isCurrent) || terms.value[0] || null
  }

  async function loadSchedule(term = currentTerm.value) {
    loading.value = true
    error.value = ''
    try {
      schedule.value = await api.getSchedule(term)
    } catch (err) {
      error.value = err.message || '课表加载失败'
    } finally {
      loading.value = false
    }
  }

  async function loadGrades(term = currentTerm.value) {
    loading.value = true
    error.value = ''
    try {
      grades.value = await api.getGrades(term)
    } catch (err) {
      error.value = err.message || '成绩加载失败'
    } finally {
      loading.value = false
    }
  }

  async function loadExams(term = currentTerm.value) {
    loading.value = true
    error.value = ''
    try {
      exams.value = await api.getExams(term)
    } catch (err) {
      error.value = err.message || '考试加载失败'
    } finally {
      loading.value = false
    }
  }

  async function loadNotices() {
    loading.value = true
    error.value = ''
    try {
      notices.value = await api.getNotices()
    } catch (err) {
      error.value = err.message || '通知加载失败'
    } finally {
      loading.value = false
    }
  }

  async function loadNotice(id) {
    notice.value = null
    notice.value = await api.getNotice(id)
  }

  async function loadCourse(id) {
    course.value = null
    course.value = await api.getCourse(id)
  }

  async function loadEvaluations() {
    evaluations.value = await api.getEvaluations()
  }

  async function loadCalendar() {
    calendar.value = await api.getCalendar()
  }

  async function loadGuides() {
    guides.value = await api.getGuides()
  }

  return {
    terms,
    currentTerm,
    schedule,
    grades,
    exams,
    notices,
    notice,
    course,
    evaluations,
    calendar,
    guides,
    loading,
    error,
    initTerms,
    loadSchedule,
    loadGrades,
    loadExams,
    loadNotices,
    loadNotice,
    loadCourse,
    loadEvaluations,
    loadCalendar,
    loadGuides
  }
})
