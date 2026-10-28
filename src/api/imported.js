const STORAGE_KEY = 'lzpu.import.v1'

export function termKey(term) {
  if (!term) return 'unknown'
  return `${term.xnm || ''}_${term.xqm || ''}`
}

function loadAll() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { v: 1, items: {} }
    const parsed = JSON.parse(raw)
    if (!parsed || parsed.v !== 1 || !parsed.items) return { v: 1, items: {} }
    return parsed
  } catch (error) {
    return { v: 1, items: {} }
  }
}

export function saveImport(term, kind, data) {
  const store = loadAll()
  const key = termKey(term)
  const entry = store.items[key] || { termLabel: term?.label || '', items: {} }
  entry.termLabel = term?.label || entry.termLabel
  entry.items[kind] = {
    data,
    source: '网页导入',
    fetchedAt: new Date().toISOString()
  }
  store.items[key] = entry
  localStorage.setItem(STORAGE_KEY, JSON.stringify(store))
}

export function clearImports() {
  localStorage.removeItem(STORAGE_KEY)
}

export function loadImports() {
  return loadAll()
}

export function hasAnyImport() {
  const store = loadAll()
  return Object.values(store.items).some(
    (entry) => entry && entry.items && Object.keys(entry.items).length > 0
  )
}

export function getImported(term, kind) {
  const entry = loadAll().items[termKey(term)]
  const item = entry?.items?.[kind]
  if (!item) return null

  if (kind === 'schedule') {
    return {
      term,
      courses: item.data.courses || [],
      sessions: item.data.sessions || [],
      source: item.source,
      fetchedAt: item.fetchedAt
    }
  }
  if (kind === 'grades') {
    return {
      term,
      grades: item.data.grades || [],
      summary: item.data.summary || {
        totalCredits: 0,
        gpa: 0,
        count: 0,
        failed: 0
      },
      source: item.source,
      fetchedAt: item.fetchedAt
    }
  }
  if (kind === 'exams') {
    const exams = Array.isArray(item.data) ? [...item.data] : []
    exams.source = item.source
    exams.fetchedAt = item.fetchedAt
    return exams
  }
  return null
}

function findImportedCourse(id) {
  const store = loadAll()
  for (const entry of Object.values(store.items)) {
    const schedule = entry?.items?.schedule?.data
    if (!schedule) continue
    const course = (schedule.courses || []).find((item) => item.id === id)
    if (!course) continue
    return {
      ...course,
      sessions: (schedule.sessions || []).filter((item) => item.courseId === id)
    }
  }
  return null
}

export class HybridAcademicApi {
  constructor(mock) {
    this.mock = mock
  }

  login(username, password) {
    return this.mock.login(username, password)
  }

  logout() {
    return this.mock.logout()
  }

  getProfile() {
    return this.mock.getProfile()
  }

  getTerms() {
    return this.mock.getTerms()
  }

  getSchedule(term) {
    return Promise.resolve(getImported(term, 'schedule') || this.mock.getSchedule(term))
  }

  getGrades(term) {
    return Promise.resolve(getImported(term, 'grades') || this.mock.getGrades(term))
  }

  getExams(term) {
    return Promise.resolve(getImported(term, 'exams') || this.mock.getExams(term))
  }

  getCourse(id) {
    const imported = findImportedCourse(id)
    return Promise.resolve(imported || this.mock.getCourse(id))
  }
}
