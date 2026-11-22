import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import Home from '../views/Home.vue'
import Schedule from '../views/Schedule.vue'
import Grades from '../views/Grades.vue'
import Profile from '../views/Profile.vue'
import Login from '../views/Login.vue'
import Notices from '../views/Notices.vue'
import PortalNoticeDetail from '../views/PortalNoticeDetail.vue'
import Messages from '../views/Messages.vue'
import ArticleDetail from '../views/ArticleDetail.vue'
import CourseDetail from '../views/CourseDetail.vue'
import Guide from '../views/Guide.vue'
import Calendar from '../views/Calendar.vue'
import Evaluation from '../views/Evaluation.vue'
import CourseSelection from '../views/CourseSelection.vue'
import ScheduleSettings from '../views/ScheduleSettings.vue'
import ReminderSettings from '../views/ReminderSettings.vue'
import AccountSettings from '../views/AccountSettings.vue'
import AppSettings from '../views/AppSettings.vue'
import Changes from '../views/Changes.vue'
import Tutorial from '../views/Tutorial.vue'
import About from '../views/About.vue'
import Privacy from '../views/Privacy.vue'
import OpenSource from '../views/OpenSource.vue'
import Feedback from '../views/Feedback.vue'

const routes = [
  { path: '/login', name: 'login', component: Login, meta: { public: true } },
  { path: '/', name: 'home', component: Home, meta: { tab: true } },
  { path: '/schedule', name: 'schedule', component: Schedule, meta: { tab: true } },
  { path: '/grades', name: 'grades', component: Grades, meta: { tab: true } },
  { path: '/profile', name: 'profile', component: Profile, meta: { tab: true } },
  {
    path: '/settings/reminders',
    name: 'reminder-settings',
    component: ReminderSettings,
    meta: { title: '提醒与通知' }
  },
  {
    path: '/settings/account',
    name: 'account-settings',
    component: AccountSettings,
    meta: { title: '账号与设备' }
  },
  {
    path: '/settings/app',
    name: 'app-settings',
    component: AppSettings,
    meta: { title: '应用与隐私' }
  },
  {
    path: '/schedule-settings',
    name: 'schedule-settings',
    component: ScheduleSettings,
    meta: { title: '课表时间' }
  },
  {
    path: '/changes',
    name: 'changes',
    component: Changes,
    meta: { title: '课表变更' }
  },
  {
    path: '/tutorial',
    name: 'tutorial',
    component: Tutorial,
    meta: { title: '使用教程' }
  },
  {
    path: '/about',
    name: 'about',
    component: About,
    meta: { title: '关于' }
  },
  {
    // 登录前后都能进入：登录页需要反馈入口
    path: '/feedback',
    name: 'feedback',
    component: Feedback,
    meta: { guest: true, title: '意见反馈' }
  },
  {
    path: '/privacy',
    name: 'privacy',
    component: Privacy,
    meta: { guest: true, title: '隐私政策' }
  },
  {
    path: '/licenses',
    name: 'licenses',
    component: OpenSource,
    meta: { guest: true, title: '开源许可' }
  },
  { path: '/notices', name: 'notices', component: Notices },
  {
    path: '/notices/:id',
    name: 'notice-detail',
    component: PortalNoticeDetail,
    meta: { title: '通知详情' }
  },
  { path: '/messages', name: 'messages', component: Messages },
  { path: '/guide', name: 'guide', component: Guide },
  {
    path: '/guide/:id',
    name: 'guide-detail',
    component: ArticleDetail,
    meta: { title: '办事指南', section: 'bszn' }
  },
  { path: '/course/:id', name: 'course-detail', component: CourseDetail },
  {
    path: '/courses',
    name: 'course-select',
    component: CourseSelection,
    meta: { title: '选课' }
  },
  {
    path: '/evaluation',
    name: 'evaluation',
    component: Evaluation,
    meta: { title: '评教' }
  },
  {
    path: '/calendar',
    name: 'calendar',
    component: Calendar
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (auth.restoring) await auth.bootstrap()
  if (to.meta.guest) return true
  if (to.meta.public) {
    // 票据过期后要允许回登录页重新认证；正常登录态下仍把登录页挡回首页
    return auth.isLoggedIn && !auth.sessionExpired ? '/' : true
  }
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
