<template>
  <div class="page page--plain">
    <BackBar title="使用教程" />

    <section class="card intro">
      <h2 class="intro-title">三分钟上手</h2>
      <ul class="intro-list">
        <li>登录后会自动同步课表、成绩、通知与消息。</li>
        <li>课表时间可在设置里逐节调整，有更晚的课会自动扩展显示。</li>
        <li>桌面长按空白处可添加下一节课、今日课程与本周课表组件。</li>
      </ul>
    </section>

    <h2 class="section-title">功能说明</h2>
    <section class="card list">
      <div v-for="section in tutorialSections" :key="section.id" class="topic">
        <button
          type="button"
          class="topic-head"
          :aria-expanded="open[section.id] ? 'true' : 'false'"
          :aria-controls="`${section.id}-body`"
          @click="toggle(section.id)"
        >
          <span class="topic-icon"><AppIcon :name="section.icon" /></span>
          <span class="topic-text">
            <span class="topic-title">{{ section.title }}</span>
            <span class="tiny">{{ section.summary }}</span>
          </span>
          <span class="arrow" :class="{ 'is-open': open[section.id] }">
            <AppIcon name="chevron-right" />
          </span>
        </button>
        <ul v-show="open[section.id]" :id="`${section.id}-body`" class="topic-body">
          <li v-for="(line, index) in section.items" :key="index">{{ line }}</li>
        </ul>
      </div>
    </section>

    <h2 class="section-title">常见问题</h2>
    <section class="card list">
      <div v-for="faq in tutorialFaqs" :key="faq.id" class="topic">
        <button
          type="button"
          class="topic-head"
          :aria-expanded="open[faq.id] ? 'true' : 'false'"
          :aria-controls="`${faq.id}-body`"
          @click="toggle(faq.id)"
        >
          <span class="faq-mark">Q</span>
          <span class="topic-text">
            <span class="topic-title">{{ faq.question }}</span>
          </span>
          <span class="arrow" :class="{ 'is-open': open[faq.id] }">
            <AppIcon name="chevron-right" />
          </span>
        </button>
        <ul v-show="open[faq.id]" :id="`${faq.id}-body`" class="topic-body">
          <li v-for="(line, index) in faq.items" :key="index">{{ line }}</li>
        </ul>
      </div>
    </section>

    <router-link class="feedback-entry" to="/feedback">仍有问题？去意见反馈</router-link>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import BackBar from '../components/BackBar.vue'
import AppIcon from '../components/AppIcon.vue'
import { tutorialFaqs, tutorialSections } from '../utils/tutorial'

const open = reactive({})
tutorialSections.forEach((section, index) => {
  // 首节默认展开，其余收起，避免一进来就是超长页面
  open[section.id] = index === 0
})
tutorialFaqs.forEach((faq) => {
  open[faq.id] = false
})

function toggle(id) {
  open[id] = !open[id]
}
</script>

<style scoped>
.intro {
  padding: 18px 16px;
}

.intro-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 10px;
}

.intro-list {
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--text-secondary);
}

.list {
  padding: 4px 16px;
}

.topic {
  border-bottom: 1px solid var(--border);
}

.topic:last-child {
  border-bottom: 0;
}

.topic-head {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 60px;
  padding: 10px 0;
  text-align: left;
}

.topic-icon,
.faq-mark {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--brand-soft);
  color: var(--brand);
}

.faq-mark {
  font-size: 15px;
  font-weight: 600;
}

.topic-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.topic-title {
  font-size: 14px;
  font-weight: 500;
}

.arrow {
  flex: none;
  display: inline-flex;
  color: var(--text-tertiary);
  transition: transform 0.18s ease;
}

.arrow.is-open {
  transform: rotate(90deg);
}

.topic-body {
  margin: -2px 0 14px 46px;
  padding-left: 16px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--text-secondary);
}

.topic-body li + li {
  margin-top: 4px;
}

.feedback-entry {
  display: block;
  margin: 22px 0 8px;
  text-align: center;
  font-size: 13px;
  color: var(--brand);
}
</style>
