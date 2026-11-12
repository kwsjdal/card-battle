<template>
  <div class="spectate">
    <div class="toolbar">
      <button class="btn-tb" @click="$router.push('/lobby')">← 返回大厅</button>
      <span class="title">观 战 大 厅</span>
      <button class="btn-tb" @click="loadRooms">⟳ 刷新</button>
    </div>

    <div class="tip">
      观战只展示双方场上局势与战斗日志，<b>不会显示任何一方的手牌</b>。
    </div>

    <!-- 对局列表 -->
    <div class="room-list" v-if="!watching">
      <div v-if="rooms.length === 0" class="empty">当前没有进行中的对局，先去打一局吧</div>
      <div v-for="r in rooms" :key="r.roomId" class="room-card" @click="watch(r)">
        <div class="rc-side">
          <span class="rc-name">{{ r.p1 }}</span>
          <span class="rc-hp" :class="{ low: r.hp1 <= 10 }">{{ r.hp1 }}</span>
        </div>
        <div class="rc-mid">
          <span class="rc-vs">VS</span>
          <span class="rc-turn">第 {{ r.turnNo }} 回合</span>
        </div>
        <div class="rc-side">
          <span class="rc-hp" :class="{ low: r.hp2 <= 10 }">{{ r.hp2 }}</span>
          <span class="rc-name">{{ r.p2 }}</span>
        </div>
        <button class="rc-btn">▶ 观看</button>
      </div>
    </div>

    <!-- 观战中 -->
    <div v-else class="watch-area">
      <div class="w-head">
        <button class="btn-tb" @click="stopWatch">← 返回列表</button>
        <span class="w-title">{{ view.p1.nickname }} <b>VS</b> {{ view.p2.nickname }}</span>
        <span class="w-turn">第 {{ view.turnNo }} 回合</span>
      </div>

      <div v-if="view.phase === 'GAME_OVER'" class="over-tip">
        本局已结束<span v-if="view.winnerId"> —— 胜者：{{
          view.winnerId === view.p1.userId ? view.p1.nickname : view.p2.nickname }}</span>
      </div>

      <div class="board">
        <div class="side" v-for="p in [view.p2, view.p1]" :key="p.userId">
          <div class="side-head">
            <span class="s-name" :class="{ cur: view.currentUserId === p.userId }">{{ p.nickname }}</span>
            <span class="s-hp">HP <b :class="{ low: p.hp <= 10 }">{{ p.hp }}</b></span>
            <span class="s-mana">💎 {{ p.mana }}/{{ p.maxMana }}</span>
            <span class="s-meta">牌库 {{ p.deckCount }} · 手牌 {{ p.handCount }}</span>
            <span v-if="p.fatigue" class="fatigue-tag">疲劳 {{ p.fatigue }}</span>
            <span v-if="p.offline" class="offline-tag">掉线中</span>
          </div>
          <div class="minions">
            <CardFace v-for="(c, i) in p.board" :key="i" :card="c" size="md" />
            <span v-if="!p.board || p.board.length === 0" class="no-minion">场上无随从</span>
          </div>
        </div>
      </div>

      <div class="log-panel">
        <div class="lp-title">战斗日志</div>
        <div class="lp-body">
          <div v-for="(l, i) in view.logTail || []" :key="i" class="lp-line">
            <span class="lp-turn">T{{ l.turn }}</span>{{ l.text }}
          </div>
          <div v-if="!(view.logTail || []).length" class="lp-empty">暂无日志</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import http from '../api/request'
import CardFace from '../components/CardFace.vue'
import { Client } from '@stomp/stompjs'

const rooms = ref([])
const watching = ref(false)
const view = ref({ p1: {}, p2: {}, logTail: [], turnNo: 0 })
let stomp = null
let sub = null
let pollTimer = null

async function loadRooms() {
  try { rooms.value = await http.get('/battle/live') } catch (e) { rooms.value = [] }
}

