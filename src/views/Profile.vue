<template>
  <div class="page">
    <section class="card user-card">
      <div class="avatar">{{ avatarText }}</div>
      <div class="user-main">
        <p class="user-name">{{ auth.user?.name }}</p>
        <p class="tiny">{{ auth.user?.id }} · {{ auth.user?.className }}</p>
        <p v-if="auth.user?.major" class="tiny">{{ auth.user.major }}</p>
        <p v-if="auth.user?.college" class="tiny">{{ auth.user.college }}</p>
      </div>
    </section>

    <section class="card settings entry-card">
      <router-link class="setting-row" to="/settings/reminders">
        <span class="setting-left">
          <span class="setting-icon"><AppIcon name="notice" /></span>
          <span class="setting-text">
            <span class="setting-title">提醒与通知</span>
            <span class="tiny">课表变更、成绩与通知消息</span>
          </span>
        </span>
        <AppIcon name="chevron-right" />
      </router-link>
      <router-link class="setting-row" to="/settings/session">
        <span class="setting-left">
          <span class="setting-icon"><AppIcon name="logout" /></span>
          <span class="setting-text">
            <span class="setting-title">登录与会话</span>
            <span class="tiny">保持登录，减少短信验证</span>
          </span>
        </span>
        <AppIcon name="chevron-right" />
      </router-link>
      <router-link class="setting-row" to="/settings/widgets">
        <span class="setting-left">
          <span class="setting-icon"><AppIcon name="calendar" /></span>
          <span class="setting-text">
            <span class="setting-title">桌面组件</span>
            <span class="tiny">组件样式与刷新</span>
          </span>
        </span>
        <AppIcon name="chevron-right" />
      </router-link>
      <router-link class="setting-row" to="/settings">
        <span class="setting-left">
          <span class="setting-icon"><AppIcon name="settings" /></span>
          <span class="setting-text">
            <span class="setting-title">应用设置</span>
            <span class="tiny">外观、课表时间、隐私与数据</span>
          </span>
        </span>
        <AppIcon name="chevron-right" />
      </router-link>
    </section>

    <button class="logout" @click="onLogout">
      <AppIcon name="logout" />
      退出登录
    </button>

    <p class="tiny footer">数据来源：柳州职业技术大学教务系统</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AppIcon from '../components/AppIcon.vue'

const auth = useAuthStore()
const router = useRouter()

const avatarText = computed(() => (auth.user?.name || '我').slice(-1))

async function onLogout() {
  const confirmed = window.confirm('退出登录会清除本机会话、课表缓存与变更记录，确定退出吗？')
  if (!confirmed) return
  await auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
/* 页面没有标题了，名片直接置顶，间距交给 .page 的安全区内边距 */
.user-card {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 22px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-main {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 3px;
}

/* 设置入口单独一张卡，与用户卡留出层级间距 */
.entry-card {
  margin-top: 16px;
}

.settings {
  padding: 4px 16px;
}

.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border);
  color: var(--text-tertiary);
}

.setting-row:last-child {
  border-bottom: none;
}

.setting-left {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.setting-icon {
  flex: none;
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--brand-soft);
  color: var(--brand);
}

.setting-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  color: var(--text);
}

.setting-title {
  font-size: 15px;
}

.logout {
  width: 100%;
  margin-top: 20px;
  height: 48px;
  border-radius: 12px;
  background: var(--card);
  color: var(--danger);
  font-size: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

/* 页面撑满一屏，数据来源用 margin-top:auto 顶到最底部（TabBar 之上） */
.page {
  display: flex;
  flex-direction: column;
}

.footer {
  margin-top: auto;
  padding-top: 20px;
  text-align: center;
}
</style>
