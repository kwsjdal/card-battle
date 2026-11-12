import { createApp } from 'vue'
/**
 * Element Plus 改为按需引入(配合 vite.config.js 的 ElementPlusResolver):
 * 全量引入会把整个 UI 库(约 810KB)打进主包; 按需引入后只打包实际用到的组件,
 * 组件级样式由插件自动带入, 详见 vite.config.js。
 * 说明: ElMessage / ElMessageBox 是函数调用式 API, 仍在各页面显式 import,
 * 其样式由 unplugin-vue-components 的 ElementPlusResolver 一并注入。
 */
import './theme.css'
import './ark.css'
import './skins.css'
import './responsive.css'
import App from './App.vue'
import router from './router'

createApp(App).use(router).mount('#app')
