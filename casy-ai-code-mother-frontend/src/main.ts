import { createApp } from 'vue'
import { createPinia } from 'pinia'

import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import '@/assets/theme.css'
import App from './App.vue'
import router from './router'
import '@/axios/access'
import { initTheme } from '@/stores/theme'

initTheme()

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.use(Antd).mount('#app')
