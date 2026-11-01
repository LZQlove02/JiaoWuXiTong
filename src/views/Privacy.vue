<template>
  <div class="page page--plain">
    <BackBar title="隐私政策" />

    <section class="card intro">
      <h1 class="intro-title">教务助手隐私政策</h1>
      <p class="meta">版本 v{{ PRIVACY_VERSION }} · {{ PRIVACY_DATE }}</p>
      <p class="lead">
        本应用是个人开发的第三方非官方客户端，仅用于查看你本人的教务信息。请在使用前阅读以下规则，了解我们处理哪些信息、为什么处理以及你可以如何管理这些信息。
      </p>
    </section>

    <section v-for="(section, index) in PRIVACY_SECTIONS" :key="section.id" class="card block">
      <h2 class="block-title">
        <span class="index">{{ index + 1 }}</span>{{ section.title }}
      </h2>
      <p class="block-text">{{ section.text }}</p>
    </section>

    <section class="card block contact">
      <h2 class="block-title">联系我们</h2>
      <p class="block-text">
        如对本规则有疑问，或需要查询、更正、删除你的个人信息，可通过意见反馈或邮箱
        <strong>{{ PRIVACY_CONTACT }}</strong> 联系。
      </p>
      <router-link class="feedback-link" to="/feedback">去意见反馈</router-link>
    </section>

    <p class="foot">如需撤回同意，请前往「设置 → 隐私与数据 → 撤回隐私同意」。</p>
  </div>
</template>

<script setup>
import BackBar from '../components/BackBar.vue'
import {
  PRIVACY_CONTACT,
  PRIVACY_DATE,
  PRIVACY_SECTIONS,
  PRIVACY_VERSION
} from '../utils/privacy'
</script>

<style scoped>
.intro {
  padding: 18px 16px;
}

.intro-title {
  font-size: 19px;
  font-weight: 600;
}

.meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.lead {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.75;
  color: var(--text-secondary);
  text-align: justify;
}

.block {
  margin-top: 12px;
  padding: 16px;
}

.block-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
}

.index {
  flex: none;
  width: 19px;
  height: 19px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 11px;
  font-weight: 600;
}

.block-text {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.75;
  color: var(--text-secondary);
  text-align: justify;
}

.contact strong {
  color: var(--text);
  font-weight: 500;
  word-break: break-all;
}

.feedback-link {
  display: block;
  margin-top: 12px;
  font-size: 13px;
  color: var(--brand);
}

.foot {
  margin: 18px 4px 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-tertiary);
  text-align: center;
}
</style>
