import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { ensureFontLoaded } from './components/library/font'
import './components/library/plugins'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')

// 预热点阵字库（内建 JSON），文字组件渲染时直接使用
void ensureFontLoaded()
