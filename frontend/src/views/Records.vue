<template>
  <div class="records">
    <div class="toolbar">
      <button class="btn-tb" @click="$router.push('/lobby')">← 返回大厅</button>
      <span class="title">我 的 战 绩</span>
      <button class="btn-tb" @click="load">⟳ 刷新</button>
    </div>

    <!-- 统计卡 -->
    <div class="stat-panel">
      <div class="stat-card">
        <span class="stat-num gold">{{ me.rating }}</span>
        <span class="stat-lbl">天梯分</span>
      </div>
      <div class="stat-sep"></div>
      <div class="stat-card">
        <span class="stat-num">{{ me.winCount + me.loseCount }}</span>
        <span class="stat-lbl">总场次</span>
      </div>
      <div class="stat-sep"></div>
      <div class="stat-card">
        <span class="stat-num win">{{ me.winCount }}</span>
        <span class="stat-lbl">胜</span>
      </div>
      <div class="stat-sep"></div>
      <div class="stat-card">
        <span class="stat-num lose">{{ me.loseCount }}</span>
        <span class="stat-lbl">负</span>
      </div>
      <div class="stat-sep"></div>
      <div class="stat-card">
        <span class="stat-num gold">{{ winRate }}</span>
        <span class="stat-lbl">胜率</span>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <span class="fb-label">结果筛选</span>
      <div class="fb-opts">
        <button v-for="f in filters" :key="f.v"
                class="fb-opt" :class="{ on: result === f.v }" @click="setResult(f.v)">
          {{ f.label }}
        </button>
      </div>
      <span class="fb-total">共 {{ total }} 条</span>
    </div>

    <!-- 战绩表 -->
    <div class="list-panel">
      <table class="rec-table">
        <thead>
          <tr>
            <th>时 间</th>
            <th>对 手</th>
            <th>结 果</th>
            <th>回 合</th>
            <th>战 报</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, i) in list" :key="i" :class="{ win: row.result === 1, lose: row.result === -1 }">
            <td class="t-time">{{ fmt(row.createTime) }}</td>
            <td class="t-opp">{{ row.opponent }}</td>
            <td class="t-result">
              <span class="r-tag" :class="'r' + row.result">
                {{ row.result === 1 ? '胜' : row.result === -1 ? '负' : '平' }}
              </span>
            </td>
            <td class="t-rounds">{{ row.rounds }}</td>
            <td class="t-act">
              <button class="btn-replay" @click="openReplay(row.id)">▶ 回放</button>
              <button class="btn-replay code" @click="codeOpen = true">🔗 战报码</button>
            </td>
          </tr>
          <tr v-if="list.length === 0">
            <td colspan="5" class="t-empty">{{ result === 0 ? '暂无对局记录' : '该筛选条件下暂无记录' }}</td>
          </tr>
        </tbody>
      </table>

      <div class="pager" v-if="total > size">
        <button class="pg-btn" :disabled="page <= 1" @click="goPage(page - 1)">‹ 上一页</button>
        <span class="pg-info">{{ page }} / {{ maxPage }}</span>
        <button class="pg-btn" :disabled="page >= maxPage" @click="goPage(page + 1)">下一页 ›</button>
      </div>
    </div>

    <!-- 战报回放弹窗 -->
    <div v-if="replayOpen" class="rp-mask" @click.self="closeReplay">
      <div class="rp-box">
        <div class="rp-head">
          <span class="rp-title">战 报 回 放</span>
          <span class="rp-sub">
            {{ replay.me.nickname }} <b class="vs">VS</b> {{ replay.opponent.nickname }}
            <span class="r-tag" :class="'r' + replay.result">
              {{ replay.result === 1 ? '胜' : replay.result === -1 ? '负' : '平' }}
            </span>
          </span>
          <button class="rp-x" @click="closeReplay">×</button>
        </div>

        <div class="rp-ctrl">
          <button class="rp-btn" :disabled="curTurn <= 1" @click="gotoTurn(curTurn - 1)">⏮ 上回合</button>
          <button class="rp-btn primary" @click="togglePlay">{{ playing ? '⏸ 暂停' : '▶ 播放' }}</button>
          <button class="rp-btn" :disabled="curTurn >= maxTurn" @click="gotoTurn(curTurn + 1)">下回合 ⏭</button>
          <span class="rp-turn">第 {{ curTurn }} / {{ maxTurn }} 回合</span>
          <input class="rp-range" type="range" min="1" :max="maxTurn" v-model.number="curTurn" />
        </div>

        <div class="rp-body">
          <div v-for="e in visibleEntries" :key="e.turn + '-' + e.text" class="rp-line"
               :class="{ sys: e.actor === 'system', mine: isMine(e.actor) }">
            <span class="rp-turnno">T{{ e.turn }}</span>
            <span class="rp-who">{{ e.actor === 'system' ? '系统' : (isMine(e.actor) ? replay.me.nickname : replay.opponent.nickname) }}</span>
            <span class="rp-text">{{ e.text }}</span>
          </div>
          <div v-if="visibleEntries.length === 0" class="rp-empty">本局没有可用的战报日志</div>
        </div>

        <div class="rp-foot">
          <span>{{ fmt(replay.createTime) }} · 房间 {{ replay.roomId }} · 共 {{ replay.rounds }} 回合</span>
          <span class="rp-actions">
            <button v-if="!isSharedView" class="rp-btn" @click="shareReplay">🔗 分享本局</button>
            <button class="rp-btn" @click="gotoTurn(maxTurn)">⏭ 跳到结尾</button>
          </span>
        </div>
      </div>
    </div>

    <!-- 用战报码查看别人的对局 -->
    <div v-if="codeOpen" class="rp-mask" @click.self="codeOpen = false">
      <div class="rp-box code-box">
        <div class="rp-head">
          <span class="rp-title">输 入 战 报 码</span>
          <button class="rp-x" @click="codeOpen = false">×</button>
        </div>
        <div class="code-body">
          <p class="code-tip">把别人分享的战报码粘进来, 即可观看那一局的过程（脱敏, 不含任何一方的手牌）。</p>
          <input v-model="codeInput" class="code-input" placeholder="例如 RB1A3f9K7"
                 @keyup.enter="viewByCode" />
          <button class="rp-btn primary" :disabled="!codeInput.trim()" @click="viewByCode">查 看</button>
          <div v-if="codeErr" class="code-err">{{ codeErr }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/request'

const me = ref({ rating: 0, winCount: 0, loseCount: 0 })
const list = ref([])
const result = ref(0)     // 0全部 1胜 -1负 2平
const page = ref(1)
const size = 10
const total = ref(0)
const filters = [
  { v: 0, label: '全部' },
  { v: 1, label: '胜' },
  { v: -1, label: '负' },
  { v: 2, label: '平' }
]
const maxPage = computed(() => Math.max(1, Math.ceil(total.value / size)))
const winRate = computed(() => {
  const total = me.value.winCount + me.value.loseCount
  return total === 0 ? '0%' : Math.round((me.value.winCount / total) * 100) + '%'
})

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : ''
}

