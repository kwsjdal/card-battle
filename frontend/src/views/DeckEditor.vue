<template>
  <div class="deck">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <div class="tl-left">
        <button class="btn-tb" @click="$router.push('/lobby')">← 返回大厅</button>
      </div>
      <div class="tl-center">
        <input class="deck-name" v-model="deckName" placeholder="卡组名称" />
        <span class="deck-count">已选 <b :class="{ warn: deck.length > 30, ok: deck.length === 30 }">{{ deck.length }}</b> / 30
          <span class="tip">(同名上限 {{ perLimit }} 张)</span>
        </span>
      </div>
      <div class="tl-right">
        <button class="btn-act" @click="autoFill" :disabled="deck.length >= 30" title="按费用曲线自动补齐到 30 张">一键补满</button>
        <button class="btn-act" @click="clearDeck" :disabled="!deck.length">清空</button>
        <button class="btn-act code" :disabled="!editingId" @click="exportCode"
                title="生成这副卡组的分享码, 发给别人即可抄作业">⇪ 分享码</button>
        <button class="btn-act code" @click="codeOpen = true"
                title="粘贴别人的卡组分享码, 一键导入">⇩ 导入码</button>
        <button class="btn-save-as" :disabled="deck.length !== 30" @click="saveAsNew">另存为新卡组</button>
        <button class="btn-save" :disabled="deck.length !== 30" @click="save">
          {{ editingId ? '更 新 卡 组' : '保 存 卡 组' }}
        </button>
      </div>
    </div>

    <!-- 我的套牌列表 -->
    <div class="deck-list-bar">
      <span class="dl-title">我的套牌</span>
      <div v-for="d in decks" :key="d.id" class="deck-chip"
           :class="{ active: d.id === editingId, battle: d.isDefault === 1 }" @click="loadDeck(d)">
        <span v-if="d.isDefault === 1" class="chip-crown" title="当前出战卡组">👑</span>
        <span class="chip-name" @dblclick.stop="renameDeck(d)">{{ d.name }}</span>
        <span class="chip-num" :class="{ bad: deckSize(d) !== 30 }">{{ deckSize(d) }}/30</span>
        <span class="chip-del" title="删除该卡组" @click.stop="removeDeck(d)">×</span>
      </div>
      <button class="deck-chip new" @click="newDeck">＋ 新建卡组</button>
      <div class="dl-right">
        <button class="btn-mini" :disabled="!editingId" @click="copyDeck" title="复制当前卡组为一新套牌">复制</button>
        <button class="btn-mini battle" :disabled="!editingId" @click="setDefault"
                title="匹配对局时使用这套卡组">设为出战</button>
        <span class="dl-count" v-if="decks.length">{{ decks.length }} 套</span>
      </div>
    </div>

    <!-- ===== 卡组区(吸顶: 滚到卡牌库底部也能拖进来) ===== -->
    <div class="deck-panel">
      <div class="deck-slot-bar" :class="{ 'drag-over': dragOver }"
           @dragover.prevent="dragOver = true"
           @dragleave="dragOver = false"
           @drop.prevent="onDropToDeck">
        <template v-for="i in 30" :key="'slot'+i">
          <div v-if="deck[i-1] && getCard(deck[i-1])" class="slot-wrap"
               :class="{ 'last-of-duplicate': isLastDup(i-1) }"
               :title="(getCard(deck[i-1])?.name || '') + ' · 单击看档案 / 右击移除, × 移除'"
               @click="openDetail(getCard(deck[i-1]))"
               @contextmenu.prevent="removeAt(i-1)">
            <CardFace :card="getCard(deck[i-1])" size="xs" />
            <span class="slot-del" title="从卡组移除" @click.stop="removeAt(i-1)">×</span>
          </div>
          <div v-else class="slot empty" :class="{ 'drag-over': dragOver }">
            <span class="slot-empty">{{ i }}</span>
          </div>
        </template>
        <div v-if="deck.length === 0" class="deck-hint">把下方卡牌<b>拖到这里</b>加入卡组（也可<b>右击</b>卡面，或点卡面右下角 ＋）</div>
      </div>

      <!-- 卡组统计 -->
      <div class="stats" v-if="deck.length">
        <div class="st-block">
          <span class="st-title">费用曲线</span>
          <div class="curve">
            <div v-for="n in 8" :key="'c'+n" class="cv-col">
              <span class="cv-num">{{ curve[n-1] || '' }}</span>
              <span class="cv-bar" :style="{ height: Math.min(curve[n-1] * 7, 42) + 'px' }"></span>
              <span class="cv-x">{{ n === 8 ? '7+' : n - 1 }}</span>
            </div>
          </div>
        </div>
        <div class="st-block st-mid">
          <div class="kv"><span>平均费用</span><b>{{ avgCost }}</b></div>
          <div class="kv"><span>合计 ⚔</span><b>{{ sumAtk }}</b></div>
          <div class="kv"><span>合计 ❤</span><b>{{ sumHp }}</b></div>
        </div>
        <div class="st-block st-prof">
          <span class="st-title">职业分布</span>
          <div v-for="p in profStats" :key="p.name" class="pf-row">
            <span class="pf-name">{{ p.name }}</span>
            <span class="pf-bar"><i :style="{ width: (p.n / deck.length * 100) + '%' }"></i></span>
            <span class="pf-n">{{ p.n }}</span>
          </div>
          <span v-if="!profStats.length" class="pf-empty">—</span>
        </div>
      </div>
    </div>

    <!-- 卡牌库 -->
    <div class="library">
      <div class="lib-head">
        <h3 class="section-title">🃏 卡 牌 库</h3>
        <div class="filters">
          <input class="f-search" v-model="kw" placeholder="搜索干员 / 技能" />
          <select class="f-sel" v-model="fCost">
            <option value="">全部费用</option>
            <option v-for="n in 7" :key="n" :value="String(n)">{{ n === 7 ? '7+' : n }} 费</option>
          </select>
          <select class="f-sel" v-model="fProf">
            <option value="">全部职业</option>
            <option v-for="p in profs" :key="p" :value="p">{{ p }}</option>
          </select>
          <select class="f-sel" v-model="sortBy">
            <option value="cost">按费用↑</option>
            <option value="costDesc">按费用↓</option>
            <option value="atk">按攻击↓</option>
            <option value="hp">按生命↓</option>
            <option value="name">按名称</option>
          </select>
          <label class="f-chk"><input type="checkbox" v-model="hideFull" /> 隐藏已加满</label>
          <span class="f-count">{{ filtered.length }} / {{ cards.length }}</span>
        </div>
      </div>

      <div class="cards">
        <div v-for="c in filtered" :key="c.id" class="card-item"
             draggable="true"
             @dragstart="onDragStart($event, c)"
             @dragend="dragId = null"
             @click="openDetail(c)"
             @contextmenu.prevent="openCtx($event, c)">
          <CardFace :card="c" size="sm"
                    :count="dupCount(c.id) > 0 ? dupCount(c.id) + '/' + perLimit : ''"
                    :class="{ 'dragging': dragId === c.id }"
                    title="拖到上方卡组 · 右击加入 · 单击看档案" />
          <span class="add-ico" title="加入卡组" @click.stop="add(c)">＋</span>
        </div>
        <div v-if="!filtered.length" class="lib-empty">没有符合条件的干员</div>
      </div>
    </div>

    <div class="corner-tag">共 {{ cards.length }} 张可用 ·
      <b>拖动 / 右击 / ＋</b> 加入卡组 · 卡组内单击看档案、右击或 × 移除</div>

    <!-- 右击菜单 -->
    <div v-if="ctx.show" class="ctx-menu" :style="{ left: ctx.x + 'px', top: ctx.y + 'px' }" @click.stop>
      <div class="ctx-title">{{ ctx.card?.name }}
        <span class="ctx-sub">{{ dupCount(ctx.card?.id) }}/{{ perLimit }} 张</span>
      </div>
      <div class="ctx-item" :class="{ off: !canAdd(ctx.card) }" @click="ctxAdd(1)">加入 1 张</div>
      <div class="ctx-item" :class="{ off: !canAdd(ctx.card) }" @click="ctxAdd(perLimit - dupCount(ctx.card?.id))">
        加满至上限（{{ perLimit }} 张）
      </div>
      <div class="ctx-item" :class="{ off: !dupCount(ctx.card?.id) }" @click="ctxRemove">从卡组移除 1 张</div>
      <div class="ctx-item" @click="openDetail(ctx.card)">查看干员档案</div>
    </div>

    <!-- 轻提示 -->
    <transition name="fade">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>

    <!-- 干员档案 -->
    <CardDetail v-model="detailOpen" :card="detailCard" />

    <!-- 卡组分享码: 导入 -->
    <el-dialog v-model="codeOpen" title="🔗 导入卡组分享码" width="440px" center>
      <div class="code-body">
        <p class="code-tip">
          粘贴别人给你的卡组分享码，一键抄走这副卡组。<br />
          你还没有解锁的卡牌会自动用已有卡牌补齐到 30 张。
        </p>
        <textarea class="code-input" v-model="codeText" rows="3" spellcheck="false"
                  placeholder="例如 CB1.01A2b3C4d5.6E7f8G9h0.K"></textarea>
        <input class="code-name" v-model="codeName" placeholder="保存成什么名字（留空自动命名）" />
      </div>
      <template #footer>
        <button class="btn-plain-sm" @click="codeOpen = false">取 消</button>
        <button class="btn-main-sm" :disabled="importing" @click="importCode">
          {{ importing ? '导入中…' : '导入为我的卡组' }}
        </button>
      </template>
    </el-dialog>

    <!-- 卡组分享码: 导出 -->
    <el-dialog v-model="shareOpen" :title="'🔗 分享「' + (deckName || '卡组') + '」'" width="440px" center>
      <div class="code-body">
        <p class="code-tip">把这串码发给朋友，他粘贴到「⇩ 导入码」里就能抄走这副卡组。</p>
        <div class="code-box" @click="copyCode" title="点击复制">{{ shareCode }}</div>
        <div class="code-credit">编码方式：卡种与张数打包成 3 字符一组 + 末尾校验位，抄漏一个字符会被拒绝</div>
      </div>
      <template #footer>
        <button class="btn-plain-sm" @click="shareOpen = false">关 闭</button>
        <button class="btn-main-sm" @click="copyCode">复制分享码</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/request'
