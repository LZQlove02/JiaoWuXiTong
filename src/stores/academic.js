import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '../api'

const EMPTY_ERRORS = { terms: '', schedule: '', grades: '', exams: '' }

export const useAcademicStore = defineStore('academic', () => {
  const terms = ref([])
  const currentTerm = ref(null)
  const schedule = ref(null)
  const grades = ref(null)
  const exams = ref([])
  const course = ref(null)
  const loading = ref(false)
  const errors = ref({ ...EMPTY_ERRORS })

  function setError(key, message) {
    errors.value = { ...errors.value, [key]: message }
  }

  async function initTerms() {
    if (terms.value.length) return
    setError('terms', '')
    try {
      terms.value = await api.getTerms()
    } catch (err) {
      setError('terms', err.message || '学期信息加载失败')
      return
    }
    currentTerm.value =
      terms.value.find((item) => item.isCurrent) || terms.value[0] || null
  }

  async function loadSchedule(term = currentTerm.value) {
    loading.value = true
    setError('schedule', '')
    try {
      schedule.value = await api.getSchedule(term)
    } catch (err) {
      setError('schedule', err.message || '课表加载失败')
    } finally {
      loading.value = false
    }
  }

  async function loadGrades(term = currentTerm.value) {
    loading.value = true
    setError('grades', '')
    try {
      grades.value = await api.getGrades(term)
    } catch (err) {
      setError('grades', err.message || '成绩加载失败')
    } finally {
      loading.value = false
    }
  }

  async function loadExams(term = currentTerm.value) {
    loading.value = true
    setError('exams', '')
    try {
      exams.value = await api.getExams(term)
    } catch (err) {
      setError('exams', err.message || '考试加载失败')
    } finally {
      loading.value = false
    }
  }

  async function loadCourse(id) {
    course.value = null
    course.value = await api.getCourse(id)
  }

  function reset() {
    terms.value = []
    currentTerm.value = null
    schedule.value = null
    grades.value = null
    exams.value = []
    course.value = null
    loading.value = false
    errors.value = { ...EMPTY_ERRORS }
  }

  return {
    terms,
    currentTerm,
    schedule,
    grades,
    exams,
    course,
    loading,
    errors,
    initTerms,
    loadSchedule,
    loadGrades,
    loadExams,
    loadCourse,
    reset
  }
})
