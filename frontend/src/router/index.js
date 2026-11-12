import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Lobby from '../views/Lobby.vue'

/**
 * 路由懒加载: 除登录页与大厅(首屏必经)外, 其余页面按需加载。
 * 配合 vite.config.js 的 manualChunks, 首屏体积由 1.2MB 降到 400KB 量级。
 * 低频页面(管理后台、数据中心、观战、战报)不会拖慢大厅进入速度。
 */
const DeckEditor = () => import('../views/DeckEditor.vue')
const Battle = () => import('../views/Battle.vue')
const Records = () => import('../views/Records.vue')
const Admin = () => import('../views/Admin.vue')
const Shop = () => import('../views/Shop.vue')
const Stats = () => import('../views/Stats.vue')
const Spectate = () => import('../views/Spectate.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: Login },
    { path: '/lobby', component: Lobby },
    { path: '/deck', component: DeckEditor },
    { path: '/battle', component: Battle },
    { path: '/records', component: Records },
    { path: '/admin', component: Admin },
    { path: '/shop', component: Shop },
    { path: '/stats', component: Stats },
    { path: '/spectate', component: Spectate },
  ]
})

// 未登录守卫 + 管理后台角色守卫
router.beforeEach((to) => {
  if (to.path === '/login') return
  if (!localStorage.getItem('token')) return '/login'
  if (to.path.startsWith('/admin')) {
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    if (user.role !== 'admin') return '/lobby'
  }
})

export default router
