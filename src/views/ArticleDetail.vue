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
      <p class="tiny meta">教务处官网</p>
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
import LoadState from '../components/LoadState.vue'

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
}

.footnote {
  margin-top: 8px;
}
</style>
