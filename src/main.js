import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import './styles/theme.css'
import './styles/base.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

const auth = useAuthStore(pinia)

// 会话恢复在后台执行，页面立即挂载；路由守卫会等待恢复结果再决定去向
auth.bootstrap()
app.use(router)
app.mount('#app')
