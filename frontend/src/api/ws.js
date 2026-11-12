import { Client } from '@stomp/stompjs'

let client = null
const listeners = new Set()   // 多页面监听器: Lobby/Battle 各自注册, Set 去重

/**
 * 全局共享一个STOMP连接; onMessage 作为持久监听器注册(组件卸载时需 removeListener)
 *
 * 【安全】握手携带 JWT(?token=xxx), 服务端由 token 推导 uid 并写入会话属性。
 * 不再把 uid 放 URL、更不在报文里带 userId —— 早期版本可被任意账号伪造他人身份操作对局。
 */
export function connect(onMessage) {
  if (onMessage) listeners.add(onMessage)
  if (client && client.connected) return client
  const token = localStorage.getItem('token')
  const userJson = localStorage.getItem('user')
  if (!token || !userJson) { console.warn('[WS] no token/user in localStorage'); return null }
  const uid = JSON.parse(userJson).id
  const url = (location.protocol === 'https' ? 'wss://' : 'ws://') + location.host + '/ws?token=' + encodeURIComponent(token)
  console.log('[WS] creating STOMP client (token auth)')
  client = new Client({
    brokerURL: url,
    reconnectDelay: 3000,
    debug: (str) => console.log('[STOMP]', str),
    onConnect: () => {
      console.log('[WS] STOMP connected! subscribing /topic/user/' + uid)
      client.subscribe('/topic/user/' + uid, (msg) => {
        const data = JSON.parse(msg.body)
        // 被其它设备挤下线: 走全局事件, 由 App.vue 统一弹窗处理
        if (data.event === 'kicked') {
          window.dispatchEvent(new CustomEvent('kicked', { detail: data }))
          return
        }
        listeners.forEach((fn) => { try { fn(data) } catch (e) { console.error('[WS] listener error', e) } })
      })
    },
    onStompError: (frame) => {
      console.error('[WS] STOMP error:', frame)
      // 401/鉴权失败: 后端会拒绝握手, 这里兜底提示重新登录
      const m = frame?.headers?.message || ''
      if (m.includes('登录') || m.includes('未授权')) {
        window.dispatchEvent(new CustomEvent('kicked', { detail: { reason: m } }))
      }
    },
    onWebSocketClose: (evt) => console.warn('[WS] WS closed:', evt),
    onWebSocketError: (evt) => console.error('[WS] WS error:', evt)
  })
  console.log('[WS] calling activate()...')
  client.activate()
  console.log('[WS] activate() returned')
  return client
}

/** 组件卸载时移除监听, 防止已卸载页面继续收到推送 */
export function removeListener(onMessage) { listeners.delete(onMessage) }

export function getClient() { return client }
