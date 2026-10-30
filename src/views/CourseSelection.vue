<template>
  <div class="page page--plain">
    <BackBar title="选课" />

    <p v-if="courseSelectionOffline" class="cache-tip">
      网络不可用，显示缓存<template v-if="courseSelectionCachedAt">（更新于 {{ formatDateTime(courseSelectionCachedAt) }}{{ courseSelectionExpired ? '，已过期' : '' }}）</template>
    </p>

    <div v-if="courseSelectionLoading && !courseSelection" class="card loading">正在查询选课状态…</div>

    <template v-else-if="courseSelection">
      <section class="card status-card">
        <span class="status-icon" :class="{ open: courseSelection.open }">
          <AppIcon :name="courseSelection.open ? 'select' : 'clock'" />
        </span>
        <p class="status-title">{{ courseSelection.label }}</p>
        <p class="status-desc">{{ courseSelection.description }}</p>
      </section>

      <section class="card read-only">
        <span class="read-only-icon"><AppIcon name="info" /></span>
        <p class="read-only-text">本应用不提供选课、退课操作，仅同步教务系统状态。</p>
      </section>

      <p v-if="fetchedLabel" class="footnote tiny">
        更新于 {{ fetchedLabel }}
      </p>
    </template>

    <section v-else class="card">
      <EmptyState
        icon="select"
        :title="errors.courseSelection ? '选课状态暂不可用' : '选课状态未知'"
        :description="errors.courseSelection || '请稍后重试。'"
        :action-label="errors.courseSelection ? '重试' : ''"
        @action="reload"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useAcademicStore } from '../stores/academic'
import { formatDateTime } from '../utils/date'
import BackBar from '../components/BackBar.vue'
import AppIcon from '../components/AppIcon.vue'
import EmptyState from '../components/EmptyState.vue'

const academic = useAcademicStore()
const {
  courseSelection,
  courseSelectionLoading,
  courseSelectionOffline,
  courseSelectionCachedAt,
  courseSelectionExpired,
  errors
} = storeToRefs(academic)

const fetchedLabel = computed(() => {
  const value = courseSelection.value?.fetchedAt
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit'
  })
})

async function reload() {
  await academic.initTerms()
  await academic.loadCourseSelection()
}

onMounted(reload)
</script>

<style scoped>
.loading {
  margin-top: 12px;
  text-align: center;
  color: var(--text-secondary);
}

.status-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 32px 20px;
  text-align: center;
}

.status-icon {
  width: 56px;
  height: 56px;
  margin-bottom: 4px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  background: var(--card-muted);
}

.status-icon.open {
  color: var(--brand);
  background: var(--brand-soft);
}

.status-title {
  font-size: 17px;
  font-weight: 600;
}

.status-desc {
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary);
  max-width: 280px;
}

.read-only {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-top: 12px;
  background: var(--card-muted);
  box-shadow: none;
}

.read-only-icon {
  display: flex;
  color: var(--text-tertiary);
}

.read-only-text {
  font-size: 12px;
  color: var(--text-secondary);
}
</style>