<template>
  <div class="shop">
    <!-- 顶部 -->
    <div class="toolbar">
      <div class="tl-left">
        <button class="btn-tb" @click="$router.push('/lobby')">← 返回大厅</button>
      </div>
      <div class="tl-center">
        <span class="page-title">商 店</span>
        <span class="balance">🪙 {{ balance }}</span>
        <span class="balance crystal">💎 {{ crystal }}</span>
      </div>
      <div class="tl-right">
        <button class="btn-tb" @click="exOpen = true">💎 兑 换</button>
        <button class="btn-tb" @click="rechargeOpen = true">充 值</button>
      </div>
    </div>

    <el-tabs v-model="tab" class="shop-tabs">
      <!-- === 抽卡 === -->
      <el-tab-pane label="卡牌抽取" name="gacha">
        <div class="collect-bar">
          <span class="collect-text">收集进度</span>
          <div class="progress"><div class="progress-in" :style="{ width: collectPct + '%' }"></div></div>
          <span class="collect-num">{{ pool.ownedCount }} / {{ pool.totalCount }}</span>
        </div>

        <!-- 货币选择: 金币充值获得 / 水晶签到任务获得 -->
        <div class="currency-row">
          <span class="cur-label">支付货币</span>
          <button class="cur-opt" :class="{ on: currency === 'gold' }" @click="currency = 'gold'">
            🪙 金币 <span class="cur-tip">充值获得</span>
          </button>
          <button class="cur-opt" :class="{ on: currency === 'crystal' }" @click="currency = 'crystal'">
            💎 水晶 <span class="cur-tip">签到/任务获得 · 每抽贵 100</span>
          </button>
        </div>

        <!-- 保底进度: 10 抽内必出「稀有」及以上, 40 抽内必出「传说」 -->
        <div class="pity-row">
          <div class="pity-item">
            <span class="pi-label">稀有保底</span>
            <div class="pi-bar">
              <div class="pi-bar-in rare" :style="{ width: pityPct('rare') + '%' }"></div>
            </div>
            <span class="pi-num" :class="{ near: (pool.pityRare ?? 10) <= 3 }">
              {{ (pool.pityRare ?? 10) <= 1 ? '下抽必出' : '再 ' + (pool.pityRare ?? 10) + ' 抽' }}
            </span>
          </div>
          <div class="pity-item">
            <span class="pi-label">传说保底</span>
            <div class="pi-bar">
              <div class="pi-bar-in legend" :style="{ width: pityPct('legend') + '%' }"></div>
            </div>
            <span class="pi-num" :class="{ near: (pool.pityLegend ?? 40) <= 5 }">
              {{ (pool.pityLegend ?? 40) <= 1 ? '下抽必出' : '再 ' + (pool.pityLegend ?? 40) + ' 抽' }}
            </span>
          </div>
        </div>

        <div class="pull-row">
          <button class="pull-btn one" :disabled="pulling" @click="doPull(1)">
            <span class="pb-main">单 抽</span>
            <span class="pb-cost">{{ currency === 'gold' ? `🪙 ${pool.costOne || 100}` : `💎 ${pool.crystalOne || 200}` }}</span>
          </button>
          <button class="pull-btn ten" :disabled="pulling" @click="doPull(10)">
            <span class="pb-main">十 连 抽</span>
            <span class="pb-cost">{{ currency === 'gold' ? `🪙 ${pool.costTen || 900} · 九折` : `💎 ${pool.crystalTen || 1900} · 九折` }}</span>
          </button>
        </div>
        <div class="pull-tip">
          重复卡牌自动回收为 {{ DUP_REFUND }} 金币 ·
          <b>10 抽内必出「稀有」及以上</b>，<b>{{ pool.pityLegendMax || 40 }} 抽内必出「传说」</b>（单抽十连共用进度）
        </div>

        <!-- 卡池一览 -->
        <h3 class="section-title">卡 池</h3>
        <div class="pool-grid">
          <CardFace v-for="c in pool.cards" :key="c.id"
               :card="lockedView(c)" size="sm"
               class="mini-card"
               :class="{ locked: !c.owned }"
               title="点击查看干员档案与时装"
               @click="openDetail(c)" />
        </div>
      </el-tab-pane>

      <!-- === 我的卡包: 只显示已拥有的卡, 点击卡面看档案 === -->
      <el-tab-pane label="我 的 卡 包" name="pack">
        <div class="collect-bar">
          <span class="collect-text">已拥有</span>
          <div class="progress"><div class="progress-in" :style="{ width: collectPct + '%' }"></div></div>
          <span class="collect-num">{{ pool.ownedCount }} / {{ pool.totalCount }}</span>
        </div>
        <div class="pool-grid pack">
          <CardFace v-for="c in ownedCards" :key="c.id" :card="c" size="sm"
               class="mini-card"
               title="点击卡面查看干员档案与时装"
               @click="openDetail(c)">
            <span class="mini-dress" v-if="cardSkin(c.name) !== 'skin-default'">👗</span>
          </CardFace>
        </div>
        <div v-if="ownedCards.length === 0" class="t-empty">还没有卡牌，去「卡牌抽取」抽几张吧</div>
        <div class="skin-tip">
          点击卡面进入<b>干员档案</b>：查看属性 / 战吼 / 亡语，并给该干员单独装配时装。
          装配后卡面、卡组、对战中都会直接显示这套时装立绘（每个干员各穿各的，不共享）。
        </div>
      </el-tab-pane>

      <!-- === 皮肤 === -->
      <el-tab-pane label="卡面皮肤" name="skin">
        <div class="skin-grid">
          <div v-for="s in skins" :key="s.id" class="skin-item"
               :class="{ equipped: skinWornBy(s.id).length > 0 }">
            <div class="skin-preview">
              <div class="pv-card card" :class="[s.frameClass, 'r3']"
                   :style="{ backgroundImage: `url(${skinImg(s.frameClass)})` }">
                <span class="pv-cost">3</span>
                <span class="pv-name">{{ skinOper(s.frameClass) }}</span>
              </div>
              <span class="pv-tag">{{ skinTag(s.frameClass) }}</span>
            </div>
            <div class="skin-info">
              <span class="skin-name">
                {{ s.name }}
                <b class="sk-ex" v-if="isExclusive(s)">专属·{{ s.operator }}</b>
                <b class="sk-ex gen" v-else>通用</b>
              </span>
              <span class="skin-desc">{{ s.description }}</span>
              <span class="skin-wear" v-if="skinWornBy(s.id).length > 0">
                已装配：{{ skinWornBy(s.id).join('、') }}
              </span>
              <span class="skin-price" v-if="s.price > 0">🪙 {{ s.price }}</span>
              <span class="skin-price free" v-else>免费</span>
            </div>
            <button v-if="s.owned" class="skin-btn on" disabled>已 拥 有</button>
            <button v-else class="skin-btn buy" @click="buy(s)">🪙 {{ s.price }} 购 买</button>
          </div>
        </div>
        <div class="skin-tip">
          皮肤购买后到<b>干员档案</b>里装配：卡组编辑/卡池里点卡片上的 ⓘ（或点卡面）→ 底部时装列表点「装配」。
          每个干员独立穿着，专属时装会直接替换卡面立绘
        </div>
      </el-tab-pane>

      <!-- === 充值流水 === -->
      <el-tab-pane label="充值流水" name="logs">
        <div class="log-head">
          <span class="lh-total">共 {{ logTotal }} 笔 · 累计 🪙 {{ logSum }}</span>
          <button class="btn-tb" @click="loadLogs">⟳ 刷新</button>
        </div>
        <table class="log-table">
          <thead>
            <tr>
              <th>时 间</th>
              <th>充值金额</th>
              <th>充值后余额</th>
              <th>渠道备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="l in logs" :key="l.id">
              <td class="t-time">{{ fmt(l.createTime) }}</td>
              <td class="t-amt">+🪙 {{ l.amount }}</td>
              <td class="t-bal">🪙 {{ l.balanceAfter }}</td>
              <td class="t-remark">{{ l.remark || '-' }}</td>
            </tr>
            <tr v-if="logs.length === 0"><td colspan="4" class="t-empty">暂无充值记录，点右上角「充值」试试</td></tr>
          </tbody>
        </table>
        <div class="pager" v-if="logTotal > logSize">
          <button class="pg-btn" :disabled="logPage <= 1" @click="logPage--; loadLogs()">‹ 上一页</button>
          <span class="pg-info">{{ logPage }} / {{ logMaxPage }}</span>
          <button class="pg-btn" :disabled="logPage >= logMaxPage" @click="logPage++; loadLogs()">下一页 ›</button>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 抽卡结果 -->
    <el-dialog v-model="resultOpen" :title="pullTitle" width="620px" center>
      <div class="result-grid">
        <div v-for="(r, i) in pullResults" :key="i"
             class="result-card"
             :class="[cardSkin(r.name), 'r' + (r.rarity || 1)]"
             :style="bg(r)">
          <span class="rc-new" v-if="r.isNew">NEW</span>
          <span class="rc-dup" v-else>重复 +{{ DUP_REFUND }}</span>
          <span v-if="r.pity" class="rc-pity" :title="'由保底产出（' + (r.rarity >= 4 ? '传说保底' : '稀有保底') + '）'">保底</span>
          <span class="rc-name">{{ r.name }}</span>
          <span class="rc-rarity">{{ rarityName(r.rarity) }}</span>
        </div>
      </div>
      <div class="result-foot">
        <span v-if="pullRefund > 0">重复回收 +{{ pullRefund }} 金币</span>
        <span v-if="pullNew > 0">　新解锁 {{ pullNew }} 张</span>
      </div>
      <template #footer>
        <button class="btn-main" @click="resultOpen = false">确 定</button>
      </template>
    </el-dialog>

    <!-- 干员档案: 属性/战吼亡语/时装装配 -->
    <CardDetail v-model="detailOpen" :card="detailCard" @closed="loadSkins()" />

    <!-- 金币换水晶 -->
    <el-dialog v-model="exOpen" title="💎 金币兑换水晶（2 : 1）" width="380px" center>
      <div class="recharge-tip">水晶价值约为金币 2 倍（水晶抽卡每抽贵 100）· 100 金币 = 50 水晶</div>
      <div class="recharge-grid">
        <button v-for="t in exTiers" :key="t.gold"
                class="recharge-opt" :class="{ on: exGold === t.gold }"
                @click="exGold = t.gold">
          <span class="amt">🪙 {{ t.gold }} → 💎 {{ t.gold / 2 }}</span>
          <span class="bonus">{{ t.label }}</span>
        </button>
      </div>
      <template #footer>
        <button class="btn-main" :disabled="exing" @click="doExchange">
          {{ exing ? '兑换中…' : '确认兑换' }}
        </button>
      </template>
    </el-dialog>

    <!-- 充值弹窗 -->
    <el-dialog v-model="rechargeOpen" title="💰 金币充值（模拟支付）" width="360px" center>
      <div class="recharge-body">
        <div class="recharge-tip">演示环境不产生真实扣费</div>
        <div class="recharge-grid">
          <button v-for="t in tiers" :key="t.coins"
                  class="recharge-opt" :class="{ on: rechargeCoins === t.coins }"
                  @click="rechargeCoins = t.coins">
            <span class="amt">🪙 {{ t.coins }}</span>
            <span class="bonus">{{ t.label }}</span>
          </button>
        </div>
      </div>
      <template #footer>
        <button class="btn-main" :disabled="recharging" @click="doRecharge">
          {{ recharging ? '支付中…' : '确认充值' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http, { idemKey } from '../api/request'
import { cardSkin, cardArt, loadSkinMap } from '../api/skin'
import CardDetail from '../components/CardDetail.vue'
import CardFace from '../components/CardFace.vue'
import sfx from '../utils/sound'

const DUP_REFUND = 20
const tab = ref('gacha')
const balance = ref(0)
const crystal = ref(0)
const currency = ref('gold')   // 'gold' | 'crystal'
const pool = ref({
  cards: [], ownedCount: 0, totalCount: 0, costOne: 100, costTen: 900,
  crystalOne: 200, crystalTen: 1900,
  pityRare: 10, pityLegend: 40, pityRareMax: 10, pityLegendMax: 40
})
/** 保底进度条: 已抽的越多条越满(剩余 1 抽时几乎满格) */
function pityPct(kind) {
  const max = kind === 'rare' ? (pool.value.pityRareMax || 10) : (pool.value.pityLegendMax || 40)
  const left = kind === 'rare' ? (pool.value.pityRare ?? max) : (pool.value.pityLegend ?? max)
  return Math.round((max - left) / max * 100)
}
const skins = ref([])
const pulling = ref(false)
const resultOpen = ref(false)
const pullResults = ref([])
const pullRefund = ref(0)
const pullNew = ref(0)
const pullTitle = ref('')

const rechargeOpen = ref(false)
const rechargeCoins = ref(300)
const recharging = ref(false)
const logs = ref([])
const logTotal = ref(0)
const logPage = ref(1)
const logSize = 10
const logMaxPage = computed(() => Math.max(1, Math.ceil(logTotal.value / logSize)))
const detailOpen = ref(false)
const detailCard = ref({})
function openDetail(c) { detailCard.value = c; detailOpen.value = true }

// 金币换水晶(2:1)
const exOpen = ref(false)
const exGold = ref(500)
const exing = ref(false)
const exTiers = [
  { gold: 100, label: '小额兑换' },
  { gold: 500, label: '推荐' },
  { gold: 1000, label: '八八折礼包' },
  { gold: 2000, label: '大额兑换' }
]

async function doExchange() {
  exing.value = true
  try {
    const r = await http.post('/wallet/exchange', { gold: exGold.value })
    balance.value = r.balance
    crystal.value = r.crystal
    const u = JSON.parse(localStorage.getItem('user') || '{}')
    u.balance = r.balance
    u.crystal = r.crystal
    localStorage.setItem('user', JSON.stringify(u))
    ElMessage.success(`兑换成功: +${r.crystalGot} 水晶`)
    exOpen.value = false
    await loadLogs()
  } finally {
    exing.value = false
  }
}
const logSum = computed(() => logs.value.reduce((s, l) => s + (l.amount || 0), 0))
// 我的卡包: 只列已拥有的卡
const ownedCards = computed(() => (pool.value.cards || []).filter(c => c.owned))
const tiers = [
  { coins: 60, label: '¥6 · 金币' },
  { coins: 300, label: '¥30 · 金币' },
  { coins: 980, label: '¥98 · 超值礼包' },
  { coins: 3280, label: '¥328 · 豪华礼包' }
]

const collectPct = computed(() => {
  const t = pool.value.totalCount || 1
  return Math.round(((pool.value.ownedCount || 0) / t) * 100)
})

const bg = (c) => {
  const art = cardArt(c.name) || c.img
  return art ? { backgroundImage: `url(${art})` } : {}
}
const rarityName = (r) => ({ 1: '普通', 2: '稀有', 3: '史诗', 4: '传说' }[r] || '普通')
/** 未拥有的卡在卡池里显示为 "???"（不剧透名字） */
const lockedView = (c) => (c.owned ? c : { ...c, name: '???', effect: '' })

// 皮肤预览: 每款皮肤配一张官方立绘/时装(素材来自 yuanyan3060/ArknightsGameResource)
const SKIN_IMG = {
  'skin-default': '/cards/1.jpg',      // 玫兰莎
  'skin-ice': '/cards/16.jpg',         // 赫默(莱茵生命)
  'skin-flame': '/cards/39.jpg',       // 德克萨斯(企鹅物流)
  'skin-forest': '/cards/32.jpg',      // 陨星(萨卡兹)
  'skin-holo': '/cards/10.jpg',        // 斯卡蒂(深海猎人)
  'skin-gold': '/cards/57.jpg',        // 阿米娅(巴别塔)
  'skin-amiya': '/skins/skin-amiya.jpg',
  'skin-kalts': '/skins/skin-kalts.jpg',
  'skin-w': '/skins/skin-w.jpg',
  'skin-silverash': '/skins/skin-silverash.jpg',
  'skin-surtr': '/skins/skin-surtr.jpg',
  'skin-lappland': '/skins/skin-lappland.jpg'
}
const SKIN_OPER = {
  'skin-default': '玫兰莎', 'skin-ice': '赫默', 'skin-flame': '德克萨斯',
  'skin-forest': '陨星', 'skin-holo': '斯卡蒂', 'skin-gold': '阿米娅',
  'skin-amiya': '阿米娅', 'skin-kalts': '凯尔希', 'skin-w': 'W',
  'skin-silverash': '银灰', 'skin-surtr': '史尔特尔', 'skin-lappland': '拉普兰德'
}
const skinImg = (cls) => SKIN_IMG[cls] || '/cards/1.jpg'
const skinOper = (cls) => SKIN_OPER[cls] || ''
const skinTag = (cls) => (SKIN_IMG[cls] || '').startsWith('/skins/') ? '官方时装' : '阵营涂装'
// 明日方舟模式: 皮肤按干员独立装配, 这里列出"当前有哪些干员穿着这款皮肤"
const isExclusive = (s) => !!s.operator
const skinWornBy = (id) => (skins.value.find(x => x.id === id) || {}).wornBy || []

async function loadPool() {
  pool.value = await http.get('/gacha/pool')
  balance.value = pool.value.balance
  crystal.value = pool.value.crystal
}
async function loadSkins() { const r = await http.get('/skins'); skins.value = r.skins; balance.value = r.balance; crystal.value = r.crystal }
async function loadWallet() {
  const w = await http.get('/wallet/me')
  balance.value = w.balance
  crystal.value = w.crystal
}

async function doPull(times) {
  pulling.value = true
  try {
    const r = await http.post('/gacha/pull', { times, currency: currency.value, idemKey: idemKey() })
    pullResults.value = r.results
    pullRefund.value = r.refund
    pullNew.value = r.newlyOwned
    balance.value = r.balance
    crystal.value = r.crystal
    pullTitle.value = times === 1 ? '✨ 单抽结果' : '✨ 十连结果'
    resultOpen.value = true
    // 保底进度直接用本次结果刷新(服务端已更新, 不必再等一次请求)
    if (r.pityRare != null) pool.value.pityRare = r.pityRare
    if (r.pityLegend != null) pool.value.pityLegend = r.pityLegend
    // 音效: 抽卡上行三音; 出了稀有/传说再叠一层高稀有音效
    sfx.play('pull')
    if ((r.results || []).some(x => (x.rarity || 1) >= 3)) setTimeout(() => sfx.play('rare'), 260)
    await loadPool()
    const u = JSON.parse(localStorage.getItem('user') || '{}')
    u.balance = r.balance
    u.crystal = r.crystal
    localStorage.setItem('user', JSON.stringify(u))
  } finally {
    pulling.value = false
  }
}

async function buy(s) {
  if (!confirm(`确定花费 ${s.price} 金币购买「${s.name}」？`)) return
  balance.value = await http.post('/skins/buy', { skinId: s.id })
  await loadSkins()
}

async function doRecharge() {
  recharging.value = true
  try {
    const rc = await http.post('/wallet/recharge', { amount: rechargeCoins.value, idemKey: idemKey() })
    balance.value = rc.balance
    const u = JSON.parse(localStorage.getItem('user') || '{}')
    u.balance = balance.value
    localStorage.setItem('user', JSON.stringify(u))
    rechargeOpen.value = false
    await loadSkins()
    await loadLogs()
  } finally {
    recharging.value = false
  }
}

async function loadLogs() {
  const r = await http.get('/wallet/logs', { params: { page: logPage.value, size: logSize } })
  logs.value = r.list
  logTotal.value = r.total
}
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '' }

onMounted(async () => { await loadPool(); await loadSkinMap(); await loadSkins(); await loadWallet(); await loadLogs() })
</script>

<style scoped>
.shop { max-width: 1100px; margin: 0 auto; padding: 20px; }

.toolbar {
  display: grid;
  grid-template-columns: 140px 1fr 140px;
  align-items: center;
  padding: 14px 22px;
  background: linear-gradient(90deg, rgba(43, 32, 23, 0.9), rgba(20, 16, 12, 0.8));
  border: 1px solid rgba(212, 168, 71, 0.35);
  border-radius: 10px;
  margin-bottom: 16px;
}
.tl-left { text-align: left; }
.tl-right { text-align: right; }
.tl-center { display: flex; align-items: center; justify-content: center; gap: 20px; }
.btn-tb {
  padding: 6px 14px;
  background: transparent;
  border: 1px solid var(--gold-dark);
  border-radius: 4px;
  color: #d8c9a8;
  font-size: 13px; cursor: pointer;
  transition: all 0.15s;
}
.btn-tb:hover { background: rgba(212, 168, 71, 0.15); color: #f0cc6d; }
.page-title {
  font-family: "STKaiti", "KaiTi", serif;
  font-size: 22px; font-weight: 700;
  color: #f0cc6d; letter-spacing: 8px;
  text-shadow: 0 0 10px rgba(240, 204, 109, 0.4);
}
.balance { font-size: 15px; font-weight: 700; color: #e8c46a; }
.balance.crystal { color: #8fd7ff; }

/* 货币选择 */
.currency-row { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.cur-label { font-size: 13px; color: #b8a884; letter-spacing: 2px; }
.cur-opt {
  padding: 8px 18px;
  background: rgba(20, 16, 12, 0.8);
  border: 1px solid rgba(120, 90, 50, 0.5);
  border-radius: 18px;
  color: #d8c9a8;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.cur-opt:hover { border-color: var(--gold); color: #f0cc6d; }
.cur-opt.on { border-color: #e6b94f; color: #f0cc6d; box-shadow: 0 0 10px rgba(230, 185, 79, 0.3); }
.cur-tip { font-size: 10px; color: #8a7b62; margin-left: 4px; }
.cur-opt.on .cur-tip { color: #c9a95e; }

/* 收集进度 */
.collect-bar { display: flex; align-items: center; gap: 14px; margin: 6px 0 20px; }
.collect-text { font-size: 13px; color: #b8a884; letter-spacing: 2px; }
.progress {
  flex: 1; height: 10px;
  background: rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(212, 168, 71, 0.3);
  border-radius: 5px;
  overflow: hidden;
}
.progress-in {
  height: 100%;
  background: linear-gradient(90deg, #a5842f, #f0cc6d);
  box-shadow: 0 0 10px rgba(240, 204, 109, 0.6);
  transition: width 0.4s;
}
.collect-num { font-size: 13px; color: #f0cc6d; font-weight: 600; }

/* 抽卡按钮 */
/* === 保底进度 === */
.pity-row {
  display: flex; gap: 22px; margin-bottom: 14px;
  padding: 10px 16px;
  background: rgba(20, 16, 12, 0.6);
  border: 1px solid rgba(212, 168, 71, 0.22);
  border-radius: 8px;
}
.pity-item { flex: 1; display: flex; align-items: center; gap: 10px; }
.pi-label { font-size: 12px; color: #b8a884; letter-spacing: 1px; white-space: nowrap; }
.pi-bar {
  flex: 1; height: 8px;
  background: rgba(0, 0, 0, 0.55);
  border-radius: 4px; overflow: hidden;
}
.pi-bar-in { height: 100%; transition: width 0.4s; border-radius: 4px; }
.pi-bar-in.rare { background: linear-gradient(90deg, #6b3fa0, #b04ad9); box-shadow: 0 0 8px rgba(176, 74, 217, 0.6); }
.pi-bar-in.legend { background: linear-gradient(90deg, #b8781a, #ffc45c); box-shadow: 0 0 8px rgba(240, 165, 42, 0.6); }
.pi-num { font-size: 11px; color: #8a7b62; white-space: nowrap; min-width: 62px; text-align: right; }
.pi-num.near { color: #ffc45c; font-weight: 700; text-shadow: 0 0 6px rgba(255, 196, 92, 0.5); }

.pull-row { display: flex; gap: 18px; margin-bottom: 10px; }
.pull-btn {
  flex: 1;
  padding: 18px 0;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex; flex-direction: column; gap: 6px; align-items: center;
}
.pull-btn.one {
  background: linear-gradient(160deg, rgba(74, 144, 217, 0.25), rgba(20, 16, 12, 0.9));
  border: 1px solid rgba(74, 144, 217, 0.55);
  color: #b7dbff;
}
.pull-btn.ten {
  background: linear-gradient(160deg, rgba(230, 185, 79, 0.28), rgba(20, 16, 12, 0.9));
  border: 1px solid rgba(230, 185, 79, 0.6);
  color: #f0cc6d;
}
.pull-btn:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 8px 22px rgba(0, 0, 0, 0.5); }
.pull-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.pb-main { font-size: 20px; font-weight: 700; letter-spacing: 4px; }
.pb-cost { font-size: 12px; opacity: 0.85; }
.pull-tip { font-size: 11px; color: #8a7b62; text-align: center; margin-bottom: 26px; }

.section-title {
  font-size: 16px; font-weight: 600;
  color: #f0cc6d; letter-spacing: 3px;
  margin: 0 0 12px;
}

/* 卡池小卡 */
.pool-grid { display: flex; flex-wrap: wrap; gap: 10px; }
.mini-card {
  position: relative;
  width: 92px; height: 124px;
  border-radius: 6px;
  background-color: #22334d;
  background-size: cover; background-position: center;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.6);
}
.mini-card::after {
  content: '';
  position: absolute; inset: 0;
  background: linear-gradient(160deg, rgba(15, 25, 45, 0.1), rgba(15, 25, 45, 0.85));
}
.mini-card.locked { filter: grayscale(0.9) brightness(0.45); }
.pool-grid.pack .mini-card { width: 92px; height: 130px; }
.mini-dress {
  position: absolute; top: 3px; right: 4px; font-size: 13px;
  text-shadow: 0 0 6px #000, 0 1px 2px #000;
}
.mini-cost {
  position: absolute; top: 4px; left: 4px; z-index: 2;
  width: 20px; height: 20px;
  background: radial-gradient(circle at 35% 30%, #b4dcff, #4a90d9 50%, #1e4682);
  border-radius: 50%;
  font-size: 11px; font-weight: 800; color: #fff;
  display: flex; align-items: center; justify-content: center;
}
.mini-name {
  position: absolute; bottom: 16px; left: 0; right: 0; z-index: 2;
  text-align: center; font-size: 11px; color: #e8dcc4;
  text-shadow: 0 1px 3px #000;
}
.mini-rarity {
  position: absolute; bottom: 3px; left: 0; right: 0; z-index: 2;
  text-align: center; font-size: 9px; color: #8a7b62;
}

/* 皮肤 */
.skin-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 16px; }
.skin-item {
  padding: 16px;
  background: rgba(20, 16, 12, 0.7);
  border: 1px solid rgba(120, 90, 50, 0.45);
  border-radius: 10px;
  display: flex; flex-direction: column; gap: 10px;
}
.skin-item.equipped { border-color: #f0cc6d; box-shadow: 0 0 14px rgba(240, 204, 109, 0.35); }
.skin-preview {
  height: 140px;
  border-radius: 8px;
  background: linear-gradient(160deg, #22334d, #0f192d);
  display: flex; align-items: center; justify-content: center;
  position: relative;
  overflow: hidden;
}
/* 迷你卡牌: 带 .card 类, 让 skins.css 的皮肤边框光效真实生效 */
.pv-card {
  position: relative;
  width: 86px; height: 118px;
  border-radius: 7px;
  background-color: #22334d;
  background-size: cover;
  background-position: center top;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.6);
}
.pv-card::before {
  content: '';
  position: absolute; inset: 0;
  background: linear-gradient(160deg, rgba(15,25,45,0.05), rgba(15,25,45,0.55));
  border-radius: 7px;
  pointer-events: none;
}
.pv-cost {
  position: absolute; top: 5px; left: 5px; z-index: 2;
  width: 20px; height: 20px; border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #b4dcff, #4a90d9 50%, #1e4682);
  font-size: 11px; font-weight: 800; color: #fff;
  display: flex; align-items: center; justify-content: center;
}
.pv-name {
  position: absolute; bottom: 6px; left: 0; right: 0; z-index: 2;
  text-align: center; font-size: 11px; color: #fff;
  text-shadow: 0 1px 3px #000;
  padding: 2px 0;
  background: rgba(0, 0, 0, 0.45);
}
.pv-tag {
  position: absolute; top: 8px; right: 10px;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 10px;
  background: rgba(240, 204, 109, 0.18);
  border: 1px solid rgba(240, 204, 109, 0.45);
  color: #f0cc6d;
  letter-spacing: 1px;
}
.skin-info { display: flex; flex-direction: column; gap: 3px; }
.skin-name { font-size: 15px; font-weight: 600; color: #f0cc6d; }
.skin-desc { font-size: 11px; color: #8a7b62; }
.skin-price { font-size: 13px; color: #e8c46a; font-weight: 600; }
.skin-price.free { color: #7ed957; }
.sk-ex { font-size: 10px; color: #7fd8ff; border: 1px rgba(127, 216, 255, 0.4) solid; border-radius: 8px; padding: 0 5px; margin-left: 4px; font-weight: 500; }
.sk-ex.gen { color: #9a8f7d; border-color: rgba(154, 143, 125, 0.35); }
.skin-wear { font-size: 11px; color: #7ed957; }
.skin-btn {
  padding: 7px 0;
  border-radius: 4px;
  background: transparent;
  border: 1px solid var(--gold-dark);
  color: #d8c9a8;
  font-size: 12px; letter-spacing: 2px; cursor: pointer;
}
.skin-btn:hover:not(:disabled) { background: rgba(212, 168, 71, 0.15); color: #f0cc6d; }
.skin-btn.buy { background: linear-gradient(180deg, #e6b94f, #a5842f); color: #1a1005; font-weight: 700; border-color: #7a5f1e; }
.skin-btn.on { border-color: #f0cc6d; color: #f0cc6d; }
.skin-btn:disabled { opacity: 0.65; cursor: default; }

/* 抽卡结果 */
.result-grid { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }
.result-card {
  position: relative;
  width: 100px; height: 136px;
  border-radius: 8px;
  background-color: #22334d;
  background-size: cover; background-position: center;
  overflow: hidden;
  animation: popIn 0.35s ease-out both;
}
.result-card::after {
  content: '';
  position: absolute; inset: 0;
  background: linear-gradient(160deg, rgba(15, 25, 45, 0.1), rgba(15, 25, 45, 0.82));
}
.rc-new, .rc-dup {
  position: absolute; top: 4px; left: 4px; z-index: 2;
  padding: 1px 6px; border-radius: 9px;
  font-size: 10px; font-weight: 700;
}
.rc-new { background: #7ed957; color: #053a0e; }
.rc-dup { background: rgba(0, 0, 0, 0.7); color: #b8a884; }
/* 保底产出标记: 右下角紫/金色小标签 */
.rc-pity {
  position: absolute; bottom: 26px; right: 4px; z-index: 2;
  padding: 1px 6px; border-radius: 8px;
  font-size: 9px; font-weight: 700;
  background: linear-gradient(135deg, #b04ad9, #6b3fa0);
  color: #fff;
  box-shadow: 0 0 8px rgba(176, 74, 217, 0.7);
}
.rc-name {
  position: absolute; bottom: 16px; left: 0; right: 0; z-index: 2;
  text-align: center; font-size: 12px; color: #fff;
  text-shadow: 0 1px 3px #000;
}
.rc-rarity {
  position: absolute; bottom: 3px; left: 0; right: 0; z-index: 2;
  text-align: center; font-size: 9px; color: #9a8b72;
}
@keyframes popIn {
  from { opacity: 0; transform: scale(0.7); }
  to { opacity: 1; transform: scale(1); }
}
.result-foot { text-align: center; margin-top: 14px; font-size: 12px; color: #8a7b62; }

/* 稀有度边框 */
.r1 { box-shadow: 0 2px 8px rgba(0, 0, 0, 0.6), 0 0 0 2px #7a7a7a; }
.r2 { box-shadow: 0 2px 8px rgba(0, 0, 0, 0.6), 0 0 0 2px #4a90d9, 0 0 12px rgba(74, 144, 217, 0.4); }
.r3 { box-shadow: 0 2px 8px rgba(0, 0, 0, 0.6), 0 0 0 2px #b04ad9, 0 0 14px rgba(176, 74, 217, 0.5); }
.r4 { box-shadow: 0 2px 8px rgba(0, 0, 0, 0.6), 0 0 0 2px #e6a23c, 0 0 18px rgba(230, 162, 60, 0.6); }

/* 充值流水 */
.log-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.lh-total { font-size: 13px; color: #b8a884; }
.log-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.log-table thead tr { background: rgba(0, 0, 0, 0.45); }
.log-table th {
  padding: 11px 14px; text-align: left;
  font-size: 12px; letter-spacing: 3px; font-weight: 600;
  color: #f0cc6d;
  border-bottom: 1px solid rgba(212, 168, 71, 0.35);
}
.log-table td { padding: 11px 14px; color: #c4b28a; border-bottom: 1px solid rgba(90, 65, 40, 0.4); }
.log-table tbody tr:hover { background: rgba(212, 168, 71, 0.08); }
.t-time { font-size: 12px; color: #8a7b62; }
.t-amt { color: #7ed957; font-weight: 600; }
.t-bal { color: #e8c46a; }
.t-remark { font-size: 12px; color: #8a7b62; }
.t-empty { text-align: center; padding: 28px; color: #6b5d4b; letter-spacing: 2px; }
.pager {
  display: flex; align-items: center; justify-content: center; gap: 18px;
  padding: 14px;
}
.pg-btn {
  padding: 6px 18px;
  background: transparent;
  border: 1px solid var(--gold-dark);
  border-radius: 4px;
  color: #d8c9a8; font-size: 12px; cursor: pointer;
}
.pg-btn:hover:not(:disabled) { background: rgba(212, 168, 71, 0.15); color: #f0cc6d; }
.pg-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.pg-info { font-size: 12px; color: #b8a884; }
.skin-tip { margin-top: 14px; font-size: 11px; color: #8a7b62; text-align: center; letter-spacing: 1px; }

/* 充值弹窗 */
.recharge-tip { font-size: 12px; color: #8a7b62; text-align: center; margin-bottom: 16px; }
.recharge-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.recharge-opt {
  padding: 16px 0;
  background: rgba(20, 16, 12, 0.85);
  border: 1px solid var(--leather-3);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  color: #e8dcc4;
}
.recharge-opt:hover { border-color: var(--gold); }
.recharge-opt.on {
  border-color: #e6b94f;
  background: linear-gradient(160deg, rgba(230, 185, 79, 0.2), rgba(20, 16, 12, 0.9));
  box-shadow: 0 0 12px rgba(230, 185, 79, 0.35);
}
.recharge-opt .amt { display: block; font-size: 18px; font-weight: 700; color: #f0cc6d; }
.recharge-opt .bonus { display: block; margin-top: 4px; font-size: 11px; color: #8a7b62; }
.btn-main {
  padding: 8px 24px;
  background: linear-gradient(180deg, #e6b94f, #a5842f);
  border: 1px solid #7a5f1e;
  border-radius: 4px;
  color: #1a1005;
  font-weight: 700;
  letter-spacing: 3px;
  cursor: pointer;
}
.btn-main:disabled { opacity: 0.5; cursor: not-allowed; }

/* ===== 平板竖屏 ===== */
@media (max-width: 980px) {
  .shop { max-width: 100%; padding: 14px; }
  .mini-card { width: 80px; height: 108px; }
  .pool-grid.pack .mini-card { width: 80px; height: 112px; }
}

/* ===== 手机竖屏 ===== */
@media (max-width: 680px) {
  .shop { padding: 10px 10px 24px; }
  .toolbar { grid-template-columns: 1fr; gap: 8px; padding: 10px 12px; text-align: center; }
  .currency-row { flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }
  .cur-opt { padding: 7px 12px; font-size: 12px; }
  .cur-tip { display: none; }

  .pity-row { flex-direction: column; gap: 8px; padding: 8px 10px; }
  .pi-label { font-size: 11px; }
  .pi-num { font-size: 10px; min-width: 54px; }

  .pull-row { gap: 8px; }
  .pull-btn { padding: 14px 0; }
  .pull-tip { font-size: 10px; line-height: 1.7; margin-bottom: 16px; }

  .pool-grid { gap: 7px; }
  .mini-card { width: 62px; height: 84px; border-radius: 5px; }
  .pool-grid.pack .mini-card { width: 62px; height: 88px; }
  .mini-dress { font-size: 11px; }

  .result-grid { gap: 6px; }
  .result-card { width: 68px; height: 92px; }
  .rc-name { font-size: 10px; bottom: 12px; }
  .rc-rarity { font-size: 8px; }
  .rc-pity { bottom: 22px; font-size: 8px; padding: 0 4px; }
}
</style>
