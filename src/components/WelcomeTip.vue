<template>
  <div class="welcome-mask" role="dialog" aria-modal="true" aria-labelledby="welcome-title">
    <div class="welcome-card">
      <img class="welcome-logo" src="../assets/app-icon.png" alt="" />
      <h2 id="welcome-title" class="welcome-title">欢迎使用教务助手</h2>
      <p class="welcome-sub">第一次使用，先看看这几条</p>
      <ul class="welcome-list">
        <li>在统一认证页勾选 Keep me signed in，可保留约一周免登录。</li>
        <li>课表时间可在设置中逐节调整，有更晚的课会自动扩展显示。</li>
        <li>桌面长按空白处可添加下一节课、今日课程与本周课表组件。</li>
      </ul>
      <div class="welcome-actions">
        <button class="primary" type="button" @click="openTutorial">查看使用教程</button>
        <button class="ghost" type="button" @click="close">知道了</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUiStore } from '../stores/ui'

const emit = defineEmits(['close'])
const router = useRouter()
const ui = useUiStore()

function close() {
  emit('close')
}

function openTutorial() {
  close()
  router.push('/tutorial')
}

onMounted(() => {
  ui.setOverlayCloser(close)
})

onBeforeUnmount(() => {
  ui.clearOverlayCloser()
})
</script>

<style scoped>
.welcome-mask {
  position: fixed;
  inset: 0;
  z-index: 112;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(10, 12, 15, 0.45);
}

.welcome-card {
  width: 100%;
  max-width: 360px;
  padding: 22px 20px 18px;
  border-radius: 18px;
  background: var(--card);
  box-shadow: var(--shadow);
}

.welcome-logo {
  width: 52px;
  height: 52px;
  margin: 0 auto 10px;
  border-radius: 15px;
  display: block;
}

.welcome-title {
  font-size: 18px;
  font-weight: 600;
  text-align: center;
}

.welcome-sub {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-tertiary);
  text-align: center;
}

.welcome-list {
  margin: 16px 0 20px;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.welcome-list li {
  position: relative;
  padding-left: 16px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
}

.welcome-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  width: 6px;
  height: 6px;
  border-radius: 3px;
  background: var(--brand);
}

.welcome-actions {
  display: flex;
  gap: 10px;
}

.primary,
.ghost {
  flex: 1;
  height: 44px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 500;
}

.primary {
  color: #fff;
  background: var(--brand);
}

.ghost {
  color: var(--text-secondary);
  background: var(--card-muted);
}
</style>
