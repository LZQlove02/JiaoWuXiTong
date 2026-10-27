import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import Home from '../views/Home.vue'
import Schedule from '../views/Schedule.vue'
import Grades from '../views/Grades.vue'
import Profile from '../views/Profile.vue'
import Login from '../views/Login.vue'
import Exams from '../views/Exams.vue'
import Notices from '../views/Notices.vue'
import ArticleDetail from '../views/ArticleDetail.vue'
import CourseDetail from '../views/CourseDetail.vue'
import Guide from '../views/Guide.vue'
import Calendar from '../views/Calendar.vue'
import ComingSoon from '../views/ComingSoon.vue'

const routes = [
  { path: '/login', name: 'login', component: Login, meta: { public: true } },
  { path: '/', name: 'home', component: Home, meta: { tab: true } },
  { path: '/schedule', name: 'schedule', component: Schedule, meta: { tab: true } },
  { path: '/grades', name: 'grades', component: Grades, meta: { tab: true } },
  { path: '/profile', name: 'profile', component: Profile, meta: { tab: true } },
  { path: '/exams', name: 'exams', component: Exams },
  { path: '/notices', name: 'notices', component: Notices },
  {
    path: '/notices/:id',
    name: 'notice-detail',
    component: ArticleDetail,
    meta: { title: '通知详情', section: 'tzgg' }
  },
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
    component: ComingSoon,
    meta: { title: '选课', icon: 'select', desc: '选课功能将在 M3 开放窗口期接入真实接口。' }
  },
  {
    path: '/evaluation',
    name: 'evaluation',
    component: ComingSoon,
    meta: { title: '评教', icon: 'evaluate', desc: '教学评价将在 M3 接入，开放期内可提交评教。' }
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

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    return auth.isLoggedIn ? '/' : true
  }
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
