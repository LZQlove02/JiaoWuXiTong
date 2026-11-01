<template>
  <div class="page page--plain">
    <BackBar title="通知详情" />

    <p v-if="offline" class="cache-tip">
      网络不可用，显示缓存<template v-if="cachedAt">（更新于 {{ formatDateTime(cachedAt) }}{{ expired ? '，已过期' : '' }}）</template>
    </p>

    <p v-if="loading && !detail" class="card loading">正在加载通知…</p>

    <section v-else-if="error" class="card">
      <EmptyState
        icon="notice"
        title="通知加载失败"
        :description="error"
        action-label="重试"
        @action="load"
      />
    </section>

    <article v-else-if="detail" class="card detail">
      <h1 class="detail-title">{{ detail.title }}</h1>
      <p class="tiny meta">
        {{ detail.publishedAt }}<template v-if="detail.views !== null"> · 浏览 {{ detail.views }}</template>
      </p>

      <p v-for="(paragraph, index) in detail.paragraphs" :key="index" class="paragraph">
        {{ paragraph }}
      </p>

      <div v-if="images.length" class="image-list">
        <div v-for="(image, index) in images" :key="image.url" class="image-item">
          <img
            v-if="image.localSrc"
            :src="image.localSrc"
            :alt="image.alt || `图片 ${index + 1}`"
            loading="lazy"
          />
          <p v-else class="image-placeholder">图片 {{ index + 1 }} 需联网后查看</p>
        </div>
      </div>

      <p v-if="detail.images && images.length < detail.images" class="tiny image-tip">
        还有 {{ detail.images - images.length }} 张图片需在教务系统中查看。
      </p>

      <div v-if="attachments.length" class="attachments">
        <p class="tiny attach-head">附件（点击下载并打开）</p>
        <button
          v-for="file in attachments"
          :key="file.name + file.url"
          type="button"
          class="attachment"
          :disabled="file.loading"
          @click="openAttachment(file)"
        >
          <span class="attachment-name">{{ file.name }}</span>
          <span class="attachment-state">
            {{ file.loading ? '下载中…' : file.localPath ? '已下载' : '下载' }}
          </span>
        </button>
      </div>

      <p v-if="actionError" class="tiny action-error">{{ actionError }}</p>

      <button v-if="detail.url" class="source-link" type="button" @click="openOriginal">
        查看原文（教务系统）
      </button>
    </article>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Capacitor } from '@capacitor/core'
import { api } from '../api'
import { authDownload } from '../api/nativeHttp'
import { openExternal, openLocalFile } from '../native/supportBridge'
import { PORTAL_NOTICE_DETAIL_TTL, readPortalCache, writePortalCache } from '../utils/portalCache'
import { formatDateTime } from '../utils/date'
import BackBar from '../components/BackBar.vue'
import EmptyState from '../components/EmptyState.vue'

const route = useRoute()
const detail = ref(null)
const loading = ref(false)
const error = ref('')
const offline = ref(false)
const cachedAt = ref('')
const expired = ref(false)
const images = ref([])
const attachments = ref([])
const actionError = ref('')
let preparedSignature = ''

function imageFileName(url, index) {
  try {
    const name = decodeURIComponent(new URL(url).pathname.split('/').pop() || '')
    if (/\.(jpg|jpeg|png)$/i.test(name)) return name
  } catch (error) {
    // URL 不合法时退回固定名
  }
  return `notice-image-${index + 1}.jpg`
}

