<template>
  <div class="page page--plain">
    <BackBar title="校历" />

    <p v-if="offline" class="cache-tip">
      网络不可用，显示 {{ updatedLabel }} 的缓存{{ expired ? '，缓存已过期' : '' }}<template v-if="!localAvailable">（图片需联网查看）</template>
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
        <img
          v-if="imageSrc"
          :src="imageSrc"
          alt="校历"
          @load="imageError = false"
          @error="imageError = true"
        />
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
        <img :src="imageSrc" alt="校历" :class="{ zoomed: zoom }" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { Capacitor } from '@capacitor/core'
import { usePublicCalendar } from '../composables/usePublicContent'
import { fileExists } from '../api/nativeHttp'
import { useUiStore } from '../stores/ui'
import BackBar from '../components/BackBar.vue'

const ui = useUiStore()
const { calendar, loading, error, offline, expired, updatedLabel, hydrate, load } =
  usePublicCalendar()

const viewer = ref(false)
const zoom = ref(false)
const imageError = ref(false)
const imageSrc = ref('')
const localAvailable = ref(false)

async function resolveImage() {
  const item = calendar.value
  imageError.value = false
  localAvailable.value = false
  if (!item) {
    imageSrc.value = ''
    return
  }
  if (item.localPath && Capacitor.isNativePlatform()) {
    try {
      const result = await fileExists(item.localPath)
      if (result.exists) {
        localAvailable.value = true
        imageSrc.value = `${Capacitor.convertFileSrc(item.localPath)}?v=${
          item.cachedAt || ''
        }`
        return
      }
    } catch (error) {
      // 本地文件不可用时退回远程地址
    }
  }
  imageSrc.value = item.imageUrl
}

watch(calendar, resolveImage, { immediate: true })

watch(viewer, (open) => {
  if (open) {
    ui.setOverlayCloser(() => {
      viewer.value = false
    })
  } else {
    ui.clearOverlayCloser()
  }
})

onUnmounted(() => ui.clearOverlayCloser())

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
