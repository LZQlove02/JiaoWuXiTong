<template>
  <div class="page page--plain">
    <BackBar title="评教" />

    <section class="card read-only">
      <span class="read-only-icon"><AppIcon name="info" /></span>
      <p class="read-only-text">仅只读展示，不提供评教提交</p>
    </section>

    <div v-if="loading && !evaluations" class="card loading">正在加载评教记录…</div>

    <template v-else-if="evaluations">
      <div class="section-head">
        <h2>评价记录</h2>
        <span class="pill">共 {{ evaluations.total }} 条</span>
      </div>

      <section v-if="evaluations.items.length" class="card list">
        <article v-for="item in evaluations.items" :key="item.id" class="eval-row">
          <div class="eval-main">
            <p class="eval-name">{{ item.courseName }}</p>
            <p class="eval-meta">{{ metaLine(item) }}</p>
          </div>
          <span class="status" :class="{ done: item.submitted }">{{ item.status }}</span>
        </article>
      </section>

      <section v-else class="card">
        <EmptyState
          icon="evaluate"
          title="暂无评教记录"
          description="教务系统暂无可展示的评教记录。"
        />
      </section>

      <p v-if="fetchedLabel || evaluations.total > evaluations.items.length" class="footnote tiny">
        <template v-if="fetchedLabel">更新于 {{ fetchedLabel }}</template>
        <template v-if="evaluations.total > evaluations.items.length">{{ fetchedLabel ? ' · ' : '' }}当前显示 {{ evaluations.items.length }} 条</template>
      </p>
    </template>

    <section v-else class="card">
      <EmptyState
        icon="evaluate"
        :title="errors.evaluations ? '评教记录暂不可用' : '暂无评教记录'"
        :description="errors.evaluations || '教务系统暂无可展示的评教记录。'"
        :action-label="errors.evaluations ? '重试' : ''"
        @action="reload"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useAcademicStore } from '../stores/academic'
import BackBar from '../components/BackBar.vue'
import AppIcon from '../components/AppIcon.vue'
import EmptyState from '../components/EmptyState.vue'

const academic = useAcademicStore()
const { evaluations, loading, errors } = storeToRefs(academic)

const fetchedLabel = computed(() => {
  const value = evaluations.value?.fetchedAt
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit'
  })
})

function metaLine(item) {
  return [item.teacher, item.className].filter(Boolean).join(' · ')
}

async function reload() {
  await academic.initTerms()
  await academic.loadEvaluations()
}

onMounted(reload)
</script>

<style scoped>
.read-only {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 12px;
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

.loading {
  margin-top: 12px;
  text-align: center;
  color: var(--text-secondary);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 12px 0 10px;
}

.section-head h2 {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-secondary);
}

.list {
  padding: 4px 16px;
}

.eval-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 0;
  border-bottom: 1px solid var(--border);
}

.eval-row:last-child {
  border-bottom: none;
}

.eval-main {
  min-width: 0;
}

.eval-name {
  font-size: 15px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.eval-meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.status {
  flex: none;
  padding: 2px 9px;
  border-radius: 20px;
  font-size: 11px;
  color: var(--text-secondary);
  background: var(--card-muted);
}

.status.done {
  color: var(--success);
  background: rgba(30, 142, 90, 0.1);
}
</style>