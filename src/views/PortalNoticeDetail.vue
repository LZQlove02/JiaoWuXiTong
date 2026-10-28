<template>
  <div class="page page--plain">
    <BackBar title="通知详情" />

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

      <div v-if="detail.attachments.length" class="attachments">
        <p class="tiny attach-head">附件（请在教务系统内下载）</p>
        <p v-for="(file, index) in detail.attachments" :key="index" class="attachment">
          {{ file.name }}
        </p>
      </div>

      <p class="tiny footnote">来源：教务系统（学生门户）</p>
    </article>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import BackBar from '../components/BackBar.vue'
import EmptyState from '../components/EmptyState.vue'

const route = useRoute()
const detail = ref(null)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await api.getPortalNoticeDetail(route.params.id)
  } catch (err) {
    error.value = err.message || '通知加载失败'
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