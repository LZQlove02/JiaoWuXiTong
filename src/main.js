import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { isDisclaimerAccepted } from './utils/disclaimer'
import './styles/theme.css'
import './styles/base.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

const auth = useAuthStore(pinia)

// 会话恢复在后台执行，页面立即挂载；路由守卫会等待恢复结果再决定去向
// 首次使用需先同意免责声明：未同意时门禁页不触碰凭据与网络，同意后再恢复
if (isDisclaimerAccepted()) auth.bootstrap()
app.use(router)
app.mount('#app')