import { loadSkinMap } from '../api/skin'
import CardDetail from '../components/CardDetail.vue'
import CardFace from '../components/CardFace.vue'

const cards = ref([])
const deck = ref([])
const deckName = ref('我的第一套卡组')
const decks = ref([])        // 已保存的多套卡组
const editingId = ref(null)  // 当前正在编辑的卡组 id(null=新建)
const detailOpen = ref(false)
const detailCard = ref({})
function openDetail(c) { detailCard.value = c; detailOpen.value = true }

/* ===== 轻提示 ===== */
const toast = ref('')
let toastTimer = null
function say(msg) {
  toast.value = msg
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toast.value = '' }, 1500)
}

async function load() { cards.value = await http.get('/cards') }
async function loadDecks() {
  decks.value = await http.get('/decks')
  if (decks.value.length && editingId.value === null) loadDeck(decks.value[0])
}
function loadDeck(d) {
  editingId.value = d.id
  deckName.value = d.name
  try { deck.value = JSON.parse(d.cardIds) || [] } catch { deck.value = [] }
}
function newDeck() {
  editingId.value = null
  deckName.value = '新卡组 ' + (decks.value.length + 1)
  deck.value = []
}
function deckSize(d) { try { return (JSON.parse(d.cardIds) || []).length } catch { return 0 } }

