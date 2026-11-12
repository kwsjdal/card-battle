import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// 开发期把 /api 代理到后端8080, 避免跨域问题
export default defineConfig({
  plugins: [
    vue(),
    /**
     * Element Plus 按需引入: 模板里用到 <el-xxx> 时才打包对应组件与该组件样式。
     * 全量引入时 UI 库约 810KB 全部进主包; 按需引入后下降到百 KB 量级。
     * 注意: 插件只处理模板里的组件; 代码里显式 import 的 ElMessage 等仍会打进对应 chunk。
     */
    Components({
      resolvers: [ElementPlusResolver()],
      dts: false      // 不生成 components.d.ts(本项目未启用 TS)
    })
  ],
  server: {
    port: 5173,
    host: true,   // 监听0.0.0.0, 允许手机同局域网访问
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      // WebSocket也走vite代理, 手机端无需直连8080
      '/ws': { target: 'ws://localhost:8080', ws: true, changeOrigin: true }
    }
  },
  build: {
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        /**
         * 手动分包(论文可写: 前端首屏体积优化)
         * 把体积大、变更少的依赖库抽成独立 chunk, 浏览器可长期缓存;
         * 业务代码更新时这些 chunk 的 hash 不变, 用户无需重新下载。
         * 配合 router 的路由懒加载, 首屏只需下载 vue 与当前页面代码。
         */
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('@vue') || id.includes('/vue/') || id.includes('vue-router')) return 'vue'
          if (id.includes('@stomp') || id.includes('axios')) return 'net'
          /**
           * 注意: 这里刻意【不】把 element-plus 归到同一个 chunk。
           * 早期版本把它们全塞进一个 ui 块, 结果登录页就会顺带下载管理后台才用到的
           * el-table 等重型组件(792KB)。不干预后 Rollup 会按模块图自动切分:
           * 各页面真正用到的组件跟着该页面的异步 chunk 走, 公共部分自动提升为共享块。
           */
          if (id.includes('element-plus') || id.includes('@element-plus')) return
          return 'vendor'
        }
      }
    }
  }
})
