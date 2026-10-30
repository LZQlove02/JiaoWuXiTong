<template>
  <div class="page">
    <section class="card user-card">
      <div class="avatar">{{ avatarText }}</div>
      <div class="user-main">
        <p class="user-name">{{ auth.user?.name }}</p>
        <p class="tiny">{{ auth.user?.id }} · {{ auth.user?.className }}</p>
        <p v-if="auth.user?.college" class="tiny">{{ auth.user.college }}</p>
      </div>
    </section>

    <section class="card settings entry-card">
      <button class="setting-row" @click="router.push('/settings')">
        <span class="setting-left"><AppIcon name="settings" />设置</span>
        <AppIcon name="chevron-right" />
      </button>
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
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  color: var(--text-tertiary);
}

.setting-left {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  color: var(--text);
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