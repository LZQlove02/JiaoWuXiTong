import { ref } from 'vue'
import {
  fetchArticleList,
  fetchArticle,
  fetchCalendar,
  getCachedList,
  getCachedArticle,
  getCachedCalendar,
  isCacheExpired,
  LIST_TTL_MS,
  ARTICLE_TTL_MS,
  CALENDAR_TTL_MS,
  formatFetchedAt
} from '../api/public'

export function usePublicList(section) {
  const items = ref([])
  const loading = ref(false)
  const error = ref('')
  const offline = ref(false)
  const expired = ref(false)
  const updatedLabel = ref('')

  function apply(data, fromCache) {
    items.value = data.items || []
    updatedLabel.value = formatFetchedAt(data.cachedAt || data.fetchedAt)
    expired.value = fromCache ? isCacheExpired(data.cachedAt, LIST_TTL_MS) : false
  }

  function hydrate() {
    const cached = getCachedList(section)
    if (!cached) return false
    apply(cached, true)
    return true
  }

  async function load() {
    const hasCache = items.value.length > 0
    loading.value = !hasCache
    error.value = ''
    offline.value = false
    try {
      const data = await fetchArticleList(section)
      apply(data, false)
    } catch (err) {
      if (hasCache) {
        offline.value = true
        expired.value = isCacheExpired(
          getCachedList(section)?.cachedAt,
          LIST_TTL_MS
        )
      } else {
        error.value = err.message || '加载失败'
      }
    } finally {
      loading.value = false
    }
  }

  return { items, loading, error, offline, expired, updatedLabel, hydrate, load }
}

export function usePublicArticle(section, id) {
  const article = ref(null)
  const loading = ref(false)
  const error = ref('')
  const offline = ref(false)
  const expired = ref(false)
  const updatedLabel = ref('')

  function apply(data, fromCache) {
    article.value = data
    updatedLabel.value = formatFetchedAt(data.cachedAt || data.fetchedAt)
    expired.value = fromCache ? isCacheExpired(data.cachedAt, ARTICLE_TTL_MS) : false
  }

  function hydrate() {
    const cached = getCachedArticle(section, id)
    if (!cached) return false
    apply(cached, true)
    return true
  }

  async function load() {
    const hasCache = Boolean(article.value)
    loading.value = !hasCache
    error.value = ''
    offline.value = false
    try {
      const data = await fetchArticle(section, id)
      apply(data, false)
    } catch (err) {
      if (hasCache) {
        offline.value = true
        expired.value = isCacheExpired(
          getCachedArticle(section, id)?.cachedAt,
          ARTICLE_TTL_MS
        )
      } else {
        error.value = err.message || '加载失败'
      }
    } finally {
      loading.value = false
    }
  }

  return { article, loading, error, offline, expired, updatedLabel, hydrate, load }
}

export function usePublicCalendar() {
  const calendar = ref(null)
  const loading = ref(false)
  const error = ref('')
  const offline = ref(false)
  const expired = ref(false)
  const updatedLabel = ref('')

  function apply(data, fromCache) {
    calendar.value = data
    updatedLabel.value = formatFetchedAt(data.cachedAt || data.fetchedAt)
    expired.value = fromCache
      ? isCacheExpired(data.cachedAt, CALENDAR_TTL_MS)
      : false
  }

  function hydrate() {
    const cached = getCachedCalendar()
    if (!cached) return false
    apply(cached, true)
    return true
  }

  async function load() {
    const hasCache = Boolean(calendar.value)
    loading.value = !hasCache
    error.value = ''
    offline.value = false
    try {
      const data = await fetchCalendar()
      apply(data, false)
    } catch (err) {
      if (hasCache) {
        offline.value = true
        expired.value = isCacheExpired(
          getCachedCalendar()?.cachedAt,
          CALENDAR_TTL_MS
        )
      } else {
        error.value = err.message || '校历加载失败'
      }
    } finally {
      loading.value = false
    }
  }

  return { calendar, loading, error, offline, expired, updatedLabel, hydrate, load }
}
