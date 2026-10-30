import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import Home from '../views/Home.vue'
import Schedule from '../views/Schedule.vue'
import Grades from '../views/Grades.vue'
import Profile from '../views/Profile.vue'
import Login from '../views/Login.vue'
import Exams from '../views/Exams.vue'
import Notices from '../views/Notices.vue'
import PortalNoticeDetail from '../views/PortalNoticeDetail.vue'
import Messages from '../views/Messages.vue'
import ArticleDetail from '../views/ArticleDetail.vue'
import CourseDetail from '../views/CourseDetail.vue'
import Guide from '../views/Guide.vue'
import Calendar from '../views/Calendar.vue'
import Evaluation from '../views/Evaluation.vue'
import CourseSelection from '../views/CourseSelection.vue'
import Settings from '../views/Settings.vue'
import Changes from '../views/Changes.vue'

const routes = [
  { path: '/login', name: 'login', component: Login, meta: { public: true } },
  { path: '/', name: 'home', component: Home, meta: { tab: true } },
  { path: '/schedule', name: 'schedule', component: Schedule, meta: { tab: true } },
  { path: '/grades', name: 'grades', component: Grades, meta: { tab: true } },
  { path: '/profile', name: 'profile', component: Profile, meta: { tab: true } },
  {
    path: '/settings',
    name: 'settings',
    component: Settings,
    meta: { title: '设置' }
  },
  {
    path: '/changes',
    name: 'changes',
    component: Changes,
    meta: { title: '课表变更' }
  },
  { path: '/exams', name: 'exams', component: Exams },
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
  if (to.meta.public) {
    return auth.isLoggedIn ? '/' : true
  }
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
