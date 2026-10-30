import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '../api'
import { useChangesStore } from './changes'
import { PORTAL_NOTICE_TTL, readPortalCache, writePortalCache } from '../utils/portalCache'
import {
  COURSE_SELECTION_TTL,
  EVALUATIONS_TTL,
  GRADES_TTL,
  MESSAGES_TTL,
  readDataCache,
  writeDataCache
} from '../utils/dataCache'

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

function sameTerm(a, b) {
  if (!a || !b) return false
  return String(a.xnm) === String(b.xnm) && String(a.xqm) === String(b.xqm)
}

function scheduleKey(term, force) {
  const xnm = term?.xnm || ''
  const xqm = term?.xqm || ''
  return `${xnm}_${xqm}|${force ? 'force' : 'soft'}`
}

function gradeCacheKey(term) {
  return term ? `grades.${term.xnm}_${term.xqm}` : 'grades.current'
}

export const useAcademicStore = defineStore('academic', () => {
  const changes = useChangesStore()
  const terms = ref([])
  const currentTerm = ref(null)
  const schedule = ref(null)
  const grades = ref(null)
  const exams = ref([])
  const evaluations = ref(null)
  const courseSelection = ref(null)
  const portalNotices = ref(null)
  const portalNoticeList = ref(null)
  const messages = ref(null)

  // 各业务域独立的 loading，避免课表请求结束时把成绩/评教的加载态提前关掉
  const termsLoading = ref(false)
  const scheduleLoading = ref(false)
  const gradesLoading = ref(false)
  const examsLoading = ref(false)
  const evaluationsLoading = ref(false)
  const courseSelectionLoading = ref(false)
  const portalNoticesLoading = ref(false)
  const portalNoticeListLoading = ref(false)
  const messagesLoading = ref(false)

  const portalNoticesOffline = ref(false)
  const portalNoticesCachedAt = ref('')
  const portalNoticesExpired = ref(false)
  const portalNoticeListOffline = ref(false)
  const portalNoticeListCachedAt = ref('')
  const portalNoticeListExpired = ref(false)
  const messagesOffline = ref(false)
  const messagesCachedAt = ref('')
  const messagesExpired = ref(false)
  const gradesOffline = ref(false)
  const gradesCachedAt = ref('')
  const gradesExpired = ref(false)
  const evaluationsOffline = ref(false)
  const evaluationsCachedAt = ref('')
  const evaluationsExpired = ref(false)
  const courseSelectionOffline = ref(false)
  const courseSelectionCachedAt = ref('')
  const courseSelectionExpired = ref(false)

  const course = ref(null)
  const errors = ref({ ...EMPTY_ERRORS })

  let scheduleRequest = null
  let scheduleRequestKey = ''
  let scheduleToken = 0
  let gradesToken = 0

  function setError(key, message) {
    errors.value = { ...errors.value, [key]: message }
  }

  async function initTerms() {
    if (terms.value.length) return
    setError('terms', '')
    termsLoading.value = true
    try {
      terms.value = await api.getTerms()
    } catch (err) {
      setError('terms', err.message || '学期信息加载失败')
      return
    } finally {
      termsLoading.value = false
    }
    currentTerm.value =
      terms.value.find((item) => item.isCurrent) || terms.value[0] || null
  }

  async function loadSchedule(term = currentTerm.value, options = {}) {
    const force = options.force !== false
    const key = scheduleKey(term, force)
    // 同学期同刷新策略的并发请求合并；不同学期的请求各自执行，旧响应由 token 丢弃
    if (scheduleRequest && scheduleRequestKey === key) return scheduleRequest

    const token = ++scheduleToken
    scheduleRequestKey = key
    scheduleLoading.value = true
    setError('schedule', '')
    const request = (async () => {
      try {
        // 默认强制刷新：打开 / 切回 App、进入课表时对一次账
        const data = await api.getSchedule(term, { force })
        if (token !== scheduleToken) return undefined
        schedule.value = data
        if (data?.term) currentTerm.value = data.term
        changes.applyFromSchedule(data)
        return data
      } catch (err) {
        if (token !== scheduleToken) return undefined
        setError('schedule', err.message || '课表加载失败')
        return undefined
      } finally {
        if (token === scheduleToken) {
          scheduleLoading.value = false
          scheduleRequest = null
          scheduleRequestKey = ''
        }
      }
    })()
    scheduleRequest = request
    return request
  }

  async function loadGrades(term = currentTerm.value) {
    const token = ++gradesToken
    const key = gradeCacheKey(term)
    const loaded = grades.value
    // 切换学期或从「全部学期」切回单学期时先清掉旧数据，避免旧响应/旧缓存串台
    if (
      term
      && loaded
      && (loaded.all || (loaded.term && !sameTerm(loaded.term, term)))
    ) {
      grades.value = null
      gradesOffline.value = false
      gradesCachedAt.value = ''
      gradesExpired.value = false
    }

    const cached = readDataCache(key, GRADES_TTL)
    if (!grades.value && cached?.data) {
      grades.value = cached.data
      gradesCachedAt.value = cached.cachedAt
      gradesExpired.value = cached.expired
    }

    gradesLoading.value = true
    setError('grades', '')
    try {
      const data = await api.getGrades(term)
      if (token !== gradesToken) return
      grades.value = data
      gradesOffline.value = false
      gradesExpired.value = false
      const cacheTime = writeDataCache(key, data)
      gradesCachedAt.value = data.fetchedAt || cacheTime
    } catch (err) {
      if (token !== gradesToken) return
      if (grades.value) {
        // 断网时保留缓存并标注来源，不把「有数据」误报成「暂无成绩」
        gradesOffline.value = true
        gradesExpired.value = Boolean(readDataCache(key, GRADES_TTL)?.expired)
      } else {
        setError('grades', err.message || '成绩加载失败')
      }
    } finally {
      if (token === gradesToken) gradesLoading.value = false
    }
  }

  async function loadAllGrades(termList = terms.value) {
    const token = ++gradesToken
    grades.value = null
    gradesOffline.value = false
    gradesCachedAt.value = ''
    gradesExpired.value = false

    const cacheKey = 'grades.all'
    const cached = readDataCache(cacheKey, GRADES_TTL)
    if (cached?.data) {
      grades.value = cached.data
      gradesCachedAt.value = cached.cachedAt
      gradesExpired.value = cached.expired
    }

    gradesLoading.value = true
    setError('grades', '')
    try {
      const data = await api.getAllGrades(termList)
      if (token !== gradesToken) return
      grades.value = data
      gradesOffline.value = false
      gradesExpired.value = false
      const cacheTime = writeDataCache(cacheKey, data)
      gradesCachedAt.value = data.fetchedAt || cacheTime
    } catch (err) {
      if (token !== gradesToken) return
      if (grades.value) {
        gradesOffline.value = true
        gradesExpired.value = Boolean(readDataCache(cacheKey, GRADES_TTL)?.expired)
      } else {
        setError('grades', err.message || '成绩加载失败')
      }
    } finally {
      if (token === gradesToken) gradesLoading.value = false
    }
  }

  async function loadExams(term = currentTerm.value) {
    examsLoading.value = true
    setError('exams', '')
    try {
      exams.value = await api.getExams(term)
    } catch (err) {
      setError('exams', err.message || '考试加载失败')
    } finally {
      examsLoading.value = false
    }
  }

  async function loadCourse(id) {
    course.value = null
    course.value = await api.getCourse(id)
  }

  async function loadEvaluations() {
    const cached = readDataCache('evaluations', EVALUATIONS_TTL)
    if (cached?.data) {
      evaluations.value = cached.data
      evaluationsCachedAt.value = cached.cachedAt
      evaluationsExpired.value = cached.expired
    }
    evaluationsLoading.value = true
    setError('evaluations', '')
    try {
      const data = await api.getEvaluations()
      evaluations.value = data
      evaluationsOffline.value = false
      evaluationsExpired.value = false
      const cacheTime = writeDataCache('evaluations', data)
      evaluationsCachedAt.value = data.fetchedAt || cacheTime
    } catch (err) {
      if (evaluations.value) {
        evaluationsOffline.value = true
        evaluationsExpired.value = Boolean(
          readDataCache('evaluations', EVALUATIONS_TTL)?.expired
        )
      } else {
        setError('evaluations', err.message || '评教记录加载失败')
      }
    } finally {
      evaluationsLoading.value = false
    }
  }

  async function loadCourseSelection() {
    const cached = readDataCache('courseSelection', COURSE_SELECTION_TTL)
    if (cached?.data) {
      courseSelection.value = cached.data
      courseSelectionCachedAt.value = cached.cachedAt
      courseSelectionExpired.value = cached.expired
    }
    courseSelectionLoading.value = true
    setError('courseSelection', '')
    try {
      const data = await api.getCourseSelectionStatus()
      courseSelection.value = data
      courseSelectionOffline.value = false
      courseSelectionExpired.value = false
      const cacheTime = writeDataCache('courseSelection', data)
      courseSelectionCachedAt.value = data.fetchedAt || cacheTime
    } catch (err) {
      if (courseSelection.value) {
        courseSelectionOffline.value = true
        courseSelectionExpired.value = Boolean(
          readDataCache('courseSelection', COURSE_SELECTION_TTL)?.expired
        )
      } else {
        setError('courseSelection', err.message || '选课状态加载失败')
      }
    } finally {
      courseSelectionLoading.value = false
    }
  }

  async function loadPortalNotices() {
    const cached = readPortalCache('notices.index', PORTAL_NOTICE_TTL)
    const cachedExpired = Boolean(cached?.expired)
    if (cached?.data) {
      portalNotices.value = cached.data
      portalNoticesCachedAt.value = cached.cachedAt
      portalNoticesExpired.value = cachedExpired
    }
    portalNoticesLoading.value = true
    setError('portalNotices', '')
    try {
      const data = await api.getPortalNotices()
      portalNotices.value = data
      portalNoticesOffline.value = false
      portalNoticesExpired.value = false
      portalNoticesCachedAt.value = data.fetchedAt || new Date().toISOString()
      writePortalCache('notices.index', data)
    } catch (err) {
      if (portalNotices.value) {
        portalNoticesOffline.value = true
        portalNoticesExpired.value = cachedExpired
      } else {
        setError('portalNotices', err.message || '教务通知加载失败')
      }
    } finally {
      portalNoticesLoading.value = false
    }
  }

  async function loadPortalNoticeList() {
    const cached = readPortalCache('notices.list', PORTAL_NOTICE_TTL)
    const cachedExpired = Boolean(cached?.expired)
    if (cached?.data) {
      portalNoticeList.value = cached.data
      portalNoticeListCachedAt.value = cached.cachedAt
      portalNoticeListExpired.value = cachedExpired
    }
    portalNoticeListLoading.value = true
    setError('portalNoticeList', '')
    try {
      const data = await api.getPortalNoticeList()
      portalNoticeList.value = data
      portalNoticeListOffline.value = false
      portalNoticeListExpired.value = false
      portalNoticeListCachedAt.value = data.fetchedAt || new Date().toISOString()
      writePortalCache('notices.list', data)
    } catch (err) {
      if (portalNoticeList.value) {
        portalNoticeListOffline.value = true
        portalNoticeListExpired.value = cachedExpired
      } else {
        setError('portalNoticeList', err.message || '教务通知加载失败')
      }
    } finally {
      portalNoticeListLoading.value = false
    }
  }

  async function loadMessages() {
    const cached = readDataCache('messages', MESSAGES_TTL)
    if (cached?.data) {
      messages.value = cached.data
      messagesCachedAt.value = cached.cachedAt
      messagesExpired.value = cached.expired
    }
    messagesLoading.value = true
    setError('messages', '')
    try {
      const data = await api.getMessages()
      messages.value = data
      messagesOffline.value = false
      messagesExpired.value = false
      const cacheTime = writeDataCache('messages', data)
      messagesCachedAt.value = data.fetchedAt || cacheTime
    } catch (err) {
      if (messages.value) {
        messagesOffline.value = true
        messagesExpired.value = Boolean(readDataCache('messages', MESSAGES_TTL)?.expired)
      } else {
        setError('messages', err.message || '消息加载失败')
      }
    } finally {
      messagesLoading.value = false
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
    portalNotices.value = null
    portalNoticeList.value = null
    messages.value = null
    termsLoading.value = false
    scheduleLoading.value = false
    gradesLoading.value = false
    examsLoading.value = false
    evaluationsLoading.value = false
    courseSelectionLoading.value = false
    portalNoticesLoading.value = false
    portalNoticeListLoading.value = false
    messagesLoading.value = false
    portalNoticesOffline.value = false
    portalNoticesCachedAt.value = ''
    portalNoticesExpired.value = false
    portalNoticeListOffline.value = false
    portalNoticeListCachedAt.value = ''
    portalNoticeListExpired.value = false
    messagesOffline.value = false
    messagesCachedAt.value = ''
    messagesExpired.value = false
    gradesOffline.value = false
    gradesCachedAt.value = ''
    gradesExpired.value = false
    evaluationsOffline.value = false
    evaluationsCachedAt.value = ''
    evaluationsExpired.value = false
    courseSelectionOffline.value = false
    courseSelectionCachedAt.value = ''
    courseSelectionExpired.value = false
    course.value = null
    scheduleRequest = null
    scheduleRequestKey = ''
    scheduleToken += 1
    gradesToken += 1
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
    termsLoading,
    scheduleLoading,
    gradesLoading,
    examsLoading,
    evaluationsLoading,
    courseSelectionLoading,
    portalNoticesLoading,
    portalNoticeListLoading,
    messagesLoading,
    portalNoticesOffline,
    portalNoticesCachedAt,
    portalNoticesExpired,
    portalNoticeListOffline,
    portalNoticeListCachedAt,
    portalNoticeListExpired,
    messagesOffline,
    messagesCachedAt,
    messagesExpired,
    gradesOffline,
    gradesCachedAt,
    gradesExpired,
    evaluationsOffline,
    evaluationsCachedAt,
    evaluationsExpired,
    courseSelectionOffline,
    courseSelectionCachedAt,
    courseSelectionExpired,
    course,
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