async function prepareFiles(data) {
  const imageList = Array.isArray(data?.imageUrls) ? data.imageUrls : []
  const attachmentList = Array.isArray(data?.attachments) ? data.attachments : []
  const signature = JSON.stringify({
    images: imageList.map((item) => item.url),
    attachments: attachmentList.map((item) => item.url || item.href)
  })
  if (signature === preparedSignature) return
  preparedSignature = signature
  actionError.value = ''
  images.value = imageList.map((item) => ({ ...item, localSrc: '' }))
  attachments.value = attachmentList.map((item) => ({
    ...item,
    loading: false,
    localPath: ''
  }))

  if (!Capacitor.isNativePlatform()) {
    images.value = images.value.map((item) => ({ ...item, localSrc: item.url }))
    return
  }

  await Promise.allSettled(
    images.value.map(async (image, index) => {
      try {
        const result = await authDownload(image.url, imageFileName(image.url, index))
        image.localSrc = Capacitor.convertFileSrc(result.path)
      } catch (error) {
        // 单张图片失败不影响正文，仍显示占位提示
      }
    })
  )
}

async function openAttachment(file) {
  if (file.loading) return
  if (!file.url) {
    actionError.value = '附件地址无法解析，请在教务系统中查看。'
    return
  }
  file.loading = true
  actionError.value = ''
  try {
    if (!file.localPath) {
      const result = await authDownload(file.url, file.name)
      file.localPath = result.path
    }
    await openLocalFile(file.localPath)
  } catch (err) {
    actionError.value = err?.message || '附件下载或打开失败，请在教务系统中查看。'
  } finally {
    file.loading = false
  }
}

async function openOriginal() {
  actionError.value = ''
  try {
    await openExternal(detail.value.url)
  } catch (err) {
    actionError.value = err?.message || '无法打开原文，请在教务系统中查看。'
  }
}

async function load() {
  const id = String(route.params.id || '').trim()
  if (!id) {
    error.value = '通知编号无效'
    detail.value = null
    return
  }
  const cacheKey = `notices.detail.${id}`
  const cached = readPortalCache(cacheKey, PORTAL_NOTICE_DETAIL_TTL)
  if (cached?.data) {
    detail.value = cached.data
    cachedAt.value = cached.cachedAt
    expired.value = cached.expired
    prepareFiles(cached.data)
  }
  loading.value = !detail.value
  error.value = ''
  offline.value = false
  try {
    detail.value = await api.getPortalNoticeDetail(id)
    expired.value = false
    cachedAt.value = detail.value.fetchedAt || new Date().toISOString()
    writePortalCache(cacheKey, detail.value)
    prepareFiles(detail.value)
  } catch (err) {
    if (detail.value) {
      offline.value = true
      expired.value = Boolean(readPortalCache(cacheKey, PORTAL_NOTICE_DETAIL_TTL)?.expired)
    } else {
      error.value = err.message || '通知加载失败'
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.loading {
  text-align: center;
  color: var(--text-secondary);
}

.detail {
  padding: 20px 18px;
}

.detail-title {
  font-size: 19px;
  line-height: 1.45;
  font-weight: 600;
}

.meta {
  margin: 8px 0 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
}

.paragraph {
  font-size: 15px;
  line-height: 1.8;
  margin-bottom: 12px;
  color: var(--text);
  white-space: pre-wrap;
}

.image-tip {
  margin-top: 8px;
  color: var(--text-tertiary);
}

.source-link {
  display: block;
  width: 100%;
  margin-top: 18px;
  padding: 11px;
  border-radius: 10px;
  background: var(--card-muted);
  color: var(--brand);
  font-size: 13px;
  text-align: center;
}

.image-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 14px 0;
}

.image-item img {
  width: 100%;
  display: block;
  border-radius: 10px;
}

.image-placeholder {
  padding: 18px;
  border-radius: 10px;
  background: var(--card-muted);
  color: var(--text-tertiary);
  font-size: 12px;
  text-align: center;
}

.attachments {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
}

.attach-head {
  margin-bottom: 8px;
}

.attachment {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
  text-align: left;
}

.attachment:last-of-type {
  border-bottom: none;
}

.attachment:disabled {
  opacity: 0.6;
}

.attachment-name {
  min-width: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--brand);
  word-break: break-all;
}

.attachment-state {
  flex: none;
  font-size: 12px;
  color: var(--text-tertiary);
}

.action-error {
  margin-top: 10px;
  color: var(--danger);
}

.footnote {
  margin-top: 16px;
}
</style>