function connectWs() {
  if (stomp && stomp.connected) return
  const token = localStorage.getItem('token')
  const url = (location.protocol === 'https' ? 'wss://' : 'ws://') + location.host + '/ws?token=' + encodeURIComponent(token)
  stomp = new Client({ brokerURL: url, reconnectDelay: 3000, debug: () => {} })
  stomp.activate()
}

async function watch(r) {
  connectWs()
  const snap = await http.get(`/battle/${r.roomId}/spectate`)
  view.value = snap
  watching.value = true
  // 等 WS 就绪后订阅观战频道; 期间靠 HTTP 轮询兜底
  let tries = 0
  const trySub = setInterval(() => {
    if (stomp && stomp.connected) {
      sub = stomp.subscribe('/topic/battle/' + r.roomId, (msg) => {
        view.value = JSON.parse(msg.body)
      })
      clearInterval(trySub)
    } else if (++tries > 20) clearInterval(trySub)
  }, 300)
  // 兜底轮询: WS 未连上时每 3 秒拉一次
  pollTimer = setInterval(async () => {
    try { view.value = await http.get(`/battle/${r.roomId}/spectate`) } catch (e) { /* 对局结束则停止 */ }
  }, 3000)
}

function stopWatch() {
  if (sub) { try { sub.unsubscribe() } catch (e) { /* 已断开 */ } sub = null }
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  watching.value = false
  loadRooms()
}

function cleanup() {
  if (pollTimer) clearInterval(pollTimer)
  if (stomp) { try { stomp.deactivate() } catch (e) { /* 已断开 */ } }
}
onMounted(loadRooms)
onUnmounted(cleanup)
</script>

<style scoped>
.spectate { max-width: 860px; margin: 0 auto; padding: 20px; }

