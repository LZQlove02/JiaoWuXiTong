<template>
  <div class="page page--plain">
    <BackBar title="校历" />

    <p v-if="offline" class="cache-tip">
      网络不可用，显示 {{ updatedLabel }} 的缓存（图片需联网查看{{ expired ? '，缓存已过期' : '' }}）
    </p>

    <div v-if="loading && !calendar" class="card state">正在加载校历…</div>

    <div v-else-if="error" class="card state">
      <p class="error-text">{{ error }}</p>
      <button class="retry" @click="load">重新加载</button>
    </div>

    <section v-else-if="calendar" class="card">
      <div class="head">
        <p class="head-title">校历</p>
        <p class="tiny">来源：柳州职业技术大学官网</p>
      </div>

      <div class="img-box" @click="openViewer">
        <img :src="calendar.imageUrl" alt="校历" @error="imageError = true" />
        <p v-if="imageError" class="img-error">图片加载失败，请联网后重试</p>
      </div>

      <p class="tiny center">点击图片可放大查看</p>
      <p class="tiny center">更新于 {{ updatedLabel }}，以学校发布为准</p>
    </section>

    <div v-if="viewer && calendar" class="viewer" @click.self="viewer = false">
      <div class="viewer-bar">
        <button class="viewer-btn" @click="viewer = false">关闭</button>
        <button class="viewer-btn" @click="zoom = !zoom">
          {{ zoom ? '缩小' : '放大' }}
        </button>
      </div>
      <div class="viewer-scroll">
        <img :src="calendar.imageUrl" alt="校历" :class="{ zoomed: zoom }" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { usePublicCalendar } from '../composables/usePublicContent'
import BackBar from '../components/BackBar.vue'

const { calendar, loading, error, offline, expired, updatedLabel, hydrate, load } =
  usePublicCalendar()

const viewer = ref(false)
const zoom = ref(false)
const imageError = ref(false)

function openViewer() {
  if (imageError.value) return
  viewer.value = true
  zoom.value = false
}

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

.head {
  margin-bottom: 12px;
}

.head-title {
  font-size: 17px;
  font-weight: 600;
}

.img-box {
  position: relative;
  border-radius: 10px;
  overflow: hidden;
  background: var(--card-muted);
}

.img-box img {
  display: block;
  width: 100%;
  height: auto;
}

.img-error {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--text-secondary);
  background: var(--card-muted);
}

.center {
  text-align: center;
  margin-top: 8px;
}

.viewer {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(0, 0, 0, 0.94);
  display: flex;
  flex-direction: column;
}

.viewer-bar {
  flex: none;
  display: flex;
  justify-content: space-between;
  padding: calc(env(safe-area-inset-top) + 10px) 16px 10px;
}

.viewer-btn {
  color: #fff;
  font-size: 15px;
  padding: 8px 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.14);
}

.viewer-scroll {
  flex: 1;
  overflow: auto;
  -webkit-overflow-scrolling: touch;
  padding: 0 8px calc(env(safe-area-inset-bottom) + 12px);
}

.viewer-scroll img {
  display: block;
  width: 100%;
  height: auto;
}

.viewer-scroll img.zoomed {
  width: 200%;
  max-width: none;
}
</style>
