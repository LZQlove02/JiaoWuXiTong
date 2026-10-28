<template>
  <div class="page page--plain">
    <BackBar title="消息" />

    <section class="card read-only">
      <span class="read-only-icon"><AppIcon name="info" /></span>
      <p class="read-only-text">仅查看，不标记已读</p>
    </section>

    <LoadState
      :loading="loading && !messages"
      :error="error"
      loading-text="正在加载消息…"
      @retry="reload"
    />

    <template v-if="messages">
      <div class="section-head">
        <h2>未阅消息</h2>
        <span class="pill">共 {{ messages.total }} 条</span>
      </div>

      <p v-if="messages.truncated" class="tiny truncate-tip">
        仅显示前 {{ messages.items.length }} 条，共 {{ messages.total }} 条
      </p>

      <section v-if="messages.items.length" class="card list">
        <article v-for="msg in messages.items" :key="msg.id" class="msg">
          <button class="msg-head" type="button" @click="toggle(msg.id)">
            <div class="msg-main">
              <p class="msg-title">{{ msg.title }}</p>
              <p class="tiny">{{ msg.time }}</p>
            </div>
            <AppIcon name="chevron-right" class="chev" :class="{ open: expandedId === msg.id }" />
          </button>
          <p v-if="expandedId === msg.id" class="msg-content">{{ msg.content || msg.title }}</p>
        </article>
      </section>

      <section v-else class="card">
        <EmptyState icon="message" title="暂无未阅消息" description="教务系统当前没有未阅消息。" />
      </section>

      <p class="tiny footnote">来源：{{ messages.source || '教务系统（只读）' }}</p>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { useAcademicStore } from '../stores/academic'
import BackBar from '../components/BackBar.vue'
import AppIcon from '../components/AppIcon.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadState from '../components/LoadState.vue'

const route = useRoute()
const academic = useAcademicStore()
const { messages, messagesLoading, errors } = storeToRefs(academic)
const loading = messagesLoading
const error = computed(() => errors.value.messages)
const expandedId = ref(route.query.id ? String(route.query.id) : '')

function toggle(id) {
  expandedId.value = expandedId.value === id ? '' : id
}

function reload() {
  academic.loadMessages()
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

.msg {
  border-bottom: 1px solid var(--border);
}

.msg:last-child {
  border-bottom: none;
}

.msg-head {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 0;
  text-align: left;
  color: var(--text-tertiary);
}

.msg-main {
  flex: 1;
  min-width: 0;
}

.msg-title {
  font-size: 14px;
  line-height: 1.55;
  color: var(--text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.chev {
  flex: none;
  transition: transform 0.2s ease;
}

.chev.open {
  transform: rotate(90deg);
}

.msg-content {
  padding: 0 0 14px;
  font-size: 14px;
  line-height: 1.75;
  color: var(--text-secondary);
  white-space: pre-wrap;
}

.footnote {
  margin-top: 14px;
  text-align: center;
}

.truncate-tip {
  margin-bottom: 8px;
  color: var(--text-tertiary);
}
</style>