<template>
  <div class="page page--plain">
    <BackBar :title="backTitle" />

    <p v-if="offline" class="cache-tip">
      网络不可用，显示缓存（更新于 {{ updatedLabel }}{{ expired ? '，已过期' : '' }}）
    </p>

    <LoadState
      :loading="loading && !article"
      :error="error"
      loading-text="正在加载正文…"
      @retry="load"
    />

    <article v-if="article && !error" class="card detail">
      <h1 class="detail-title">{{ article.title }}</h1>
      <p
        v-for="(paragraph, index) in article.paragraphs"
        :key="index"
        class="paragraph"
      >
        {{ paragraph }}
      </p>
      <p v-if="!article.paragraphs.length" class="paragraph">{{ article.text }}</p>

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

      <div v-if="article.attachments?.length" class="attachments">
        <p class="tiny attach-head">附件（点击在浏览器中打开）</p>
        <button
          v-for="file in article.attachments"
          :key="file.name + file.url"
          type="button"
          class="attachment"
          @click="openAttachment(file)"
        >
          <span class="attachment-name">{{ file.name }}</span>
          <AppIcon name="chevron-right" />
        </button>
      </div>

      <p v-if="actionError" class="tiny action-error">{{ actionError }}</p>

      <p v-if="!offline && updatedLabel" class="tiny footnote">
        更新于 {{ updatedLabel }}
      </p>

      <button v-if="article.url" class="source-link" type="button" @click="openOriginal">
        查看原文（教务处官网）
      </button>
    </article>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Capacitor } from '@capacitor/core'
import { downloadFile } from '../api/nativeHttp'
import { openExternal } from '../native/supportBridge'
import { usePublicArticle } from '../composables/usePublicContent'
import AppIcon from '../components/AppIcon.vue'
import BackBar from '../components/BackBar.vue'
import LoadState from '../components/LoadState.vue'

const route = useRoute()
const backTitle = computed(() => route.meta.title || '详情')
const section = route.meta.section || 'tzgg'

const { article, loading, error, offline, expired, updatedLabel, hydrate, load } =
  usePublicArticle(section, route.params.id)

const images = ref([])
const actionError = ref('')
let preparedSignature = ''

function hashUrl(url) {
  let hash = 0
  for (let index = 0; index < url.length; index += 1) {
    hash = (hash * 31 + url.charCodeAt(index)) | 0
  }
  return Math.abs(hash).toString(36)
}

function imageFileName(url, index) {
  try {
    const name = decodeURIComponent(new URL(url).pathname.split('/').pop() || '')
    const match = name.match(/\.(jpg|jpeg|png)$/i)
    if (match) {
      // 原生下载只接受 ASCII 文件名，用 URL 哈希 + 扩展名，避免中文名被拒绝
      return `guide-${hashUrl(url)}-${index + 1}${match[1].toLowerCase()}`
    }
  } catch (error) {
    // 非法 URL 时用固定名
  }
  return `guide-${hashUrl(url)}-${index + 1}.jpg`
}

async function prepareImages(data) {
  const list = Array.isArray(data?.images) ? data.images : []
  const signature = JSON.stringify(list.map((item) => item.url))
  if (signature === preparedSignature) return
  preparedSignature = signature
  actionError.value = ''
  images.value = list.map((item) => ({ ...item, localSrc: '' }))
  if (images.value.length === 0) return
  if (!Capacitor.isNativePlatform()) {
    images.value = images.value.map((item) => ({ ...item, localSrc: item.url }))
    return
  }
  await Promise.allSettled(
    images.value.map(async (image, index) => {
      try {
        const result = await downloadFile(image.url, imageFileName(image.url, index))
        image.localSrc = Capacitor.convertFileSrc(result.path)
      } catch (error) {
        // 单张图片失败时保留占位提示
      }
    })
  )
}

async function openAttachment(file) {
  actionError.value = ''
  try {
    await openExternal(file.url)
  } catch (error) {
    actionError.value = error?.message || '无法打开附件，请在浏览器中访问官网。'
  }
}

async function openOriginal() {
  actionError.value = ''
  try {
    await openExternal(article.value.url)
  } catch (error) {
    actionError.value = error?.message || '无法打开原文，请在浏览器中访问官网。'
  }
}

watch(article, (value) => {
  if (value) prepareImages(value)
}, { immediate: true })

onMounted(() => {
  hydrate()
  load()
})
</script>

<style scoped>
.detail {
  padding: 20px 18px;
}

.detail-title {
  font-size: 19px;
  line-height: 1.45;
  font-weight: 600;
  /* 原来的「教务处官网」分隔行已移除，这里补回标题与正文的间距 */
  margin-bottom: 16px;
}

.paragraph {
  font-size: 15px;
  line-height: 1.8;
  margin-bottom: 12px;
  color: var(--text);
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

.attachment-name {
  min-width: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--brand);
  word-break: break-all;
}

.action-error {
  margin-top: 10px;
  color: var(--danger);
}

.footnote {
  margin-top: 8px;
}
</style>
