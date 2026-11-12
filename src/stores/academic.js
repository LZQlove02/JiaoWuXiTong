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
  evaluations: '',
  courseSelection: '',
  portalNotices: '',
  portalNoticeList: '',
  messages: '',
  messagesAll: ''
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
  const evaluations = ref(null)
  const courseSelection = ref(null)
  const portalNotices = ref(null)
  const portalNoticeList = ref(null)
  const messages = ref(null)
  const messagesAll = ref(null)

  // 各业务域独立的 loading，避免课表请求结束时把成绩/评教的加载态提前关掉
  const termsLoading = ref(false)
  const scheduleLoading = ref(false)
  const gradesLoading = ref(false)
  const evaluationsLoading = ref(false)
  const courseSelectionLoading = ref(false)
  const portalNoticesLoading = ref(false)
  const portalNoticeListLoading = ref(false)
  const messagesLoading = ref(false)
  const messagesAllLoading = ref(false)

  const portalNoticesOffline = ref(false)
  const portalNoticesCachedAt = ref('')
  const portalNoticesExpired = ref(false)
  const portalNoticeListOffline = ref(false)
  const portalNoticeListCachedAt = ref('')
  const portalNoticeListExpired = ref(false)
  const messagesOffline = ref(false)
  const messagesCachedAt = ref('')
  const messagesExpired = ref(false)
  const messagesAllOffline = ref(false)
  const messagesAllCachedAt = ref('')
  const messagesAllExpired = ref(false)
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
  // 账号代次：reset() 时 +1。没有独立 token 的业务域（评教/选课/通知/消息/学期）
  // 用它丢弃上一个账号残留的响应，避免退出后旧数据落进新会话首帧。
  let dataEpoch = 0

  function setError(key, message) {
    errors.value = { ...errors.value, [key]: message }
  }

  async function loadTerms() {
    const epoch = dataEpoch
    setError('terms', '')
    termsLoading.value = true
    try {
      const list = await api.getTerms()
      if (epoch !== dataEpoch) return
      terms.value = list
      currentTerm.value = list.find((item) => item.isCurrent) || list[0] || null
    } catch (err) {
      if (epoch !== dataEpoch) return
      setError('terms', err.message || '学期信息加载失败')
    } finally {
      if (epoch === dataEpoch) termsLoading.value = false
    }
  }

  async function initTerms() {
    if (terms.value.length) return
    await loadTerms()
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
        // 显式忽略：对账失败已由队列内部兜底，这里不需要阻断课表展示
        void changes.applyFromSchedule(data)
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

  // 冷启动快照：把缓存课表先放进 store，首页首帧即可渲染，网络刷新随后覆盖
  function applySnapshotSchedule(data) {
    if (!data || !Array.isArray(data.sessions)) return
    schedule.value = data
    if (data.term) currentTerm.value = data.term
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

  async function loadCourse(id) {
    course.value = null
    course.value = await api.getCourse(id)
  }

  async function loadEvaluations() {
    const epoch = dataEpoch
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
      if (epoch !== dataEpoch) return
      evaluations.value = data
      evaluationsOffline.value = false
      evaluationsExpired.value = false
      const cacheTime = writeDataCache('evaluations', data)
      evaluationsCachedAt.value = data.fetchedAt || cacheTime
    } catch (err) {
      if (epoch !== dataEpoch) return
      if (evaluations.value) {
        evaluationsOffline.value = true
        evaluationsExpired.value = Boolean(
          readDataCache('evaluations', EVALUATIONS_TTL)?.expired
        )
      } else {
        setError('evaluations', err.message || '评教记录加载失败')
      }
    } finally {
      if (epoch === dataEpoch) evaluationsLoading.value = false
    }
  }

  async function loadCourseSelection() {
    const epoch = dataEpoch
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
      if (epoch !== dataEpoch) return
      courseSelection.value = data
      courseSelectionOffline.value = false
      courseSelectionExpired.value = false
      const cacheTime = writeDataCache('courseSelection', data)
      courseSelectionCachedAt.value = data.fetchedAt || cacheTime
    } catch (err) {
      if (epoch !== dataEpoch) return
      if (courseSelection.value) {
        courseSelectionOffline.value = true
        courseSelectionExpired.value = Boolean(
          readDataCache('courseSelection', COURSE_SELECTION_TTL)?.expired
        )
      } else {
        setError('courseSelection', err.message || '选课状态加载失败')
      }
    } finally {
      if (epoch === dataEpoch) courseSelectionLoading.value = false
    }
  }

  async function loadPortalNotices() {
    const epoch = dataEpoch
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
      if (epoch !== dataEpoch) return
      portalNotices.value = data
      portalNoticesOffline.value = false
      portalNoticesExpired.value = false
      portalNoticesCachedAt.value = data.fetchedAt || new Date().toISOString()
      writePortalCache('notices.index', data)
    } catch (err) {
      if (epoch !== dataEpoch) return
      if (portalNotices.value) {
        portalNoticesOffline.value = true
        portalNoticesExpired.value = cachedExpired
      } else {
        setError('portalNotices', err.message || '教务通知加载失败')
      }
    } finally {
      if (epoch === dataEpoch) portalNoticesLoading.value = false
    }
  }

  async function loadPortalNoticeList() {
    const epoch = dataEpoch
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
      if (epoch !== dataEpoch) return
      portalNoticeList.value = data
      portalNoticeListOffline.value = false
      portalNoticeListExpired.value = false
      portalNoticeListCachedAt.value = data.fetchedAt || new Date().toISOString()
      writePortalCache('notices.list', data)
    } catch (err) {
      if (epoch !== dataEpoch) return
      if (portalNoticeList.value) {
        portalNoticeListOffline.value = true
        portalNoticeListExpired.value = cachedExpired
      } else {
        setError('portalNoticeList', err.message || '教务通知加载失败')
      }
    } finally {
      if (epoch === dataEpoch) portalNoticeListLoading.value = false
    }
  }

  // 未阅与全部消息使用不同缓存键，避免「全部」覆盖首页未阅数据
  async function loadMessages(mode = 'unread') {
    const epoch = dataEpoch
    const all = mode === 'all'
    const cacheKey = all ? 'messages.all' : 'messages'
    const target = all ? messagesAll : messages
    const loading = all ? messagesAllLoading : messagesLoading
    const offline = all ? messagesAllOffline : messagesOffline
    const cachedAt = all ? messagesAllCachedAt : messagesCachedAt
    const expired = all ? messagesAllExpired : messagesExpired
    const errorKey = all ? 'messagesAll' : 'messages'

    const cached = readDataCache(cacheKey, MESSAGES_TTL)
    if (cached?.data) {
      target.value = cached.data
      cachedAt.value = cached.cachedAt
      expired.value = cached.expired
    }
    loading.value = true
    setError(errorKey, '')
    try {
      const data = await api.getMessages(mode)
      if (epoch !== dataEpoch) return
      target.value = data
      offline.value = false
      expired.value = false
      const cacheTime = writeDataCache(cacheKey, data)
      cachedAt.value = data.fetchedAt || cacheTime
    } catch (err) {
      if (epoch !== dataEpoch) return
      if (target.value) {
        offline.value = true
        expired.value = Boolean(readDataCache(cacheKey, MESSAGES_TTL)?.expired)
      } else {
        setError(errorKey, err.message || '消息加载失败')
      }
    } finally {
      if (epoch === dataEpoch) loading.value = false
    }
  }

  function reset() {
    terms.value = []
    currentTerm.value = null
    schedule.value = null
    grades.value = null
    evaluations.value = null
    courseSelection.value = null
    portalNotices.value = null
    portalNoticeList.value = null
    messages.value = null
    messagesAll.value = null
    termsLoading.value = false
    scheduleLoading.value = false
    gradesLoading.value = false
    evaluationsLoading.value = false
    courseSelectionLoading.value = false
    portalNoticesLoading.value = false
    portalNoticeListLoading.value = false
    messagesLoading.value = false
    messagesAllLoading.value = false
    portalNoticesOffline.value = false
    portalNoticesCachedAt.value = ''
    portalNoticesExpired.value = false
    portalNoticeListOffline.value = false
    portalNoticeListCachedAt.value = ''
    portalNoticeListExpired.value = false
    messagesOffline.value = false
    messagesCachedAt.value = ''
    messagesExpired.value = false
    messagesAllOffline.value = false
    messagesAllCachedAt.value = ''
    messagesAllExpired.value = false
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
    dataEpoch += 1
    errors.value = { ...EMPTY_ERRORS }
  }

  return {
    terms,
    currentTerm,
    schedule,
    grades,
    evaluations,
    courseSelection,
    portalNotices,
    portalNoticeList,
    messages,
    messagesAll,
    termsLoading,
    scheduleLoading,
    gradesLoading,
    evaluationsLoading,
    courseSelectionLoading,
    portalNoticesLoading,
    portalNoticeListLoading,
    messagesLoading,
    messagesAllLoading,
    portalNoticesOffline,
    portalNoticesCachedAt,
    portalNoticesExpired,
    portalNoticeListOffline,
    portalNoticeListCachedAt,
    portalNoticeListExpired,
    messagesOffline,
    messagesCachedAt,
    messagesExpired,
    messagesAllOffline,
    messagesAllCachedAt,
    messagesAllExpired,
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
    loadTerms,
    loadSchedule,
    applySnapshotSchedule,
    loadGrades,
    loadAllGrades,
    loadEvaluations,
    loadCourseSelection,
    loadPortalNotices,
    loadPortalNoticeList,
    loadMessages,
    loadCourse,
    reset
  }
})
