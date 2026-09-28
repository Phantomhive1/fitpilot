import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'
import router from './router'

// 启用 Element Plus 暗色主题
document.documentElement.classList.add('dark')

createApp(App).use(ElementPlus).use(router).mount('#app')
