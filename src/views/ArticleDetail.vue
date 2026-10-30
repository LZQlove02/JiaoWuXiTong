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
      <p v-if="!offline && updatedLabel" class="tiny footnote">
        更新于 {{ updatedLabel }}
      </p>
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
  /* 原来的「教务处官网」分隔行已移除，这里补回标题与正文的间距 */
  margin-bottom: 16px;
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
