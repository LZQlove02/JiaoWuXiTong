<template>
  <div class="page page--plain">
    <BackBar title="课表变更" />

    <div class="chips">
      <button
        v-for="chip in chips"
        :key="chip.key"
        class="chip"
        :class="{ 'is-active': filter === chip.key }"
        @click="filter = chip.key"
      >
        {{ chip.label }}
      </button>
    </div>

    <div v-if="!changes.loaded" class="card loading">正在加载变更记录…</div>
    <div v-else-if="changes.error && !changes.entries.length" class="card error-card">
      <p class="error-text">{{ changes.error }}</p>
      <button class="error-retry" @click="retry">重试</button>
    </div>
    <div v-else-if="!groups.length" class="card">
      <EmptyState icon="calendar" :title="emptyTitle" :description="emptyDescription" />
    </div>
    <template v-else>
      <section v-for="group in groups" :key="group.label" class="group">
        <h2 class="section-title">{{ group.label }}</h2>
        <div class="card list">
          <article v-for="entry in group.items" :key="entry.id" class="entry">
            <div class="entry-head">
              <span class="tag" :class="`tag--${metaOf(entry).tone}`">{{ metaOf(entry).label }}</span>
              <span class="entry-course">{{ entry.courseName }}</span>
            </div>
            <p class="entry-detail">{{ summaryOf(entry) }}</p>
            <p class="tiny">{{ detectedText(entry.detectedAt) }}</p>
          </article>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useChangesStore } from '../stores/changes'
import { changeMeta, changeSummary, dateGroupLabel, detectedText } from '../utils/changeLabels'
import BackBar from '../components/BackBar.vue'
import EmptyState from '../components/EmptyState.vue'

const changes = useChangesStore()
const filter = ref('all')

const chips = [
  { key: 'all', label: '全部' },
  { key: 'cancel', label: '停课' },
  { key: 'move', label: '调课' },
  { key: 'add', label: '新增' },
  { key: 'room', label: '换教室' }
]

const filtered = computed(() =>
  changes.entries.filter((entry) => filter.value === 'all' || entry.type === filter.value)
)

const groups = computed(() => {
  const grouped = new Map()
  for (const entry of filtered.value) {
    const label = dateGroupLabel(entry.detectedAt)
    if (!grouped.has(label)) grouped.set(label, [])
    grouped.get(label).push(entry)
  }
  return [...grouped.entries()].map(([label, items]) => ({ label, items }))
})

const activeChipLabel = computed(
  () => chips.find((chip) => chip.key === filter.value)?.label || '变更'
)

const emptyTitle = computed(() =>
  filter.value === 'all' ? '最近 30 天没有课表变更' : `没有${activeChipLabel.value}记录`
)

const emptyDescription = computed(() =>
  filter.value === 'all'
    ? '停课、调课、新增或换教室时会自动记在这里。'
    : '换一个筛选条件看看。'
)

function metaOf(entry) {
  return changeMeta(entry.type)
}

function summaryOf(entry) {
  return changeSummary(entry)
}

async function retry() {
  await changes.init()
  if (!changes.error) await changes.markAllRead()
}

onMounted(async () => {
  await changes.init()
  // 进入列表即视为看过，清掉提醒条与红点
  await changes.markAllRead()
})
</script>

<style scoped>
.chips {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.chip {
  flex: none;
  padding: 6px 14px;
  border-radius: 16px;
  background: var(--card-muted);
  color: var(--text-secondary);
  font-size: 13px;
}

.chip.is-active {
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 600;
}

.loading {
  text-align: center;
  color: var(--text-secondary);
  padding: 32px 0;
}

.error-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 32px 16px;
}

.error-text {
  font-size: 14px;
  color: var(--danger);
}

.error-retry {
  padding: 7px 18px;
  border-radius: 18px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 13px;
  font-weight: 600;
}

.group {
  margin-bottom: 4px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-tertiary);
  margin: 0 0 8px 4px;
}

.list {
  padding: 2px 16px;
}

.entry {
  padding: 13px 0;
  border-bottom: 1px solid var(--border);
}

.entry:last-child {
  border-bottom: none;
}

.entry-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.entry-course {
  font-size: 15px;
  font-weight: 600;
}

.entry-detail {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.45;
}

.tag {
  flex: none;
  padding: 2px 7px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
}

.tag--danger {
  background: rgba(192, 57, 43, 0.12);
  color: var(--danger);
}

.tag--warning {
  background: rgba(183, 121, 31, 0.14);
  color: var(--warning);
}

.tag--success {
  background: rgba(30, 142, 90, 0.14);
  color: var(--success);
}

.tag--muted {
  background: var(--card-muted);
  color: var(--text-tertiary);
}
</style>