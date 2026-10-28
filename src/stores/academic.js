import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '../api'

const EMPTY_ERRORS = {
  terms: '',
  schedule: '',
  grades: '',
  exams: '',
  evaluations: '',
  courseSelection: '',
  portalNotices: '',
  portalNoticeList: '',
  messages: ''
}

export const useAcademicStore = defineStore('academic', () => {
  const terms = ref([])
  const currentTerm = ref(null)
  const schedule = ref(null)
  const grades = ref(null)
  const exams = ref([])
  const evaluations = ref(null)
  const courseSelection = ref(null)
  const portalNotices = ref([])
  const portalNoticeList = ref(null)
  const messages = ref(null)
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
    // 切换学期时先清掉旧学期数据，避免加载失败后仍显示上一学期的成绩
    if (
      term
      && grades.value?.term
      && (String(term.xnm) !== String(grades.value.term.xnm)
        || String(term.xqm) !== String(grades.value.term.xqm))
    ) {
      grades.value = null
    }
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

  async function loadAllGrades(termList = terms.value) {
    loading.value = true
    setError('grades', '')
    grades.value = null
    try {
      grades.value = await api.getAllGrades(termList)
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

  async function loadEvaluations() {
    loading.value = true
    setError('evaluations', '')
    try {
      evaluations.value = await api.getEvaluations()
    } catch (err) {
      setError('evaluations', err.message || '评教记录加载失败')
    } finally {
      loading.value = false
    }
  }

  async function loadCourseSelection() {
    loading.value = true
    setError('courseSelection', '')
    try {
      courseSelection.value = await api.getCourseSelectionStatus()
    } catch (err) {
      setError('courseSelection', err.message || '选课状态加载失败')
    } finally {
      loading.value = false
    }
  }

  async function loadPortalNotices() {
    setError('portalNotices', '')
    try {
      portalNotices.value = await api.getPortalNotices()
    } catch (err) {
      setError('portalNotices', err.message || '教务通知加载失败')
    }
  }

  async function loadPortalNoticeList() {
    loading.value = true
    setError('portalNoticeList', '')
    try {
      portalNoticeList.value = await api.getPortalNoticeList()
    } catch (err) {
      setError('portalNoticeList', err.message || '教务通知加载失败')
    } finally {
      loading.value = false
    }
  }

  async function loadMessages() {
    loading.value = true
    setError('messages', '')
    try {
      messages.value = await api.getMessages()
    } catch (err) {
      setError('messages', err.message || '消息加载失败')
    } finally {
      loading.value = false
    }
  }

  function reset() {
    terms.value = []
    currentTerm.value = null
    schedule.value = null
    grades.value = null
    exams.value = []
    evaluations.value = null
    courseSelection.value = null
    portalNotices.value = []
    portalNoticeList.value = null
    messages.value = null
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
    evaluations,
    courseSelection,
    portalNotices,
    portalNoticeList,
    messages,
    course,
    loading,
    errors,
    initTerms,
    loadSchedule,
    loadGrades,
    loadAllGrades,
    loadExams,
    loadEvaluations,
    loadCourseSelection,
    loadPortalNotices,
    loadPortalNoticeList,
    loadMessages,
    loadCourse,
    reset
  }
})
