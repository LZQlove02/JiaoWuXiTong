<template>
  <div class="page page--plain">
    <BackBar title="通知详情" />

    <p v-if="offline" class="cache-tip">
      网络不可用，显示缓存<template v-if="cachedAt">（更新于 {{ formatDateTime(cachedAt) }}）</template>
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

      <p v-if="detail.images" class="tiny image-tip">
        正文含 {{ detail.images }} 张图片，请在教务系统中查看。
      </p>

      <div v-if="detail.attachments.length" class="attachments">
        <p class="tiny attach-head">附件（请在教务系统内下载）</p>
        <p v-for="(file, index) in detail.attachments" :key="index" class="attachment">
          {{ file.name }}
        </p>
      </div>

      <p class="tiny footnote">来源：{{ detail.source || '教务系统（学生门户）' }}</p>
    </article>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
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
  }
  loading.value = !detail.value
  error.value = ''
  offline.value = false
  try {
    detail.value = await api.getPortalNoticeDetail(id)
    cachedAt.value = detail.value.fetchedAt || new Date().toISOString()
    writePortalCache(cacheKey, detail.value)
  } catch (err) {
    if (detail.value) {
      offline.value = true
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
  margin-top: 4px;
  color: var(--text-tertiary);
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
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary);
  word-break: break-all;
}

.footnote {
  margin-top: 16px;
}
</style>