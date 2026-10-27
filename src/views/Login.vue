<template>
  <div class="login-page">
    <div class="brand">
      <div class="logo">柳</div>
      <h1 class="brand-title">柳职教务</h1>
      <p class="brand-sub">柳州职业技术大学 · 教务助手</p>
    </div>

    <form class="card login-card" @submit.prevent="submit">
      <label class="field">
        <span class="field-label">学号</span>
        <input
          v-model.trim="username"
          class="field-input"
          inputmode="numeric"
          placeholder="请输入学号"
          autocomplete="username"
        />
      </label>

      <label class="field">
        <span class="field-label">密码</span>
        <input
          v-model="password"
          class="field-input"
          type="password"
          placeholder="请输入密码"
          autocomplete="current-password"
        />
      </label>

      <p v-if="error" class="error">{{ error }}</p>

      <button class="submit" type="submit" :disabled="auth.loading">
        {{ auth.loading ? '登录中…' : '登录' }}
      </button>

      <p class="hint">M1 演示模式：使用模拟数据，任意学号与密码均可登录。</p>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const error = ref('')

async function submit() {
  error.value = ''
  if (!username.value || !password.value) {
    error.value = '请输入学号和密码'
    return
  }
  try {
    await auth.login(username.value, password.value)
    router.replace(route.query.redirect || '/')
  } catch (err) {
    error.value = err.message || '登录失败，请重试'
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
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
  background: var(--brand);
  color: #fff;
  font-size: 30px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
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

.field {
  display: block;
  margin-bottom: 16px;
}

.field-label {
  display: block;
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.field-input {
  width: 100%;
  height: 46px;
  padding: 0 14px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--card-muted);
  color: var(--text);
  font-size: 15px;
  outline: none;
}

.field-input:focus {
  border-color: var(--brand);
  background: var(--card);
}

.error {
  color: var(--danger);
  font-size: 13px;
  margin-bottom: 12px;
}

.submit {
  width: 100%;
  height: 48px;
  border-radius: 12px;
  background: var(--brand);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}

.submit:disabled {
  opacity: 0.6;
}

.hint {
  margin-top: 14px;
  font-size: 12px;
  color: var(--text-tertiary);
  text-align: center;
  line-height: 1.6;
}
</style>
