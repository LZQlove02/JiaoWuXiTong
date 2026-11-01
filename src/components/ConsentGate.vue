<template>
  <div
    class="gate"
    role="dialog"
    aria-modal="true"
    aria-labelledby="consent-title"
  >
    <div class="gate-card">
      <header class="gate-head">
        <img class="gate-logo" src="../assets/app-icon.png" alt="" />
        <h1 id="consent-title" class="gate-title">{{ title }}</h1>
        <p class="gate-sub">{{ subtitle }}</p>
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
            {{ primaryText }}
          </button>
          <button class="ghost" type="button" @click="$emit('decline')">{{ declineText }}</button>
        </template>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'

const props = defineProps({
  // gate：同意门禁；reader：设置页只读重读
  mode: { type: String, default: 'gate' },
  // Web 预览环境退不掉，用一句静态提示兜底
  declined: { type: Boolean, default: false },
  title: { type: String, default: '使用前请阅读' },
  subtitle: { type: String, default: '' },
  // [{ title, text }]
  clauses: { type: Array, required: true },
  versionText: { type: String, default: '' },
  primaryText: { type: String, default: '同意并继续' },
  declineText: { type: String, default: '不同意，退出' }
})

defineEmits(['accept', 'decline', 'close'])

const primaryRef = ref(null)

const footNote = computed(() => {
  if (props.mode === 'reader') return props.versionText
  const note = '继续即表示你已阅读并同意上述条款'
  return props.versionText ? `${note} · ${props.versionText}` : note
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