/* ===== 战报回放 ===== */
const replayOpen = ref(false)
const replay = ref({ me: {}, opponent: {}, timeline: [], turns: {}, result: 0, rounds: 0, roomId: '' })
const curTurn = ref(1)
const playing = ref(false)
let playTimer = null

const maxTurn = computed(() => {
  const ks = Object.keys(replay.value.turns || {}).map(Number).filter(n => !isNaN(n))
  return ks.length ? Math.max(...ks) : 1
})
const visibleEntries = computed(() =>
  (replay.value.timeline || []).filter(e => e.turn <= curTurn.value))
const isMine = (actor) => String(actor) === String((replay.value.me || {}).id)
/** 分享视图（别人的战报）不能再次分享，因为后端会校验参与者 */
const isSharedView = computed(() => (replay.value.me || {}).id === '__shared__')

async function openReplay(id) {
  try {
    replay.value = await http.get(`/records/${id}/replay`)
    curTurn.value = 1
    replayOpen.value = true
  } catch (e) { /* request.js 已统一提示 */ }
}
function closeReplay() { stopPlay(); replayOpen.value = false }
function gotoTurn(t) {
  curTurn.value = Math.min(maxTurn.value, Math.max(1, t))
  if (curTurn.value >= maxTurn.value) stopPlay()
}
function togglePlay() { playing.value ? stopPlay() : startPlay() }
function startPlay() {
  if (curTurn.value >= maxTurn.value) curTurn.value = 1
  playing.value = true
  playTimer = setInterval(() => {
    if (curTurn.value >= maxTurn.value) { stopPlay(); return }
    curTurn.value++
  }, 900)
}
function stopPlay() { playing.value = false; if (playTimer) { clearInterval(playTimer); playTimer = null } }

