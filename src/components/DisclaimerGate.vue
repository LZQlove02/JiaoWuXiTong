<template>
  <div
    class="gate"
    role="dialog"
    aria-modal="true"
    aria-labelledby="disclaimer-title"
  >
    <div class="gate-card">
      <header class="gate-head">
        <img class="gate-logo" src="../assets/app-icon.png" alt="" />
        <h1 id="disclaimer-title" class="gate-title">使用前请阅读</h1>
        <p class="gate-sub">柳职教务 · 免责声明</p>
      </header>

      <div class="gate-body">
        <section v-for="(item, index) in clauses" :key="item.title" class="clause">
          <h2 class="clause-title">
            <span class="clause-index">{{ index + 1 }}</span>{{ item.title }}
          </h2>
          <p class="clause-text">{{ item.text }}</p>
        </section>
      </div>

      <footer class="gate-foot">
        <p class="gate-note">{{ footNote }}</p>
        <p v-if="declined" class="gate-declined">已退出，请关闭此页面。</p>
        <button
          v-else-if="mode === 'reader'"
          ref="primaryRef"
          class="primary"
          type="button"
          @click="$emit('close')"
        >
          关闭
        </button>
        <template v-else>
          <button ref="primaryRef" class="primary" type="button" @click="$emit('accept')">
            同意并继续
          </button>
          <button class="ghost" type="button" @click="$emit('decline')">不同意，退出</button>
        </template>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { DISCLAIMER_DATE, DISCLAIMER_VERSION } from '../utils/disclaimer'

const props = defineProps({
  // gate：首次启动门禁；reader：设置页只读重读
  mode: { type: String, default: 'gate' },
  // Web 预览环境退不掉，用一句静态提示兜底
  declined: { type: Boolean, default: false }
})

defineEmits(['accept', 'decline', 'close'])

const clauses = [
  {
    title: '非官方应用',
    text: '本应用为个人开发的第三方客户端，与学校及教务系统运营方无隶属关系，不对外分发、不商业化。'
  },
  {
    title: '数据来源',
    text: '课表、成绩等数据来自学校教务系统，仅限本人账号只读使用；不采集、不上传他人数据。'
  },
  {
    title: '账号安全',
    text: '登录在学校统一认证页面完成，本应用不保存账号密码；本机仅保存加密会话，可在退出登录时删除。'
  },
  {
    title: '使用风险',
    text: '接口可能随时变更或失效，数据仅供参考，以学校教务系统为准；使用本应用产生的后果由使用者自行承担。'
  },
  {
    title: '合规使用',
    text: '请遵守学校网络与信息管理规定，不得用于爬取、传播他人数据或商业用途。'
  },
  {
    title: '声明更新',
    text: '本声明修订后将于下次启动重新提示。'
  }
]

const primaryRef = ref(null)

const footNote = computed(() => {
  const base = `声明版本 v${DISCLAIMER_VERSION} · ${DISCLAIMER_DATE}`
  return props.mode === 'reader'
    ? base
    : `继续即表示你已阅读并同意上述条款 · ${base}`
})

onMounted(() => {
  primaryRef.value?.focus()
})
</script>

<style scoped>
.gate {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: flex;
  justify-content: center;
  background: var(--bg);
  padding: calc(env(safe-area-inset-top) + 26px) 22px
    calc(env(safe-area-inset-bottom) + 16px);
  overflow: hidden;
}

.gate-card {
  width: 100%;
  max-width: 520px;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.gate-head {
  text-align: center;
  padding-bottom: 16px;
}

.gate-logo {
  width: 56px;
  height: 56px;
  margin: 0 auto 12px;
  border-radius: 16px;
  display: block;
}

.gate-title {
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.gate-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.gate-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 4px 4px 8px 2px;
}

.clause {
  margin-bottom: 14px;
}

.clause:last-child {
  margin-bottom: 0;
}

.clause-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.clause-index {
  flex: none;
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 11px;
  font-weight: 600;
}

.clause-text {
  font-size: 13px;
  line-height: 1.65;
  color: var(--text-secondary);
  text-align: justify;
}

.gate-foot {
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.gate-note {
  margin-bottom: 12px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--text-tertiary);
  text-align: center;
}

.primary {
  width: 100%;
  padding: 13px;
  border-radius: 12px;
  background: var(--brand);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  outline: none;
}

.primary:active {
  opacity: 0.85;
}

.ghost {
  width: 100%;
  margin-top: 8px;
  padding: 10px;
  border-radius: 12px;
  font-size: 13px;
  color: var(--text-tertiary);
  outline: none;
}

.gate-declined {
  padding: 10px 0;
  text-align: center;
  font-size: 14px;
  color: var(--text-secondary);
}

@media (max-height: 560px) {
  .gate-head {
    padding-bottom: 10px;
  }

  .gate-logo {
    width: 44px;
    height: 44px;
    margin-bottom: 8px;
  }

  .clause {
    margin-bottom: 10px;
  }
}
</style>