.toolbar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 22px;
  background: linear-gradient(90deg, rgba(43,32,23,0.9), rgba(20,16,12,0.8));
  border: 1px solid rgba(212,168,71,0.35);
  border-radius: 10px; margin-bottom: 14px;
}
.toolbar .title {
  font-family: "STKaiti", "KaiTi", serif;
  font-size: 20px; font-weight: 700; color: #f0cc6d; letter-spacing: 6px;
  text-shadow: 0 0 6px rgba(240,204,109,0.4);
}
.btn-tb {
  padding: 6px 14px; background: transparent;
  border: 1px solid var(--gold-dark); border-radius: 4px;
  color: #d8c9a8; font-size: 13px; cursor: pointer;
}
.btn-tb:hover { background: rgba(212,168,71,0.15); color: #f0cc6d; }

.tip {
  padding: 8px 14px; margin-bottom: 14px;
  font-size: 12px; color: #9a8b72;
  background: rgba(20,16,12,0.6);
  border-left: 2px solid rgba(212,168,71,0.5); border-radius: 0 6px 6px 0;
}
.tip b { color: #f0cc6d; }

.empty { text-align: center; padding: 48px; color: #6b5d4b; letter-spacing: 2px; }

.room-card {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 18px; margin-bottom: 10px;
  background: linear-gradient(155deg, rgba(43,32,23,0.85), rgba(20,16,12,0.9));
  border: 1px solid rgba(212,168,71,0.3); border-radius: 8px;
  cursor: pointer; transition: all .15s;
}
.room-card:hover { border-color: rgba(230,185,79,0.7); background: rgba(212,168,71,0.08); }
.rc-side { flex: 1; display: flex; align-items: center; gap: 8px; }
.rc-side:last-of-type { justify-content: flex-end; }
.rc-name { font-size: 14px; color: #e8dcc4; font-weight: 500; }
.rc-hp { font-size: 18px; font-weight: 700; color: #6ee26e; min-width: 30px; text-align: center; }
.rc-hp.low { color: #ff6e6e; }
.rc-mid { text-align: center; min-width: 90px; }
.rc-vs { display: block; font-size: 12px; color: #8a7b62; letter-spacing: 2px; }
.rc-turn { display: block; font-size: 11px; color: #b8a884; margin-top: 2px; }
.rc-btn {
  padding: 5px 14px; background: rgba(212,168,71,0.14);
  border: 1px solid rgba(212,168,71,0.5); border-radius: 4px;
  color: #f0cc6d; font-size: 12px; cursor: pointer; white-space: nowrap;
}

.watch-area {
  background: linear-gradient(155deg, rgba(43,32,23,0.85), rgba(20,16,12,0.9));
  border: 1px solid rgba(212,168,71,0.35); border-radius: 10px; overflow: hidden;
}
.w-head {
  display: flex; align-items: center; gap: 14px; padding: 12px 18px;
  background: rgba(0,0,0,0.35); border-bottom: 1px solid rgba(212,168,71,0.25);
}
.w-title { font-size: 15px; color: #e8dcc4; font-weight: 600; }
.w-title b { color: #e6b94f; margin: 0 6px; }
.w-turn { margin-left: auto; font-size: 12px; color: #b8a884; }
.over-tip {
  padding: 10px 18px; font-size: 13px; color: #f0cc6d;
  background: rgba(230,185,79,0.12); border-bottom: 1px solid rgba(212,168,71,0.25);
}

.board { padding: 14px 18px; display: flex; flex-direction: column; gap: 14px; }
.side { background: rgba(0,0,0,0.25); border-radius: 8px; padding: 10px 12px; }
.side-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 8px; }
.s-name { font-size: 14px; color: #e8dcc4; font-weight: 600; }
.s-name.cur { color: #f0cc6d; text-shadow: 0 0 6px rgba(240,204,109,0.5); }
.s-hp { font-size: 13px; color: #b8a884; }
.s-hp b { color: #6ee26e; font-size: 16px; }
.s-hp b.low { color: #ff6e6e; }
.s-mana { font-size: 12px; color: #8ab4e8; }
.s-meta { font-size: 11px; color: #8a7b62; }
.fatigue-tag {
  padding: 1px 7px; border-radius: 999px; font-size: 11px;
  color: #ffd8d8; background: rgba(163,45,45,.85); border: 1px solid rgba(247,193,193,.5);
}
.offline-tag {
  padding: 1px 7px; border-radius: 999px; font-size: 11px;
  color: #ffe0b0; background: rgba(150,100,20,.8); border: 1px solid rgba(250,199,117,.5);
}
.minions { display: flex; gap: 8px; flex-wrap: wrap; min-height: 92px; align-items: center; }
.no-minion { font-size: 12px; color: #6b5d4b; letter-spacing: 2px; }

.log-panel { border-top: 1px solid rgba(212,168,71,0.25); }
.lp-title {
  padding: 8px 18px; font-size: 12px; color: #f0cc6d; letter-spacing: 3px;
  background: rgba(0,0,0,0.25);
}
.lp-body { max-height: 200px; overflow-y: auto; padding: 10px 18px; }
.lp-line { font-size: 12px; color: #b8a884; line-height: 1.8; }
.lp-turn { display: inline-block; min-width: 28px; color: #8a7b62; }
.lp-empty { text-align: center; padding: 20px; color: #6b5d4b; }

@media (max-width: 680px) {
  .spectate { padding: 12px; }
  .toolbar { padding: 10px 12px; gap: 8px; }
  .toolbar .title { font-size: 16px; letter-spacing: 3px; }
  .room-card { flex-wrap: wrap; gap: 8px; padding: 12px; }
  .rc-side { flex-basis: 100%; justify-content: flex-start; }
  .rc-mid { min-width: auto; }
  .rc-btn { flex-basis: 100%; text-align: center; }
  .w-head { padding: 10px 12px; gap: 8px; }
  .w-title { font-size: 13px; }
  .board { padding: 10px 12px; }
  .side { padding: 8px; }
  .minions { min-height: 0; gap: 6px; }
  .lp-body { max-height: 150px; padding: 8px 12px; }
}
</style>