/* ===== 战报分享码 ===== */
const codeOpen = ref(false)
const codeInput = ref('')
const codeErr = ref('')

/** 生成并复制本局分享码 */
async function shareReplay() {
  try {
    const r = await http.get(`/records/${replay.value.id}/shareCode`)
    const code = r.code
    // 优先用剪贴板 API; 局域网 http 下浏览器可能禁用, 退化为手动提示
    try {
      await navigator.clipboard.writeText(code)
      ElMessage.success('战报码已复制：' + code)
    } catch (e) {
      ElMessage.success('战报码：' + code + '（请手动复制）')
    }
  } catch (e) { /* request.js 已统一提示 */ }
}

/** 用战报码打开别人的对局 */
async function viewByCode() {
  const code = codeInput.value.trim()
  if (!code) return
  codeErr.value = ''
  try {
    const data = await http.get(`/records/share/${encodeURIComponent(code)}`)
    // 分享视图没有 me/opponent 结构, 这里归一成回放弹窗需要的样子
    replay.value = {
      ...data,
      me: { id: '__shared__', nickname: data.side1 },
      opponent: { id: '__other__', nickname: data.side2 },
      result: 0,
      roomId: '（分享）'
    }
    // 分享视图剥掉了 actor, 全部当作系统行显示, 避免误标成"我方"
    replay.value.timeline = (data.timeline || []).map(e => ({ ...e, actor: 'system' }))
    curTurn.value = 1
    codeOpen.value = false
    replayOpen.value = true
    ElMessage.success('已载入分享战报（脱敏视图）')
  } catch (e) {
    codeErr.value = (e && e.msg) || '战报码无效'
  }
}

async function load() {
  const data = await http.get('/records/my', { params: { result: result.value, page: page.value, size } })
  me.value = data
  list.value = data.list
  total.value = data.total
}
function setResult(v) { result.value = v; page.value = 1; load() }
function goPage(p) { page.value = p; load() }
onMounted(load)
onUnmounted(() => { if (playTimer) clearInterval(playTimer) })   // 组件卸载时停掉播放定时器
</script>

<style scoped>
.records { max-width: 820px; margin: 0 auto; padding: 20px; }

