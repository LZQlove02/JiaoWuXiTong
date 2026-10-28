<template>
  <div class="page page--plain">
    <BackBar title="通知公告" />

    <p v-if="offline" class="cache-tip">
      网络不可用，显示缓存（更新于 {{ updatedLabel }}{{ expired ? '，已过期' : '' }}）
    </p>
    <p v-else-if="loading && items.length" class="cache-tip">正在刷新…</p>

    <LoadState
      :loading="loading && !items.length"
      :error="error"
      loading-text="正在加载教务处公告…"
      @retry="load"
    />

    <section v-if="!error && !(loading && !items.length)" class="card list">
      <button
        v-for="item in items"
        :key="item.id"
        class="notice-row"
        @click="open(item.id)"
      >
        <div class="notice-main">
          <p class="notice-title">{{ item.title }}</p>
          <p class="tiny">{{ item.date }}</p>
        </div>
        <AppIcon name="chevron-right" />
      </button>
      <EmptyState v-if="!items.length" title="暂无公告" />
    </section>

    <p v-if="!offline && updatedLabel" class="tiny footnote">
      来源：教务处官网 · 更新于 {{ updatedLabel }}
    </p>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePublicList } from '../composables/usePublicContent'
import BackBar from '../components/BackBar.vue'
import AppIcon from '../components/AppIcon.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadState from '../components/LoadState.vue'

const router = useRouter()
const { items, loading, error, offline, expired, updatedLabel, hydrate, load } =
  usePublicList('tzgg')

function open(id) {
  router.push(`/notices/${id}`)
}

onMounted(() => {
  hydrate()
  load()
})
</script>

<style scoped>
.list {
  padding: 4px 16px;
}

.notice-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border);
  text-align: left;
  color: var(--text-tertiary);
}

.notice-row:last-of-type {
  border-bottom: none;
}

.notice-main {
  flex: 1;
  min-width: 0;
}

.notice-title {
  font-size: 15px;
  line-height: 1.45;
  color: var(--text);
  margin-bottom: 4px;
}
</style>
