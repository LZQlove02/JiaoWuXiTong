<template>
  <div class="page page--plain">
    <BackBar title="通知公告" />

    <p v-if="portalNoticeListOffline" class="cache-tip">
      网络不可用，显示缓存<template v-if="portalNoticeListCachedAt">（更新于 {{ formatDateTime(portalNoticeListCachedAt) }}）</template>
    </p>

    <p v-if="error && items.length" class="tiny error-tip">
      刷新失败：{{ error }}（显示上次数据）
    </p>
    <p v-if="portalNoticeList?.truncated" class="tiny truncate-tip">
      仅显示前 {{ items.length }} 条，共 {{ portalNoticeList.total }} 条
    </p>

    <LoadState
      v-if="!items.length"
      :loading="loading"
      :error="error"
      loading-text="正在加载教务通知…"
      @retry="academic.loadPortalNoticeList"
    />

    <section v-if="items.length" class="card list">
      <button
        v-for="item in items"
        :key="item.id"
        class="notice-row"
        @click="open(item.id)"
      >
        <div class="notice-main">
          <p class="notice-title">
            <span v-if="item.pinned" class="pin">置顶</span>{{ item.title }}
          </p>
          <p class="tiny">{{ item.date }}<template v-if="item.publisher"> · {{ item.publisher }}</template></p>
        </div>
        <AppIcon name="chevron-right" />
      </button>
    </section>

    <section v-else-if="!error && !loading" class="card">
      <EmptyState title="暂无通知" description="教务系统暂无通知公告。" />
    </section>

    <p v-if="!error && portalNoticeListCachedAt" class="tiny footnote">
      更新于 {{ formatDateTime(portalNoticeListCachedAt) }}
    </p>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useAcademicStore } from '../stores/academic'
import { formatDateTime } from '../utils/date'
import BackBar from '../components/BackBar.vue'
import AppIcon from '../components/AppIcon.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadState from '../components/LoadState.vue'

const router = useRouter()
const academic = useAcademicStore()
const { portalNoticeList, portalNoticeListLoading, portalNoticeListOffline, portalNoticeListCachedAt, errors } =
  storeToRefs(academic)
const loading = portalNoticeListLoading

const items = computed(() => portalNoticeList.value?.items || [])
const error = computed(() => errors.value.portalNoticeList)

function open(id) {
  router.push(`/notices/${id}`)
}

onMounted(() => {
  academic.loadPortalNoticeList()
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

.pin {
  display: inline-block;
  margin-right: 6px;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  color: var(--danger);
  background: rgba(192, 57, 43, 0.1);
}

.error-tip {
  margin-bottom: 10px;
  color: var(--danger);
}

.truncate-tip {
  margin-bottom: 8px;
  color: var(--text-tertiary);
}
</style>