.toolbar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 22px;
  background: linear-gradient(90deg, rgba(43,32,23,0.9), rgba(20,16,12,0.8));
  border: 1px solid rgba(212,168,71,0.35);
  border-radius: 10px;
  margin-bottom: 16px;
}
.toolbar .title {
  font-family: "STKaiti", "KaiTi", serif;
  font-size: 20px; font-weight: 700;
  color: #f0cc6d; letter-spacing: 6px;
  text-shadow: 0 0 6px rgba(240,204,109,0.4);
}
.btn-tb {
  padding: 6px 14px;
  background: transparent;
  border: 1px solid var(--gold-dark);
  border-radius: 4px;
  color: #d8c9a8;
  font-size: 13px; cursor: pointer;
  transition: all 0.15s;
}
.btn-tb:hover { background: rgba(212,168,71,0.15); color: #f0cc6d; }

/* === 统计卡 === */
.stat-panel {
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 0;
  padding: 18px;
  background: linear-gradient(155deg, rgba(43,32,23,0.85), rgba(20,16,12,0.9));
  border: 1px solid rgba(212,168,71,0.35);
  border-radius: 10px;
  box-shadow: 0 6px 24px rgba(0,0,0,0.5), inset 0 1px 0 rgba(240,204,109,0.2);
  margin-bottom: 18px;
}
.stat-card {
  flex: 1;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 6px 0;
  text-align: center;
}
.stat-num {
  font-size: 26px; font-weight: 700; color: #fff;
  font-family: "Microsoft YaHei", sans-serif;
}
.stat-num.gold { color: #f0cc6d; text-shadow: 0 0 6px rgba(240,204,109,0.4); }
.stat-num.win { color: #6ee26e; }
.stat-num.lose { color: #ff6e6e; }
.stat-lbl {
  font-size: 11px; color: #8a7b62; letter-spacing: 3px;
  margin-top: 4px;
}
.stat-sep {
  width: 1px; align-self: stretch;
  background: linear-gradient(180deg, transparent, rgba(212,168,71,0.3), transparent);
  margin: 4px 0;
}

/* === 筛选栏 === */
.filter-bar {
  display: flex; align-items: center; gap: 14px;
  padding: 10px 18px;
  background: linear-gradient(90deg, rgba(43,32,23,0.85), rgba(20,16,12,0.8));
  border: 1px solid rgba(212,168,71,0.28);
  border-radius: 8px;
  margin-bottom: 12px;
}
.fb-label { font-size: 12px; color: #8a7b62; letter-spacing: 2px; }
.fb-opts { display: flex; gap: 8px; }
.fb-opt {
  padding: 5px 16px;
  background: rgba(20,16,12,0.8);
  border: 1px solid rgba(120,80,30,0.5);
  border-radius: 14px;
  color: #c4b28a;
  font-size: 12px; cursor: pointer;
  transition: all 0.15s;
}
.fb-opt:hover { border-color: var(--gold); color: #f0cc6d; }
.fb-opt.on {
  border-color: #e6b94f;
  background: linear-gradient(160deg, rgba(230,185,79,0.22), rgba(20,16,12,0.85));
  color: #f0cc6d;
}
.fb-total { margin-left: auto; font-size: 12px; color: #8a7b62; }

/* === 分页 === */
.pager {
  display: flex; align-items: center; justify-content: center; gap: 18px;
  padding: 14px;
  border-top: 1px solid rgba(212,168,71,0.2);
}
.pg-btn {
  padding: 6px 18px;
  background: transparent;
  border: 1px solid var(--gold-dark);
  border-radius: 4px;
  color: #d8c9a8;
  font-size: 12px; cursor: pointer;
}
.pg-btn:hover:not(:disabled) { background: rgba(212,168,71,0.15); color: #f0cc6d; }
.pg-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.pg-info { font-size: 12px; color: #b8a884; }

/* === 战绩表 === */
.list-panel {
  background: linear-gradient(155deg, rgba(43,32,23,0.85), rgba(20,16,12,0.9));
  border: 1px solid rgba(212,168,71,0.35);
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 6px 24px rgba(0,0,0,0.5);
}
.rec-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.rec-table thead tr {
  background: rgba(0,0,0,0.45);
}
.rec-table th {
  padding: 12px 14px;
  text-align: left;
  font-size: 12px; letter-spacing: 3px;
  font-weight: 600;
  color: #f0cc6d;
  border-bottom: 1px solid rgba(212,168,71,0.35);
}
.rec-table tbody tr {
  border-bottom: 1px solid rgba(90,65,40,0.4);
  transition: background 0.15s;
}
.rec-table tbody tr:hover { background: rgba(212,168,71,0.08); }
.rec-table tbody tr.win { background: rgba(110,226,110,0.08); }
.rec-table tbody tr.lose { background: rgba(255,110,110,0.08); }
.rec-table tbody tr:last-child { border-bottom: none; }
.rec-table td {
  padding: 11px 14px;
  color: #c4b28a;
}
.t-time { font-size: 12px; color: #8a7b62; }
.t-opp { color: #e8dcc4; font-weight: 500; }
.t-rounds { color: #b8a884; text-align: center; }
.t-empty { text-align: center; padding: 28px; color: #6b5d4b; letter-spacing: 2px; }

.r-tag {
  display: inline-block;
  padding: 3px 14px;
  border-radius: 3px;
  font-size: 12px; font-weight: 600;
  letter-spacing: 2px;
}
.r-tag.r1 {
  background: rgba(110,226,110,0.22);
  color: #8eff8e;
  border: 1px solid rgba(110,226,110,0.5);
}
.r-tag.r-1 {
  background: rgba(255,110,110,0.22);
  color: #ff8e8e;
  border: 1px solid rgba(255,110,110,0.5);
}
.r-tag.r0 {
  background: rgba(180,180,180,0.22);
  color: #e0e0e0;
  border: 1px solid rgba(180,180,180,0.4);
}

/* === 战报列 === */
.t-act { text-align: center; }
.btn-replay {
  padding: 4px 12px;
  background: rgba(212,168,71,0.12);
  border: 1px solid rgba(212,168,71,0.5);
  border-radius: 4px;
  color: #f0cc6d; font-size: 12px; cursor: pointer;
  transition: all 0.15s;
}
.btn-replay:hover { background: rgba(212,168,71,0.28); }

/* === 回放弹窗 === */
.rp-mask {
  position: fixed; inset: 0; z-index: 2000;
  background: rgba(0,0,0,0.72);
  display: flex; align-items: center; justify-content: center;
  padding: 24px;
}
.rp-box {
  width: min(680px, 100%);
  max-height: 84vh;
  display: flex; flex-direction: column;
  background: linear-gradient(160deg, rgba(43,32,23,0.98), rgba(18,14,10,0.98));
  border: 1px solid rgba(212,168,71,0.45);
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0,0,0,0.7);
  overflow: hidden;
}
.rp-head {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid rgba(212,168,71,0.28);
  background: rgba(0,0,0,0.3);
}
.rp-title {
  font-family: "STKaiti", "KaiTi", serif;
  font-size: 18px; font-weight: 700; color: #f0cc6d; letter-spacing: 4px;
}
.rp-sub { font-size: 12px; color: #b8a884; display: flex; align-items: center; gap: 6px; }
.rp-sub .vs { color: #e6b94f; margin: 0 2px; }
.rp-x {
  margin-left: auto; width: 26px; height: 26px; line-height: 1;
  background: transparent; border: 1px solid rgba(212,168,71,0.4);
  border-radius: 4px; color: #d8c9a8; font-size: 16px; cursor: pointer;
}
.rp-x:hover { background: rgba(212,168,71,0.2); color: #f0cc6d; }

.rp-ctrl {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  padding: 10px 18px;
  border-bottom: 1px solid rgba(212,168,71,0.18);
}
.rp-btn {
  padding: 5px 14px;
  background: rgba(20,16,12,0.8);
  border: 1px solid rgba(120,80,30,0.55);
  border-radius: 4px; color: #c4b28a; font-size: 12px; cursor: pointer;
}
.rp-btn:hover:not(:disabled) { border-color: var(--gold); color: #f0cc6d; }
.rp-btn:disabled { opacity: .4; cursor: not-allowed; }
.rp-btn.primary {
  background: linear-gradient(160deg, rgba(230,185,79,0.24), rgba(20,16,12,0.85));
  border-color: #e6b94f; color: #f0cc6d;
}
.rp-turn { font-size: 12px; color: #b8a884; margin-left: 4px; }
.rp-range { flex: 1; min-width: 110px; accent-color: #e6b94f; }
.rp-actions { display: flex; align-items: center; gap: 8px; }

/* ===== 战报码弹窗 ===== */
.code-box { width: min(460px, 100%); }
.code-body { padding: 18px; display: flex; flex-direction: column; gap: 12px; }
.code-tip {
  margin: 0; font-size: 12px; line-height: 1.7; color: #b8a884;
}
.code-input {
  width: 100%; box-sizing: border-box;
  padding: 10px 14px;
  background: rgba(0,0,0,0.45);
  border: 1px solid rgba(120,80,30,0.6);
  border-radius: 6px;
  color: #f0e6cf;
  font-family: "Consolas", "Courier New", monospace;
  font-size: 16px; letter-spacing: 2px; text-align: center;
  outline: none;
}
.code-input::placeholder { color: #6b5f4a; letter-spacing: 1px; font-size: 13px; }
.code-input:focus { border-color: #e6b94f; box-shadow: 0 0 0 2px rgba(230,185,79,0.18); }
.code-body .rp-btn { width: 100%; padding: 9px 0; font-size: 13px; letter-spacing: 4px; }
.code-err {
  padding: 8px 12px;
  background: rgba(180,50,50,0.16);
  border: 1px solid rgba(220,80,80,0.45);
  border-radius: 5px;
  color: #ff9d9d; font-size: 12px; line-height: 1.6;
}

.rp-body { flex: 1; overflow-y: auto; padding: 12px 18px; min-height: 180px; }
.rp-line {
  display: flex; align-items: baseline; gap: 8px;
  padding: 6px 8px; margin-bottom: 4px;
  border-left: 2px solid rgba(212,168,71,0.25);
  border-radius: 0 4px 4px 0;
  font-size: 13px; color: #d8c9a8;
  animation: rpIn .25s ease-out;
}
@keyframes rpIn { from { opacity: 0; transform: translateX(-6px); } to { opacity: 1; transform: none; } }
.rp-line.sys { border-left-color: rgba(150,150,150,.5); color: #9a9a9a; font-style: italic; }
.rp-line.mine { border-left-color: #6ee26e; background: rgba(110,226,110,0.06); }
.rp-turnno { font-size: 11px; color: #8a7b62; min-width: 26px; }
.rp-who { font-size: 11px; color: #e6b94f; min-width: 72px; }
.rp-text { flex: 1; line-height: 1.5; }
.rp-empty { text-align: center; padding: 40px; color: #6b5d4b; letter-spacing: 2px; }

.rp-foot {
  display: flex; align-items: center; justify-content: space-between; gap: 10px;
  padding: 10px 18px; border-top: 1px solid rgba(212,168,71,0.22);
  font-size: 11px; color: #8a7b62; background: rgba(0,0,0,0.25);
}

@media (max-width: 680px) {
  .rp-mask { padding: 12px; align-items: flex-start; }
  .rp-box { max-height: 90vh; }
  .rp-head { flex-wrap: wrap; gap: 8px; padding: 12px; }
  .rp-title { font-size: 16px; letter-spacing: 2px; }
  .rp-ctrl { padding: 10px 12px; gap: 6px; }
  .rp-btn { padding: 5px 10px; font-size: 12px; }
  .rp-range { width: 100%; flex-basis: 100%; margin-top: 2px; }
  .rp-body { padding: 10px 12px; }
  .rp-line { flex-wrap: wrap; gap: 4px; font-size: 12px; }
  .rp-who { min-width: 0; }
  .rp-foot { padding: 10px 12px; font-size: 10px; }
  .rp-actions { gap: 6px; }
  .code-body { padding: 14px; }
  .code-input { font-size: 14px; letter-spacing: 1px; }
  .t-act .btn-replay { padding: 4px 8px; font-size: 11px; }
}
</style>
