<template>
  <div class="login-page">
    <div class="brand">
      <img class="logo" src="../assets/app-icon.png" alt="柳职教务" />
      <h1 class="brand-title">柳职教务</h1>
      <p class="brand-sub">柳州职业技术大学 · 教务助手</p>
    </div>

    <section class="card login-card">
      <button class="sso" type="button" :disabled="auth.loading" @click="ssoLogin">
        {{ auth.loading ? '正在打开统一认证…' : '学校统一认证登录' }}
      </button>
      <p class="hint">登录后可查看实时课表。</p>
      <p v-if="error" class="error">{{ error }}</p>
    </section>

    <router-link class="feedback-link" to="/feedback">遇到问题？意见反馈</router-link>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const error = ref('')

async function ssoLogin() {
  error.value = ''
  try {
    await auth.loginWithSso()
    router.replace(route.query.redirect || '/')
  } catch (err) {
    error.value = err.message || '统一认证登录失败'
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100%;
  padding: calc(env(safe-area-inset-top) + 48px) 24px
    calc(env(safe-area-inset-bottom) + 24px);
  background: var(--bg);
}

.brand {
  text-align: center;
  margin-bottom: 28px;
}

.logo {
  width: 64px;
  height: 64px;
  margin: 0 auto 14px;
  border-radius: 18px;
  display: block;
}

.brand-title {
  font-size: 24px;
  font-weight: 600;
  letter-spacing: 1px;
}

.brand-sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}

.login-card {
  padding: 20px;
}

.feedback-link {
  display: block;
  margin-top: 20px;
  text-align: center;
  font-size: 13px;
  color: var(--text-tertiary);
}

.sso {
  width: 100%;
  height: 48px;
  border-radius: 12px;
  background: #1f7a5a;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}

.sso:disabled {
  opacity: 0.6;
}

.hint {
  margin-top: 16px;
  font-size: 12px;
  color: var(--text-tertiary);
  text-align: center;
  line-height: 1.6;
}

.error {
  margin-top: 12px;
  color: var(--danger);
  font-size: 13px;
  text-align: center;
}
</style>