async function save() {
  try {
    const r = await http.post('/decks', { id: editingId.value, name: deckName.value, cardIds: deck.value })
    editingId.value = r
    await loadDecks()
    say('保存成功')
  } catch (e) { /* 失败原因已由 request 拦截器弹出提示 */ }
}
async function saveAsNew() {
  try {
    const r = await http.post('/decks', { name: deckName.value, cardIds: deck.value })
    editingId.value = r
    await loadDecks()
    say('已另存为新卡组')
  } catch (e) { /* 同上 */ }
}
async function removeDeck(d) {
  if (!confirm(`确定删除卡组「${d.name}」？`)) return
  await http.delete('/decks/' + d.id)
  if (editingId.value === d.id) newDeck()
  await loadDecks()
  say('已删除「' + d.name + '」')
}
async function renameDeck(d) {
  const n = prompt('重命名卡组', d.name)
  if (!n || n === d.name) return
  const ids = JSON.parse(d.cardIds || '[]')
  await http.post('/decks', { id: d.id, name: n, cardIds: ids })
  if (editingId.value === d.id) deckName.value = n
  await loadDecks()
  say('已重命名')
}
async function copyDeck() {
  if (!editingId.value) return
  const name = (deckName.value || '卡组') + ' 副本'
  const r = await http.post('/decks', { name, cardIds: deck.value })
  editingId.value = r
  deckName.value = name
  await loadDecks()
  say('已复制为「' + name + '」')
}
async function setDefault() {
  if (!editingId.value) return say('请先保存这套卡组')
  await http.post('/decks/default', { id: editingId.value })
  await loadDecks()
  say('已设为出战卡组 👑')
}

/* ===== 卡组分享码(导入 / 导出) ===== */
const codeOpen = ref(false)
const shareOpen = ref(false)
const codeText = ref('')
const codeName = ref('')
const shareCode = ref('')
const importing = ref(false)

async function exportCode() {
  if (!editingId.value) return say('请先保存这套卡组')
  try {
    const r = await http.get('/decks/code', { params: { deckId: editingId.value } })
    shareCode.value = r.code
    shareOpen.value = true
  } catch (e) { /* 失败原因已由 request 拦截器弹出提示 */ }
}
async function importCode() {
  const code = codeText.value.trim()
  if (!code) return say('请先粘贴卡组分享码')
  importing.value = true
  try {
    const r = await http.post('/decks/code', { code, name: codeName.value })
    codeOpen.value = false
    codeText.value = ''
    codeName.value = ''
    await loadDecks()
    const mine = decks.value.find(d => d.id === r.deckId)
    if (mine) loadDeck(mine)
    if (r.replaced > 0) {
      ElMessage.success(`导入成功，${r.replaced} 张你没有的卡已自动补齐`)
      say(`导入成功：${r.replaced} 张缺失卡已替换`)
    } else {
      ElMessage.success('导入成功：「' + r.name + '」')
      say('导入成功，30 张全部到位')
    }
  } finally { importing.value = false }
}
async function copyCode() {
  if (!shareCode.value) return
  try {
    await navigator.clipboard.writeText(shareCode.value)
    say('分享码已复制')
  } catch (e) {
    say('复制失败，请手动选中复制')
  }
}

