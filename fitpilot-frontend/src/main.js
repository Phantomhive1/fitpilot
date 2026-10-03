import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/theme.css'
import App from './App.vue'
import router from './router'

// 主题初始化：读取上次选择（默认深色），在 Vue 挂载前应用，避免闪烁
// - html.dark 类        → 控制 Element Plus 的暗色变量
// - html[data-theme]    → 控制我们自己的 CSS 变量（见 src/styles/theme.css）
const savedTheme = localStorage.getItem('fitpilot:theme') || 'dark'
document.documentElement.dataset.theme = savedTheme
if (savedTheme === 'dark') document.documentElement.classList.add('dark')

createApp(App).use(ElementPlus).use(router).mount('#app')
