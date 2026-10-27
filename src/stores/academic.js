import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '../api'

export const useAcademicStore = defineStore('academic', () => {
  const terms = ref([])
  const currentTerm = ref(null)
  const schedule = ref(null)
  const grades = ref(null)
  const exams = ref([])
  const course = ref(null)
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

  async function loadCourse(id) {
    course.value = null
    course.value = await api.getCourse(id)
  }

  return {
    terms,
    currentTerm,
    schedule,
    grades,
    exams,
    course,
    loading,
    error,
    initTerms,
    loadSchedule,
    loadGrades,
    loadExams,
    loadCourse
  }
})