onMounted(async () => { await load(); await loadSkinMap(); await loadDecks() })
// 每张上限: 卡池 15 种以上限 2 张; 不足时动态放宽(12种->3张), 保证能凑满30
const perLimit = computed(() => Math.max(2, Math.ceil(30 / Math.max(cards.value.length, 1))))
function getCard(id) { return cards.value.find(c => c.id === id) }
function dupCount(id) { return id ? deck.value.filter(i => i === id).length : 0 }
function isLastDup(idx) {
  const id = deck.value[idx]
  if (!id) return false
  return deck.value.slice(0, idx).filter(i => i === id).length === 1
}

function canAdd(c) {
  return !!c && deck.value.length < 30 && dupCount(c.id) < perLimit.value
}
function add(c, n) {
  let num = n || 1
  let added = 0
  while (num-- > 0) {
    if (deck.value.length >= 30) { say('卡组已满 30 张'); break }
    if (dupCount(c.id) >= perLimit.value) { say(`「${c.name}」已达上限 ${perLimit.value} 张`); break }
    deck.value.push(c.id)
    added++
  }
  if (added) say(`已加入「${c.name}」×${added}（当前 ${deck.value.length}/30）`)
  return added
}
/** 一键补满: 按费用从低到高补齐到 30 张 */
function autoFill() {
  if (!cards.value.length) return
  const pool = [...cards.value].sort((a, b) => a.cost - b.cost)
  let guard = 0
  while (deck.value.length < 30 && guard++ < 200) {
    const c = pool.find(x => dupCount(x.id) < perLimit.value)
    if (!c) break
    deck.value.push(c.id)
  }
  say(`已补满至 ${deck.value.length} 张`)
}
function clearDeck() {
  if (!deck.value.length) return
  if (!confirm('清空当前卡组的全部卡牌？')) return
  deck.value = []
  say('已清空')
}
/** 从卡组移除指定槽位的卡 */
function removeAt(idx) {
  const c = getCard(deck.value[idx])
  deck.value.splice(idx, 1)
  if (c) say(`已移除「${c.name}」（当前 ${deck.value.length}/30）`)
}

/* ===== 拖拽加入卡组 ===== */
const dragId = ref(null)
const dragOver = ref(false)
function onDragStart(e, c) {
  dragId.value = c.id
  e.dataTransfer.effectAllowed = 'copy'
  e.dataTransfer.setData('text/plain', String(c.id))
}
function onDropToDeck(e) {
  dragOver.value = false
  const id = Number(e.dataTransfer.getData('text/plain') || dragId.value)
  if (!id) return
  const c = cards.value.find(x => x.id === id)
  if (!c) return
  try { add(c) } catch (err) { /* noop */ }
  dragId.value = null
}

/* ===== 右击菜单 ===== */
const ctx = ref({ show: false, x: 0, y: 0, card: null })
function openCtx(e, c) {
  const w = 168, h = 190
  ctx.value = {
    show: true,
    x: Math.min(e.clientX, window.innerWidth - w - 8),
    y: Math.min(e.clientY, window.innerHeight - h - 8),
    card: c
  }
}
function closeCtx() { if (ctx.value.show) ctx.value.show = false }
function ctxAdd(n) {
  const c = ctx.value.card
  closeCtx()
  if (c && n > 0) add(c, n)
}
function ctxRemove() {
  const c = ctx.value.card
  closeCtx()
  if (!c) return
  const i = deck.value.lastIndexOf(c.id)
  if (i >= 0) removeAt(i)
}
onMounted(() => document.addEventListener('click', closeCtx))
onUnmounted(() => document.removeEventListener('click', closeCtx))

/* ===== 卡牌库筛选/排序 ===== */
const kw = ref('')
const fCost = ref('')
const fProf = ref('')
const sortBy = ref('cost')
const hideFull = ref(false)
const PROF_LIST = ['先锋', '近卫', '重装', '狙击', '术师', '医疗', '辅助', '特种']
const profs = computed(() => {
  const s = new Set(cards.value.map(professionOf).filter(Boolean))
  return PROF_LIST.filter(p => s.has(p))
})
function professionOf(c) {
  const d = (c && c.description) || ''
  const m = d.match(/(先锋|近卫|重装|狙击|术师|医疗|辅助|特种|召唤)干员/)
  return m ? m[1] : ''
}
const filtered = computed(() => {
  let list = cards.value
  const k = kw.value.trim()
  if (k) {
    list = list.filter(c =>
      (c.name || '').includes(k) ||
      ((c.description || '').includes(k)) ||
      ((c.effect || '').includes(k)))
  }
  if (fCost.value) {
    const v = Number(fCost.value)
    list = list.filter(c => (v === 7 ? c.cost >= 7 : c.cost === v))
  }
  if (fProf.value) list = list.filter(c => professionOf(c) === fProf.value)
  if (hideFull.value) list = list.filter(c => dupCount(c.id) < perLimit.value)
  const arr = [...list]
  const by = sortBy.value
  arr.sort((a, b) => {
    if (by === 'cost') return a.cost - b.cost || a.id - b.id
    if (by === 'costDesc') return b.cost - a.cost || a.id - b.id
    if (by === 'atk') return b.attack - a.attack || a.cost - b.cost
    if (by === 'hp') return b.hp - a.hp || a.cost - b.cost
    return String(a.name).localeCompare(String(b.name), 'zh')
  })
  return arr
})

