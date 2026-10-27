<template>
  <div class="page page--plain">
    <BackBar :title="backTitle" />

    <p v-if="offline" class="cache-tip">
      网络不可用，显示缓存（更新于 {{ updatedLabel }}{{ expired ? '，已过期' : '' }}）
    </p>

    <div v-if="loading && !article" class="card state">正在加载正文…</div>

    <div v-else-if="error" class="card state">
      <p class="error-text">{{ error }}</p>
      <button class="retry" @click="load">重新加载</button>
    </div>

    <article v-else-if="article" class="card detail">
      <h1 class="detail-title">{{ article.title }}</h1>
      <p class="tiny meta">教务处官网</p>
      <div class="divider"></div>
      <p
        v-for="(paragraph, index) in article.paragraphs"
        :key="index"
        class="paragraph"
      >
        {{ paragraph }}
      </p>
      <p v-if="!article.paragraphs.length" class="paragraph">{{ article.text }}</p>
      <p v-if="!offline" class="tiny footnote">
        抓取于 {{ updatedLabel }}，以官网原文为准
      </p>
      <p v-else class="tiny footnote">离线缓存，以官网原文为准</p>
    </article>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { usePublicArticle } from '../composables/usePublicContent'
import BackBar from '../components/BackBar.vue'

const route = useRoute()
const backTitle = computed(() => route.meta.title || '详情')
const section = route.meta.section || 'tzgg'

const { article, loading, error, offline, expired, updatedLabel, hydrate, load } =
  usePublicArticle(section, route.params.id)

onMounted(() => {
  hydrate()
  load()
})
</script>

<style scoped>
.cache-tip {
  font-size: 12px;
  color: var(--warning);
  margin-bottom: 10px;
  text-align: center;
}

.state {
  text-align: center;
  color: var(--text-secondary);
  padding: 32px 16px;
}

.error-text {
  color: var(--danger);
  font-size: 14px;
  margin-bottom: 12px;
}

.retry {
  padding: 8px 18px;
  border-radius: 20px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 14px;
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
  margin-top: 8px;
}

.divider {
  height: 1px;
  background: var(--border);
  margin: 16px 0;
}

.paragraph {
  font-size: 15px;
  line-height: 1.8;
  margin-bottom: 12px;
  color: var(--text);
}

.footnote {
  margin-top: 8px;
}
</style>
