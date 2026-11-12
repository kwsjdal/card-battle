<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import http from './api/request'
import sfx from './utils/sound'

/**
 * 全局交互音: 所有按钮点下去响一下(可在大厅右上角关掉)。
 * 首次交互时解锁 AudioContext(浏览器自动播放策略要求)。
 */
function onDocPointer() {
  sfx.unlock()
  document.removeEventListener('pointerdown', onDocPointer)
}
function onDocClick(e) {
  if (e.target && e.target.closest && e.target.closest('button')) sfx.play('click')
}

/**
 * 单设备登录: 账号在别处登录时, 后端会通过 WebSocket 推 kicked 事件,
 * 这里统一弹窗提示并清掉本地登录态回到登录页(不需要用户手动刷新才知道)。
 */
const router = useRouter()
function onKicked(e) {
  ElMessageBox.alert(
    e.detail?.msg || '你的账号在另一台设备登录，当前设备已退出',
    '⚠ 已退出登录',
    { confirmButtonText: '重新登录', type: 'warning', showClose: false }
  ).finally(() => {
    localStorage.clear()
    router.push('/login')
  })
}
onMounted(() => {
  window.addEventListener('kicked', onKicked)
  document.addEventListener('pointerdown', onDocPointer)
  document.addEventListener('click', onDocClick)
})
onUnmounted(() => {
  window.removeEventListener('kicked', onKicked)
  document.removeEventListener('pointerdown', onDocPointer)
  document.removeEventListener('click', onDocClick)
})
</script>

<template>
  <div class="app-bg">
    <div class="bg-grid"></div>
    <div class="bg-glow glow-amber"></div>
    <div class="bg-glow glow-cyan"></div>
    <div class="ark-hex hex-a"></div>
    <div class="ark-hex hex-b"></div>
    <div class="vignette"></div>
    <div class="scan"></div>
    <router-view />
  </div>
</template>

<style scoped>
.app-bg {
  position: relative;
  min-height: 100vh;
  background:
    radial-gradient(1200px 600px at 50% -10%, rgba(240, 165, 42, 0.08), transparent 70%),
    linear-gradient(180deg, #12161b 0%, #0d1013 45%, #090b0d 100%);
  background-attachment: fixed;
  overflow-x: hidden;
}

/* 工业网格 */
.bg-grid {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.028) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.028) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse at 50% 30%, #000 20%, transparent 85%);
  -webkit-mask-image: radial-gradient(ellipse at 50% 30%, #000 20%, transparent 85%);
  z-index: 0;
}

.bg-glow {
  position: fixed;
  width: 60vw;
  height: 60vw;
  border-radius: 50%;
  filter: blur(90px);
  pointer-events: none;
  z-index: 0;
}
.glow-amber {
  left: -18vw;
  top: -22vw;
  background: radial-gradient(circle, rgba(240, 165, 42, 0.14), transparent 65%);
}
.glow-cyan {
  right: -20vw;
  bottom: -24vw;
  background: radial-gradient(circle, rgba(79, 214, 230, 0.09), transparent 65%);
}

.hex-a { top: 8%; right: 3%; }
.hex-b { bottom: 6%; left: 2%; transform: rotate(12deg); }

.vignette {
  position: fixed;
  inset: 0;
  pointer-events: none;
  box-shadow:
    inset 0 0 180px 50px rgba(0, 0, 0, 0.78),
    inset 0 90px 220px -60px rgba(0, 0, 0, 0.6),
    inset 0 -90px 220px -60px rgba(0, 0, 0, 0.65);
  z-index: 1;
}

.scan {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background: repeating-linear-gradient(
    0deg,
    rgba(255, 255, 255, 0.02) 0px,
    rgba(255, 255, 255, 0.02) 1px,
    transparent 1px,
    transparent 4px
  );
  z-index: 1;
}

.app-bg > :not(.bg-grid):not(.bg-glow):not(.vignette):not(.scan):not(.ark-hex) {
  position: relative;
  z-index: 3;
}
</style>