/* ===== 卡组统计 ===== */
const deckCards = computed(() => deck.value.map(getCard).filter(Boolean))
const curve = computed(() => {
  const a = new Array(8).fill(0)
  for (const c of deckCards.value) {
    const i = c.cost >= 7 ? 7 : c.cost
    a[i]++
  }
  return a
})
const avgCost = computed(() => {
  if (!deckCards.value.length) return '0.0'
  return (deckCards.value.reduce((s, c) => s + (c.cost || 0), 0) / deckCards.value.length).toFixed(1)
})
const sumAtk = computed(() => deckCards.value.reduce((s, c) => s + (c.attack || 0), 0))
const sumHp = computed(() => deckCards.value.reduce((s, c) => s + (c.hp || 0), 0))
const profStats = computed(() => {
  const m = new Map()
  for (const c of deckCards.value) {
    const p = professionOf(c) || '其他'
    m.set(p, (m.get(p) || 0) + 1)
  }
  return [...m.entries()].map(([name, n]) => ({ name, n })).sort((a, b) => b.n - a.n)
})
</script>

<style scoped>
.deck { max-width: 1100px; margin: 0 auto; padding: 20px; }

/* === 顶部工具栏(自适应换行, 窄窗口不挤爆) === */
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  background: linear-gradient(90deg, rgba(43,32,23,0.9), rgba(20,16,12,0.8));
  border: 1px solid rgba(212,168,71,0.35);
  border-radius: 10px;
  margin-bottom: 14px;
}
.tl-left { flex: 0 0 auto; }
.tl-center {
  flex: 1 1 260px;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}
.tl-right { display: flex; gap: 10px; flex: 0 0 auto; flex-wrap: wrap; }
.btn-save-as {
  padding: 8px 14px;
  background: transparent;
  border: 1px solid var(--gold-dark);
  border-radius: 4px;
  color: #d8c9a8;
  font-size: 12px;
  letter-spacing: 2px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.btn-save-as:hover:not(:disabled) { background: rgba(212,168,71,0.15); color: #f0cc6d; }
.btn-save-as:disabled { opacity: 0.45; cursor: not-allowed; }
.btn-act {
  padding: 8px 12px;
  background: transparent;
  border: 1px solid rgba(120,80,30,0.7);
  border-radius: 4px;
  color: #b8a884;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.btn-act:hover:not(:disabled) { border-color: var(--gold); color: #f0cc6d; }
.btn-act:disabled { opacity: 0.4; cursor: not-allowed; }
/* 分享码按钮: 青色, 与"保存/清空"区分 */
.btn-act.code { border-color: rgba(79, 214, 230, 0.5); color: #6fd8f0; }
.btn-act.code:hover:not(:disabled) {
  border-color: #4fd6e6;
  background: rgba(79, 214, 230, 0.14);
  box-shadow: 0 0 10px rgba(79, 214, 230, 0.35);
}

/* === 分享码弹窗 === */
.code-body { padding: 2px; }
.code-tip { margin: 0 0 12px; font-size: 12px; color: #8d97a3; line-height: 1.8; }
.code-input {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  background: rgba(8, 10, 12, 0.85);
  border: 1px solid var(--line);
  border-radius: 4px;
  color: #c8ecff;
  font-family: "Consolas", "Microsoft YaHei", monospace;
  font-size: 13px;
  letter-spacing: 1px;
  resize: vertical;
}
.code-input:focus { outline: none; border-color: #4fd6e6; box-shadow: 0 0 10px rgba(79, 214, 230, 0.3); }
.code-name {
  width: 100%;
  box-sizing: border-box;
  margin-top: 10px;
  padding: 8px 12px;
  background: rgba(8, 10, 12, 0.85);
  border: 1px solid var(--line);
  border-radius: 4px;
  color: #d9dee5;
  font-size: 13px;
}
.code-name:focus { outline: none; border-color: var(--gold); }
.code-box {
  padding: 14px 16px;
  background: linear-gradient(150deg, rgba(79, 214, 230, 0.12), rgba(8, 10, 12, 0.9));
  border: 1px dashed rgba(79, 214, 230, 0.55);
  border-radius: 4px;
  color: #c8ecff;
  font-family: "Consolas", "Microsoft YaHei", monospace;
  font-size: 15px;
  letter-spacing: 2px;
  word-break: break-all;
  text-align: center;
  cursor: pointer;
  user-select: all;
  transition: all 0.15s;
}
.code-box:hover { border-color: #4fd6e6; box-shadow: 0 0 14px rgba(79, 214, 230, 0.35); }
.code-credit { margin-top: 12px; font-size: 11px; color: #4c5561; line-height: 1.7; }

/* 弹窗内的小按钮(与大厅保持同一套) */
.btn-main-sm, .btn-plain-sm {
  padding: 7px 16px; font-size: 12px; cursor: pointer; border-radius: 3px; font-weight: 600;
}
.btn-main-sm {
  background: linear-gradient(180deg, #f2ad36, #c98a1f);
  color: #1a1005; border: 1px solid #7a5416;
}
.btn-main-sm:hover:not(:disabled) { box-shadow: 0 0 12px rgba(240, 165, 42, 0.6); }
.btn-main-sm:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-plain-sm { background: transparent; color: #8d97a3; border: 1px solid var(--line); }
.btn-plain-sm:hover { color: #f0cc6d; border-color: var(--gold); }

/* === 我的套牌列表 === */
.deck-list-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px 16px;
  background: rgba(0,0,0,0.35);
  border: 1px solid rgba(120,80,30,0.4);
  border-radius: 8px;
  margin-bottom: 12px;
}
.dl-title {
  font-size: 13px;
  color: #f0cc6d;
  letter-spacing: 3px;
  font-weight: 600;
  margin-right: 4px;
}
.deck-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(20,16,12,0.75);
  border: 1px solid rgba(120,80,30,0.55);
  border-radius: 16px;
  color: #d8c9a8;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.deck-chip:hover { border-color: var(--gold); color: #f0cc6d; }
.deck-chip.active {
  border-color: #e6b94f;
  background: linear-gradient(160deg, rgba(230,185,79,0.22), rgba(20,16,12,0.85));
  color: #f0cc6d;
  box-shadow: 0 0 10px rgba(230,185,79,0.3);
}
.deck-chip.battle { border-color: #6fd8f0; box-shadow: 0 0 8px rgba(111,216,240,0.35); }
.chip-crown { font-size: 12px; }
.chip-num { font-size: 11px; color: #7ed957; }
.chip-num.bad { color: #e2554f; }
.deck-chip.new { border-style: dashed; color: #9a8b72; }
.deck-chip.new:hover { color: #f0cc6d; }
.chip-name { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chip-del {
  display: inline-flex; align-items: center; justify-content: center;
  width: 16px; height: 16px;
  border-radius: 50%;
  background: rgba(226,85,79,0.2);
  color: #ff9a94;
  font-size: 12px; line-height: 1;
}
.chip-del:hover { background: rgba(226,85,79,0.5); color: #fff; }
.dl-right { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.btn-mini {
  padding: 5px 12px;
  background: rgba(20,16,12,0.8);
  border: 1px solid rgba(120,80,30,0.6);
  border-radius: 14px;
  color: #d8c9a8; font-size: 12px; cursor: pointer;
  transition: all 0.15s;
}
.btn-mini:hover:not(:disabled) { border-color: var(--gold); color: #f0cc6d; }
.btn-mini.battle:hover:not(:disabled) { border-color: #6fd8f0; color: #6fd8f0; }
.btn-mini:disabled { opacity: 0.4; cursor: not-allowed; }
.dl-count { font-size: 11px; color: #8a7b62; }
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

.deck-name {
  padding: 6px 12px;
  background: rgba(20,16,12,0.85);
  border: 1px solid var(--leather-3);
  border-radius: 4px;
  color: #e8dcc4;
  font-size: 14px;
  flex: 1 1 150px;
  min-width: 110px;
  max-width: 220px;
  outline: none;
}
.deck-name:focus { border-color: var(--gold); }
.deck-count { font-size: 13px; color: #b8a884; white-space: nowrap; }
.deck-count b { color: #f0cc6d; font-size: 16px; }
.deck-count b.ok { color: #7ed957; }
.deck-count b.warn { color: var(--blood); }
.deck-count .tip { color: #8a7b62; font-size: 11px; margin-left: 4px; }

.btn-save {
  padding: 8px 18px;
  background: linear-gradient(180deg, #e6b94f, #a5842f);
  border: 1px solid #7a5f1e;
  border-radius: 4px;
  color: #1a1005;
  font-weight: 700;
  letter-spacing: 3px;
  font-size: 13px; cursor: pointer;
  transition: all 0.15s;
  box-shadow: 0 3px 10px rgba(212,168,71,0.4);
  white-space: nowrap;
}
.btn-save:hover:not(:disabled) { box-shadow: 0 4px 16px rgba(240,204,109,0.6); transform: translateY(-1px); }
.btn-save:disabled { opacity: 0.45; cursor: not-allowed; }

/* === 卡组区(吸顶: 页面滚动时始终留在顶部, 卡牌库底部的卡也能拖进来) === */
.deck-panel {
  position: sticky;
  top: 0;
  z-index: 30;
  padding: 10px 0 12px;
  background: linear-gradient(180deg, rgba(18,14,10,0.98) 82%, rgba(18,14,10,0.75));
  border-bottom: 1px solid rgba(212,168,71,0.25);
}
.deck-slot-bar {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-content: flex-start;
  gap: 5px;
  min-height: 80px;
  padding: 12px;
  background: rgba(0,0,0,0.35);
  border: 1px solid rgba(120,80,30,0.4);
  border-radius: 8px;
  transition: background 0.15s, border-color 0.15s;
}
/* 拖拽悬停高亮 */
.deck-slot-bar.drag-over {
  background: rgba(60,110,170,0.25);
  border-color: #6fd8f0;
  box-shadow: inset 0 0 20px rgba(110,214,240,0.25);
}
.slot-wrap {
  position: relative;
  width: 50px; height: 70px;
  border-radius: 4px;
  cursor: pointer;
  transition: transform 0.15s;
}
.slot-wrap:hover { transform: translateY(-3px); }
.slot-wrap.last-of-duplicate {
  box-shadow: 0 0 0 2px var(--poison), 0 0 8px rgba(126,217,87,0.5);
  border-radius: 4px;
}
.slot {
  width: 50px; height: 70px;
  border: 1px dashed #5a4128;
  border-radius: 4px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(20,16,12,0.6);
}
.slot.empty.drag-over { border-color: #6fd8f0; background: rgba(60,110,170,0.2); }
/* 移除按钮: 悬浮才出现 */
.slot-del {
  position: absolute; top: -6px; right: -6px; z-index: 6;
  width: 17px; height: 17px; border-radius: 50%;
  background: #8f2020; color: #ffd9d9;
  font-size: 12px; line-height: 15px; text-align: center;
  border: 1px solid #331010;
  opacity: 0; transition: opacity 0.15s;
}
.slot-wrap:hover .slot-del { opacity: 1; }
.slot-del:hover { background: #c93030; }
.slot-empty { font-size: 11px; color: #6b5d4b; }
/* 空卡组提示 */
.deck-hint {
  width: 100%;
  text-align: center;
  padding: 6px 0 2px;
  font-size: 12px;
  color: #8a7b62;
  letter-spacing: 1px;
}
.deck-hint b { color: #6fd8f0; }
/* 拖拽中的卡半透明 */
.dragging { opacity: 0.45; }

/* === 卡组统计 === */
.stats {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 18px;
  margin-top: 10px;
  padding: 10px 14px;
  background: rgba(0,0,0,0.3);
  border: 1px solid rgba(120,80,30,0.35);
  border-radius: 8px;
}
.st-title { font-size: 12px; color: #f0cc6d; letter-spacing: 2px; display: block; margin-bottom: 6px; }
.curve { display: flex; align-items: flex-end; gap: 6px; }
.cv-col { display: flex; flex-direction: column; align-items: center; gap: 2px; width: 22px; }
.cv-num { font-size: 10px; color: #7ed957; height: 12px; }
.cv-bar {
  width: 16px; min-height: 2px;
  background: linear-gradient(180deg, #6fd8f0, #2a6fa8);
  border-radius: 2px 2px 0 0;
}
.cv-x { font-size: 10px; color: #8a7b62; }
.st-mid { display: flex; gap: 16px; }
.kv { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.kv span { font-size: 11px; color: #8a7b62; }
.kv b { font-size: 15px; color: #f0cc6d; }
.st-prof { flex: 1 1 220px; min-width: 200px; }
.pf-row { display: flex; align-items: center; gap: 8px; margin-bottom: 3px; }
.pf-name { width: 34px; font-size: 11px; color: #d8c9a8; }
.pf-bar { flex: 1; height: 8px; background: rgba(255,255,255,0.08); border-radius: 4px; overflow: hidden; }
.pf-bar i { display: block; height: 100%; background: linear-gradient(90deg, #e6b94f, #a5842f); }
.pf-n { width: 20px; text-align: right; font-size: 11px; color: #b8a884; }
.pf-empty { font-size: 11px; color: #6b5d4b; }

/* === 卡牌库 === */
.library { margin-top: 14px; }
.lib-head {
  display: flex; align-items: center; justify-content: space-between;
  gap: 14px; flex-wrap: wrap; margin-bottom: 12px;
}
.section-title {
  font-size: 18px; font-weight: 600;
  color: #f0cc6d;
  letter-spacing: 3px;
  margin: 0;
  text-shadow: 0 0 6px rgba(240,204,109,0.35);
}
.filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.f-search, .f-sel {
  padding: 5px 10px;
  background: rgba(20,16,12,0.85);
  border: 1px solid var(--leather-3);
  border-radius: 4px;
  color: #e8dcc4; font-size: 12px; outline: none;
}
.f-search { width: 150px; }
.f-search:focus, .f-sel:focus { border-color: var(--gold); }
.f-chk { font-size: 12px; color: #b8a884; display: flex; align-items: center; gap: 4px; cursor: pointer; }
.f-count { font-size: 11px; color: #8a7b62; }

.cards {
  display: flex; flex-wrap: wrap; gap: 14px;
  max-height: 46vh;
  overflow-y: auto;
  padding: 4px 6px 10px;
  align-content: flex-start;
}
.cards::-webkit-scrollbar { width: 8px; }
.cards::-webkit-scrollbar-thumb { background: rgba(212,168,71,0.35); border-radius: 4px; }
.card-item { position: relative; cursor: pointer; }
.lib-empty { width: 100%; text-align: center; padding: 20px; color: #6b5d4b; font-size: 13px; }

/* 右下角加卡按钮: 单击卡面=看档案, 点这个才是加入卡组 */
.add-ico {
  position: absolute; bottom: 4px; right: 4px;
  z-index: 4;
  width: 22px; height: 22px; border-radius: 50%;
  background: linear-gradient(180deg, #e6b94f, #a5842f);
  color: #1a1005; font-size: 15px; font-weight: 800; line-height: 20px;
  text-align: center;
  border: 1px solid #7a5f1e;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.6);
  cursor: pointer;
  opacity: 0.85;
  transition: opacity 0.15s, transform 0.15s;
}
.add-ico:hover { opacity: 1; transform: scale(1.18); }

.corner-tag {
  text-align: right;
  margin-top: 10px;
  font-size: 11px; color: #5a4128; letter-spacing: 2px;
}

/* === 右击菜单 === */
.ctx-menu {
  position: fixed;
  z-index: 200;
  width: 168px;
  padding: 4px;
  background: rgba(20,16,12,0.97);
  border: 1px solid rgba(212,168,71,0.5);
  border-radius: 6px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.7);
}
.ctx-title {
  padding: 6px 8px; font-size: 12px; color: #f0cc6d;
  border-bottom: 1px solid rgba(212,168,71,0.25); margin-bottom: 4px;
  display: flex; justify-content: space-between; align-items: center;
}
.ctx-sub { font-size: 11px; color: #8a7b62; }
.ctx-item {
  padding: 7px 10px; font-size: 12px; color: #e8dcc4;
  border-radius: 4px; cursor: pointer;
}
.ctx-item:hover { background: rgba(212,168,71,0.18); color: #f0cc6d; }
.ctx-item.off { opacity: 0.4; cursor: not-allowed; }
.ctx-item.off:hover { background: transparent; color: #e8dcc4; }

/* === 轻提示 === */
.toast {
  position: fixed;
  left: 50%; bottom: 46px; transform: translateX(-50%);
  z-index: 300;
  padding: 8px 18px;
  background: rgba(20,16,12,0.95);
  border: 1px solid rgba(212,168,71,0.55);
  border-radius: 18px;
  color: #f0cc6d; font-size: 13px;
  box-shadow: 0 6px 20px rgba(0,0,0,0.6);
}
.fade-enter-active, .fade-leave-active { transition: opacity 0.18s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* ===== 平板竖屏 ===== */
@media (max-width: 980px) {
  .cface.sm { width: 80px; height: 108px; }
  .cards { gap: 10px; }
}

/* ===== 手机竖屏 ===== */
@media (max-width: 680px) {
  .deck { padding: 8px 8px 20px; }
  .toolbar { padding: 10px 10px; gap: 8px; }
  .tl-center { flex: 1 1 100%; justify-content: space-between; gap: 8px; }
  .tl-right { flex: 1 1 100%; justify-content: center; gap: 6px; }
  .btn-act, .btn-save-as, .btn-save, .btn-tb { padding: 7px 9px; font-size: 11px; }
  .deck-name { max-width: 46%; font-size: 13px; }

  /* 卡组槽位区: 手机上不吸顶, 否则 30 个槽位会吃掉整屏 */
  .deck-panel { position: static; padding: 8px 0 10px; }
  .cface.xs { width: 40px; height: 56px; }
  .slot-wrap, .slot { margin: 0; }
  .cface.sm { width: 72px; height: 98px; }
  .cface.sm .cf-mana { min-width: 19px; height: 19px; font-size: 10px; }
  .cface.sm .cf-atk, .cface.sm .cf-hp { min-width: 21px; height: 21px; font-size: 11px; }
  .cards { gap: 8px; max-height: 38vh; padding: 4px 2px 10px; }

  .stats { gap: 10px; padding: 8px 10px; }
  .st-block { min-width: 100%; }
  .filters { gap: 6px; }
  .f-search, .f-sel { padding: 5px 6px; font-size: 11px; }
  .corner-tag { font-size: 10px; letter-spacing: 0; text-align: center; line-height: 1.6; }
  .toast { bottom: 24px; font-size: 12px; padding: 7px 14px; }
}
</style>
