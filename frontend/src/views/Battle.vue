<template>
  <div class="battle" v-if="state">

    <!-- 攻击轨迹光束: 从攻击者中心连到受击者中心, 一眼看清"谁打了谁" -->
    <svg class="atk-beam" v-if="beam" :key="beamKey">
      <line :x1="beam.x1" :y1="beam.y1" :x2="beam.x2" :y2="beam.y2" :class="beam.side" />
      <circle class="beam-tip" :class="beam.side" :cx="beam.x2" :cy="beam.y2" r="6" />
    </svg>
    <!-- 我方英雄被攻击: 全屏红色边缘闪烁 -->
    <div class="hurt-vignette" v-if="fx && fx.targetSide === 'me' && fx.dst === -1"></div>

    <!-- 左侧：炉石式滚动战斗日志（完整历史，新条目自动滚到底） -->
    <aside class="side-log" :class="{ collapsed: logCollapsed }">
      <div class="sl-head" @click="logCollapsed = !logCollapsed" title="点击折叠/展开">
        <span class="sl-title">战 斗 日 志</span>
        <span class="sl-count">{{ logList.length }}</span>
        <button class="sl-music" :class="{ off: !bgmOn }"
                :title="bgmOn ? '关闭背景音乐' : '开启背景音乐'"
                @click.stop="toggleBgm">{{ bgmOn ? '🎵' : '🔇' }}</button>
        <span class="sl-toggle">{{ logCollapsed ? '▸' : '▾' }}</span>
      </div>
      <div class="sl-body" ref="logBody" v-show="!logCollapsed">
        <template v-for="(l, i) in logList" :key="l.seq ?? i">
          <div v-if="i === 0 || logList[i - 1].turn !== l.turn" class="sl-turn">
            — 第 {{ l.turn }} 回合 —
          </div>
          <div class="sl-line" :class="logSide(l)">
            <span class="sl-text">{{ l.text }}</span>
          </div>
        </template>
        <div v-if="!logList.length" class="sl-empty">暂无日志</div>
      </div>
    </aside>

    <!-- 敌方英雄信息条 -->
    <div class="hero-bar foe-bar" :class="{ 'hero-hit': isHeroHit('foe'), 'hp-target': heroTgt }"
         @click="heroTgt && heroTo(-1)">
      <div class="hero-info">
        <span class="hero-avatar foe-avatar"><img :src="foe.userId < 0 ? '/ak/pepe.gif' : '/ak/explore.png'" alt="对手" /></span>
        <div class="hero-detail">
          <span class="hero-name">{{ foe.nickname }}</span>
          <span class="hero-stat">HP <b :class="{'hp-low': foe.hp <= 10}">{{ foe.hp }}</b>　手牌 {{ foe.handCount ?? foe.hand.length }}　牌库 <b class="deck-num" :class="{ 'deck-draw': foeDrawing }">{{ foe.deckCount }}</b>
            <span v-if="foe.fatigue" class="fatigue-tag">疲劳 {{ foe.fatigue }}</span>
          </span>
        </div>
      </div>
      <div class="crystals" :title="`法力 ${foe.mana}/${foe.maxMana}`">
        <span v-for="i in 10" :key="'fc'+i" class="crystal"
              :class="{ active: i <= foe.mana, charged: i > foe.mana && i <= foe.maxMana }"></span>
        <span class="mana-num">💎 {{ foe.mana }}/{{ foe.maxMana }}</span>
      </div>
      <span v-if="isHeroHit('foe')" class="dmg-num hero-dmg" :class="{ big: fx.damage >= 5 }">-{{ fx.damage }}</span>
      <span v-if="isHeroHeal('foe')" class="heal-num hero-heal">+{{ fx.heal }}</span>
    </div>

    <!-- 敌方战场 -->
    <div class="board foe-board">
      <CardFace v-for="(c, i) in foe.board" :key="'f'+i" :card="c" size="md"
           class="foe-card mine-target"
           :class="[{ hit: isCardHit('foe', i), attacking: isCardAttacking('foe', i),
                      'shield-break': isShieldBreak('foe', i), 'hp-target': heroTgt }, 'r' + (c.rarity || 1)]"
           @click="heroTgt ? heroTo(i) : (selecting !== null ? attack(i) : showDetail(c))"
           @contextmenu.prevent="showDetail(c)"
           @pointerdown="lpStart(c)" @pointerup="lpEnd" @pointerleave="lpEnd" @pointercancel="lpEnd" />
      <div v-if="foe.board.length === 0" class="board-empty">— 空 —</div>
      <div v-if="fx && fx.targetSide === 'foe' && fx.dst >= 0" class="fx-dmg" :style="{ left: fxPos }">
        <span class="slash"></span>
        <span v-if="fx.poison" class="poison-ring"></span>
        <span v-if="fx.poison" class="poison-mark">☠</span>
        <template v-if="fx.targetDied">
          <span class="death-puff"></span>
          <span v-for="n in 6" :key="'fd'+n" class="shard" :class="['s' + n, { poison: fx.poison }]"></span>
        </template>
        <span class="dmg-num" :class="{ big: fx.damage >= 5, poison: fx.poison }">-{{ fx.damage }}</span>
      </div>
    </div>

    <!-- 中央回合控制 -->
    <div class="center-bar">
      <div class="center-left">
        <span class="turn-badge">回 合 {{ state.turnNo }}</span>
        <span class="turn-tag" :class="{'my-turn': myTurn}">
          {{ myTurn ? '▶ 你 的 回 合' : '等 对 手 行 动' }}
        </span>
      </div>
      <div class="center-right">
        <button class="btn-battle hero-btn"
                :class="{ armed: heroTgt }"
                :disabled="!myTurn || me.heroPowerUsed || me.mana < 2"
                :title="me.heroPowerUsed ? '本回合已用过英雄技能' : (me.mana < 2 ? '法力不足 2 点' : '消耗 2 点法力，对任意目标造成 1 点伤害')"
                @click="startHeroPower">
          {{ heroTgt ? '选择目标…' : '⚡ 英雄技能 2💎' }}
        </button>
        <button class="btn-battle danger" @click="surrender">投降</button>
        <button class="btn-battle" :disabled="!myTurn" @click="endTurn">结束回合</button>
        <button class="btn-battle danger-solid"
                :disabled="!myTurn || selecting === null"
                @click="attackHero">攻击英雄脸</button>
      </div>
    </div>

    <!-- 我方战场 -->
    <div class="board my-board">
      <CardFace v-for="(c, i) in me.board" :key="'m'+i" :card="c" size="md"
           :sleeping="!!c.sick"
           class="mine-card attacker"
           :title="myTurn
             ? (c.sick ? '召唤失调：下个回合才能攻击' : (c.canAct === false ? '本回合已攻击过' : '点击选择, 再点目标攻击'))
             : '等待对手行动'"
           :class="[{ sel: selecting === i,
             'can-act': myTurn && c.canAct !== false,
             'no-act': c.canAct === false,
             'acted': c.canAct === false && !c.sick,
             attacking: isCardAttacking('me', i),
             hit: isCardHit('me', i),
             'shield-break': isShieldBreak('me', i) }, 'r' + (c.rarity || 1)]"
           @click="pick(i)"
           @contextmenu.prevent="showDetail(c)"
           @pointerdown="lpStart(c)" @pointerup="lpEnd" @pointerleave="lpEnd" @pointercancel="lpEnd">
        <span v-if="selecting === i" class="target-hint">选 择 目 标 ▲</span>
      </CardFace>
      <div v-if="me.board.length === 0" class="board-empty">— 空 —</div>
      <div v-if="fx && fx.targetSide === 'me' && fx.dst >= 0" class="fx-dmg" :style="{ left: fxPos }">
        <span class="slash"></span>
        <span v-if="fx.poison" class="poison-ring"></span>
        <span v-if="fx.poison" class="poison-mark">☠</span>
        <template v-if="fx.targetDied">
          <span class="death-puff"></span>
          <span v-for="n in 6" :key="'md'+n" class="shard" :class="['s' + n, { poison: fx.poison }]"></span>
        </template>
        <span class="dmg-num" :class="{ big: fx.damage >= 5, poison: fx.poison }">-{{ fx.damage }}</span>
      </div>
    </div>

    <!-- 我方英雄信息条 -->
    <div class="hero-bar me-bar" :class="{ 'hero-hit': isHeroHit('me') }">
      <div class="hero-info">
        <span class="hero-avatar me-avatar"><img src="/ak/sit.png" alt="我" /></span>
        <div class="hero-detail">
          <span class="hero-name">{{ me.nickname }}</span>
          <span class="hero-stat">HP <b :class="{'hp-low': me.hp <= 10}">{{ me.hp }}</b>
            <span v-if="me.fatigue" class="fatigue-tag"
                  :title="`牌库已空, 下次抽牌将受到 ${me.fatigue + 1} 点疲劳伤害`">疲劳 {{ me.fatigue }}</span>
          </span>
        </div>
      </div>
      <div class="crystals own" :title="`法力 ${me.mana}/${me.maxMana}`">
        <span v-for="i in 10" :key="'mc'+i" class="crystal"
              :class="{ active: i <= me.mana, charged: i > me.mana && i <= me.maxMana }"></span>
        <span class="mana-num">💎 {{ me.mana }}/{{ me.maxMana }}</span>
      </div>
      <span v-if="isHeroHit('me')" class="dmg-num hero-dmg" :class="{ big: fx.damage >= 5 }">-{{ fx.damage }}</span>
      <span v-if="isHeroHeal('me')" class="heal-num hero-heal">+{{ fx.heal }}</span>
    </div>

    <!-- 手牌 -->
    <div class="hand-area">
      <div class="hand" :class="{ full: me.hand.length > 7 }">
        <CardFace v-for="(c, i) in me.hand" :key="'h'+i" :card="c" size="sm"
             class="hand-card"
             :class="[{ unaffordable: myTurn && c.cost > me.mana,
                       'just-drawn': drawnIdx.includes(i) }]"
             @click="play(i)"
             @contextmenu.prevent="showDetail(c)"
             @pointerdown="lpStart(c)" @pointerup="lpEnd" @pointerleave="lpEnd" @pointercancel="lpEnd" />
      </div>
      <!-- 牌库牌堆: 抽牌动画的"出发地", 抽牌时整体抖一下 -->
      <div class="deck-pile" :class="{ drawing: drawing }" :title="`牌库剩余 ${me.deckCount} 张`">
        <span class="dp-card c1"></span>
        <span class="dp-card c2"></span>
        <span class="dp-card c3"></span>
        <span class="dp-num">{{ me.deckCount }}</span>
      </div>
      <div class="hand-tip">右键 / 长按卡牌 查看干员介绍</div>
    </div>

    <!-- 操作提示（完整日志已移到左侧面板，这里只留一行引导） -->
    <div class="logs">
      <div v-if="heroTgt" class="log-line hint">
        ⚡ 英雄技能：点击敌方随从或敌方英雄条，造成 1 点伤害
      </div>
      <div v-else-if="selecting !== null" class="log-line hint">
        点击对方随从进行攻击；或点「攻击英雄脸」直接打脸
      </div>
    </div>

    <!-- 结算弹窗 -->
    <el-dialog v-model="over" :title="overTitle" width="320px" center :close-on-click-modal="false">
      <div class="result-body">
        <img :src="resultMascot" class="result-mascot" alt="" />
        <div class="result-text">{{ overText }}</div>
        <div class="ak-credit">Q 版形象与干员立绘素材来自《明日方舟》(© Hypergryph) · 仅用于学习演示</div>
      </div>
      <button class="btn-main" @click="$router.push('/lobby')">返回大厅</button>
    </el-dialog>

    <!-- 卡牌速览(右键/长按): 战斗内精简版, 不含时装装配 -->
    <CardDetail v-model="detailOpen" :card="detailCard" hide-skins />
  </div>

  <!-- 快照到达前的占位: 进不去对局时给出明确原因 + 重试, 而不是白屏 -->
  <div class="battle-loading" v-else>
    <div class="bl-box">
      <div class="bl-spin" v-if="!loadError"></div>
      <div class="bl-icon" v-else>⚠</div>
      <p class="bl-title">{{ loadError ? '进入对局失败' : '正在进入对局…' }}</p>
      <p class="bl-sub" v-if="loadError">{{ loadError }}</p>
      <p class="bl-sub" v-else>房间号 {{ roomId || '(未获取)' }}</p>
      <div class="bl-actions">
        <button class="btn-main" @click="$router.push('/lobby')">返回大厅</button>
        <button class="btn-plain" v-if="loadError" @click="reload">重 试</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { connect, removeListener, getClient } from '../api/ws'
import http from '../api/request'
import { cardSkin, cardArt, loadSkinMap } from '../api/skin'
import CardFace from '../components/CardFace.vue'
import CardDetail from '../components/CardDetail.vue'
import sfx from '../utils/sound'

const state = ref(null)
const loadError = ref('')     // 快照拉取失败原因(为空=还在加载)
const reload = () => location.reload()
const selecting = ref(null)
const heroTgt = ref(false)    // 英雄技能选目标中
const over = ref(false)
const overTitle = ref('')
const overText = ref('')
const resultMascot = ref('/ak/photo.gif')
const fx = ref(null)          // 当前攻击动画: { src, dst, damage, attackerSide, targetSide, ... }
let lastSeq = 0               // 已播放的动作序号, 防止重复播放
let fxTimer = null
const roomId = localStorage.getItem('roomId')
const uid = JSON.parse(localStorage.getItem('user')).id

// 注意: 快照到达前 state 为 null, 而下方 watch(handSig/deckCount) 会在 setup 阶段
// 立即求值一次, 所以这里必须整体判空, 否则 null.p2 直接把组件渲染崩掉(黑屏)。
const me = computed(() => {
  if (!state.value) return null
  return state.value.p1?.userId === uid ? state.value.p1 : state.value.p2
})
const foe = computed(() => {
  if (!state.value) return null
  return state.value.p1?.userId === uid ? state.value.p2 : state.value.p1
})
const myTurn = computed(() => state.value?.currentUserId === uid)
const iWin = computed(() => state.value?.winnerId === uid)

/**
 * 抽牌动画: 服务端抽牌一律 append 到手牌末尾, 出牌则从任意位置移除。
 * 所以只要用多重集算出"新增了几张"(打出一张+战吼抽一张也测得出来),
 * 末尾这 k 张就是刚抽到的, 让它们从牌库位置飞入。
 */
const drawnIdx = ref([])
const drawing = ref(false)
let drawTimer = null
const sigOf = (c) => `${c.id}|${c.name}|${c.cost}`
const handSig = computed(() => (me.value?.hand || []).map(sigOf))
watch(handSig, (nv, ov) => {
  if (!ov || !ov.length || !nv || !nv.length) return
  const pool = new Map()
  for (const s of ov) pool.set(s, (pool.get(s) || 0) + 1)
  let k = 0
  for (const s of nv) {
    const left = pool.get(s) || 0
    if (left > 0) pool.set(s, left - 1)
    else k++
  }
  if (k <= 0) return
  k = Math.min(k, nv.length)
  drawnIdx.value = Array.from({ length: k }, (_, j) => nv.length - k + j)
  drawing.value = true
  sfx.play('pull')
  clearTimeout(drawTimer)
  drawTimer = setTimeout(() => { drawnIdx.value = []; drawing.value = false }, 760)
})
/** 对手英雄条的牌库数字: 对方抽牌时闪一下(看不到对方手牌, 只能从这里感知) */
const foeDrawing = ref(false)
let foeDrawTimer = null
watch(() => foe.value?.deckCount ?? 0, (n, o) => {
  if (n >= o) return
  foeDrawing.value = true
  clearTimeout(foeDrawTimer)
  foeDrawTimer = setTimeout(() => { foeDrawing.value = false }, 700)
})

const bg = (c) => {
  const art = cardArt(c.name) || (c && c.img)
  return art ? { backgroundImage: `url(${art})` } : {}
}

/* === 攻击动画 === */
const isCardHit = (side, i) => !!fx.value && fx.value.targetSide === side && fx.value.dst === i
const isCardAttacking = (side, i) => !!fx.value && fx.value.attackerSide === side && fx.value.src === i
const isHeroHit = (side) => !!fx.value && fx.value.targetSide === side && fx.value.dst === -1
/** 圣盾被击碎: 给受击卡播一层碎盾白光 */
const isShieldBreak = (side, i) => isCardHit(side, i) && !!fx.value.shieldBroken
/** 吸血治疗: 攻击方英雄条上飘绿色 +N */
const isHeroHeal = (side) => !!fx.value && fx.value.heal > 0 && fx.value.attackerSide === side
/** 伤害数字水平位置: 按目标在战场中的槽位估算百分比 */
const fxPos = computed(() => {
  if (!fx.value || fx.value.dst < 0) return '50%'
  const board = fx.value.targetSide === 'me' ? me.value?.board : foe.value?.board
  const n = Math.max(board?.length || 1, 1)
  return ((Math.min(fx.value.dst, n - 1) + 0.5) / n * 100) + '%'
})

function triggerFx(snapshot) {
  const a = snapshot && snapshot.lastAction
  if (!a || a.seq === lastSeq) return
  lastSeq = a.seq
  const attackerSide = a.attackerUserId === uid ? 'me' : 'foe'
  const targetSide = attackerSide === 'me' ? 'foe' : 'me'
  fx.value = { ...a, attackerSide, targetSide }
  markBeam()
  // 音效: 攻击是金属撞击, 英雄技能是扫频; 挨打的是我时才放受伤音
  if (a.type === 'heropower') sfx.play('hero')
  else sfx.play('attack')
  if (targetSide === 'me' && a.dst === -1) sfx.play('hurt')
  clearTimeout(fxTimer)
  fxTimer = setTimeout(() => { fx.value = null }, 900)
}

/**
 * 攻击光束: 等 DOM 挂上 .attacking / .hit 后, 量出攻击者与受击者的实际位置,
 * 画一条全屏 SVG 光束连过去(金色=我方出手, 红色=敌方出手)。
 */
const beam = ref(null)
const beamKey = ref(0)
let beamTimer = null
function markBeam() {
  nextTick(() => {
    const fxv = fx.value
    if (!fxv) return
    const atkEl = document.querySelector((fxv.attackerSide === 'me' ? '.my-board' : '.foe-board') + ' .card.attacking')
    let tgtEl = null
    if (fxv.dst >= 0) {
      tgtEl = document.querySelector((fxv.targetSide === 'me' ? '.my-board' : '.foe-board') + ' .card.hit')
    } else {
      tgtEl = document.querySelector(fxv.targetSide === 'me' ? '.me-bar' : '.foe-bar')
    }
    if (!atkEl || !tgtEl) return
    const a = atkEl.getBoundingClientRect()
    const t = tgtEl.getBoundingClientRect()
    beam.value = {
      side: fxv.attackerSide,
      x1: +(a.left + a.width / 2).toFixed(1),
      y1: +(a.top + a.height / 2).toFixed(1),
      x2: +(t.left + t.width / 2).toFixed(1),
      y2: +(t.top + t.height / 2).toFixed(1)
    }
    beamKey.value++
    clearTimeout(beamTimer)
    beamTimer = setTimeout(() => { beam.value = null }, 750)
  })
}


/** 只带 roomId 与操作参数; 身份由 WS 握手的 token 决定, 不再由前端携带 userId */
function send(action) {
  getClient().publish({ destination: '/app/battle.action', body: JSON.stringify({ roomId, ...action }) })
  selecting.value = null
  heroTgt.value = false
}
const play = (i) => { if (lpTaken()) return; sfx.play('play'); send({ type: 'play', handIndex: i }) }
/**
 * 卡牌速览: 桌面右键 / 手机长按 打开干员档案, 与"点击出牌"互不干扰
 * 长按触发后, 紧随其后的 click 会被 lpTaken() 吃掉, 避免顺手把牌打出去
 */
const detailOpen = ref(false)
const detailCard = ref({})
let lpTimer = null
let lpFired = false

function showDetail(c) {
  if (!c || !c.name) return
  detailCard.value = c
  detailOpen.value = true
  sfx.play('card')
}
function lpStart(c) {
  clearTimeout(lpTimer)
  lpFired = false
  lpTimer = setTimeout(() => {
    lpFired = true
    showDetail(c)
    // 兜底: 万一 click 没来(卡片被重绘), 0.8 秒后自动解除抑制, 避免吞掉下一次正常点击
    setTimeout(() => { lpFired = false }, 800)
  }, 420)
}
function lpEnd() { clearTimeout(lpTimer); lpTimer = null }
/** 本次 click 是否只是长按的副产物 */
function lpTaken() {
  if (!lpFired) return false
  lpFired = false
  return true
}

const endTurn = () => send({ type: 'end' })
const surrender = () => confirm('确定投降?') && send({ type: 'surrender' })

/** 英雄技能(2费1点伤害, 每回合一次): 先进入选目标状态, 再点敌方随从/英雄条 */
function startHeroPower() {
  if (!myTurn.value) return ElMessage.warning('还没轮到你的回合')
  if (me.value.heroPowerUsed) return ElMessage.warning('本回合的英雄技能已经用过了')
  if (me.value.mana < 2) return ElMessage.warning('法力不足，英雄技能需要 2 点法力')
  selecting.value = null
  heroTgt.value = !heroTgt.value
}
function heroTo(idx) {
  sfx.play('hero')
  send({ type: 'hero', targetIndex: idx })
}
/** 选择我方随从: 召唤失调(本回合刚上场且无冲锋) / 已攻击过的不能选, 给出明确提示 */
const pick = (i) => {
  if (lpTaken()) return
  const c = me.value?.board?.[i]
  if (!c) return
  if (!myTurn.value) return ElMessage.warning('还没轮到你的回合')
  if (c.sick) return ElMessage.warning('召唤失调：「' + c.name + '」下个回合才能攻击（拥有「冲锋」的随从上场即可攻击）')
  if (c.canAct === false) return ElMessage.warning('「' + c.name + '」本回合已经攻击过了')
  selecting.value = selecting.value === i ? null : i
}
const attack = (targetBoardIdx) => {
  if (lpTaken()) return
  if (selecting.value !== null) send({ type: 'attack', boardIndex: selecting.value, targetIndex: targetBoardIdx })
}
const attackHero = () => {
  if (selecting.value === null) return
  const c = me.value?.board?.[selecting.value]
  // 突袭随从登场当回合只能打随从(与服务端 rushLock 校验保持一致, 这里先做一次友好拦截)
  if (c && c.rushLock) return ElMessage.warning('「突袭」随从登场当回合只能攻击随从，不能攻击英雄')
  send({ type: 'attack', boardIndex: selecting.value, targetIndex: -1 })
}

/**
 * 结算弹窗: kind = win / lose / draw, note 为额外说明(房间回收原因)
 */
function finish(kind, note) {
  setTimeout(() => {
    if (kind === 'win') {
      overTitle.value = '🏆 胜 利'; overText.value = '恭喜！天梯积分 +25'
      resultMascot.value = '/ak/photo.gif'; sfx.play('win')
    } else if (kind === 'lose') {
      overTitle.value = '💀 失 败'; overText.value = '再接再厉！天梯积分 -25'
      resultMascot.value = '/ak/read.gif'; sfx.play('lose')
    } else {
      overTitle.value = '🤝 平 局'; overText.value = '本局判和，天梯积分不变'
      resultMascot.value = '/ak/fly.gif'; sfx.play('ding')
    }
    if (note) overText.value = note + ' · ' + overText.value
    over.value = true
  }, 650)   // 等最后一击动画播完再弹结算
  localStorage.removeItem('roomId')
}

/** 房间超时回收原因 -> 中文说明 */
function reasonNote(reason) {
  if (reason === 'IDLE') return '双方长时间无操作，房间已回收'
  if (reason === 'LIMIT') return '本局达到时长上限，房间已回收'
  if (reason === 'OFFLINE') return '对局中有玩家掉线超时未重连'
  return ''
}

/**
 * 背景音乐: 纯 Web Audio 合成的氛围铺底(见 utils/sound.js), 默认开启
 * 开关偏好存 localStorage.bgmOn, 进对战页淡入、离开淡出
 */
const bgmOn = ref(sfx.isBgmOn())
function toggleBgm() {
  bgmOn.value = !bgmOn.value
  sfx.setBgmOn(bgmOn.value)
  if (bgmOn.value) { sfx.startBgm(); sfx.play('ding') } else { sfx.stopBgm(); sfx.play('click') }
}

/**
 * 左侧日志面板: 累积完整历史
 * 服务端每次只下发最后 10 条(logTail), 用 seq 去重即可把多次推送拼成完整日志。
 * 没有 seq 的老数据退化为 turn+text 作为键(可能重复, 但不影响可读性)。
 */
const logCollapsed = ref(false)
const logBody = ref(null)
const logMap = new Map()          // seq -> 日志条目(用普通 Map, 变化由 logVersion 触发)
const logVersion = ref(0)

function mergeLogs(tail) {
  if (!tail || !tail.length) return
  for (const l of tail) {
    const key = l.seq != null ? l.seq : (l.turn + '|' + l.text)
    if (!logMap.has(key)) logMap.set(key, l)
  }
  logVersion.value++
  nextTick(scrollLogToEnd)
}

/** 按 seq 排序后的完整日志 */
const logList = computed(() => {
  logVersion.value            // 依赖追踪
  return [...logMap.values()].sort((a, b) => (a.seq ?? 0) - (b.seq ?? 0))
})

/** 条目归属: system 灰 / 我方金 / 敌方红 */
function logSide(l) {
  const a = String(l.actor)
  if (a === 'system' || a === 'null' || a === 'undefined') return 'sys'
  return a === String(uid) ? 'mine' : 'foe'
}

function scrollLogToEnd() {
  const el = logBody.value
  if (el) el.scrollTop = el.scrollHeight
}

/** 实时推送处理: 服务端通过 /topic/user/{uid} 推送 state / game_over / room_closed */
function onMsg(m) {
  // 服务端回执: 非法操作(法力不足 / 被嘲讽拦截 / 非本人房间等)在 WS 上没有返回值,
  // 靠这条私密 error 帧把原因告诉玩家, 否则点了牌却毫无反应。
  if (m.event === 'error') return ElMessage.warning(m.msg || '操作失败')
  if (m.event === 'game_over') {
    triggerFx(m.snapshot)
    state.value = m.snapshot
    mergeLogs(m.snapshot.logTail)
    const w = m.snapshot.winnerId
    finish(w == null ? 'draw' : (w === uid ? 'win' : 'lose'), '')
  } else if (m.event === 'room_closed') {
    // 房间保洁: 空闲超时 / 总时长超限 -> 判和; 掉线超时 -> 掉线方判负
    state.value = m.snapshot
    mergeLogs(m.snapshot.logTail)
    const w = m.snapshot.winnerId
    finish(w == null ? 'draw' : (w === uid ? 'win' : 'lose'), reasonNote(m.reason))
  } else if (m.event === 'state' && m.roomId === roomId) {
    triggerFx(m)
    state.value = m
    mergeLogs(m.logTail)
  }
}

onMounted(async () => {
  if (bgmOn.value) sfx.startBgm()   // 氛围铺底(失败不影响页面, 内部已 try/catch)
  connect(onMsg)                    // 注册监听(连接复用全局单例)
  // 皮肤只影响卡框/立绘, 绝不能阻塞进对局: 不 await, 失败也不管
  loadSkinMap().catch(() => {})
  // 解决 STOMP 竞态: 进入 battle 时主动拉一次当前快照(这是进对局的关键路径)
  try {
    const rid = roomId || localStorage.getItem('roomId')
    if (!rid) throw new Error('本地没有房间号, 请从大厅重新进入')
    const snap = await http.get(`/battle/${rid}/snapshot`)
    if (!snap) throw new Error('服务端返回了空快照')
    state.value = snap   // axios 拦截器已 return resp.data.data, snap 就是快照对象
    lastSeq = snap.lastAction ? snap.lastAction.seq : 0   // 历史动作不重播
    mergeLogs(snap.logTail)   // 进房时先补上开局日志
  } catch (e) {
    console.error('[battle] 拉取战斗快照失败', e)
    loadError.value = e?.response?.data?.msg || e?.message || '未知错误'
  }
})
onUnmounted(() => {
  removeListener(onMsg)
  clearTimeout(fxTimer); clearTimeout(beamTimer)
  clearTimeout(drawTimer); clearTimeout(foeDrawTimer)
  sfx.stopBgm()
})
</script>

<style scoped>
/**
 * 布局改为「左侧日志 + 右侧战场」两列。
 * 用 grid 而不是包一层 div: 原有子元素直接进第二列, 不必改动 DOM 层级;
 * 日志面板跨所有行占第一列, 高度随战场撑满。
 */
.battle {
  max-width: 1180px;
  margin: 0 auto;
  padding: 18px;
  color: #d9dee5;
  position: relative;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}
.battle > :not(.side-log) { grid-column: 2; }
.side-log { grid-column: 1; grid-row: 1 / -1; }

/* === 左侧: 炉石式滚动战斗日志 === */
.side-log {
  position: sticky;
  top: 18px;
  display: flex;
  flex-direction: column;
  background: rgba(8, 10, 13, 0.82);
  border: 1px solid rgba(120, 80, 30, 0.55);
  border-radius: 8px;
  box-shadow: inset 0 1px 4px rgba(0, 0, 0, 0.6), 0 4px 18px rgba(0, 0, 0, 0.4);
  overflow: hidden;
}
.sl-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: linear-gradient(180deg, rgba(240, 165, 42, 0.18), rgba(240, 165, 42, 0.05));
  border-bottom: 1px solid rgba(240, 165, 42, 0.3);
  cursor: pointer;
  user-select: none;
}
.sl-title {
  flex: 1;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 3px;
  color: var(--gold-light);
}
.sl-count {
  min-width: 20px;
  padding: 1px 6px;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.08);
  font-size: 10px;
  color: #9aa4b0;
  text-align: center;
}
.sl-toggle { font-size: 11px; color: #6b7683; }
/* 背景音乐开关(挂在日志头部右侧, 点击不触发折叠) */
.sl-music {
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid rgba(240, 165, 42, 0.45);
  border-radius: 50%;
  background: rgba(240, 165, 42, 0.12);
  font-size: 11px;
  line-height: 1;
  cursor: pointer;
  transition: all .18s ease;
  animation: bgmPulse 2.4s ease-in-out infinite;
}
.sl-music:hover { background: rgba(240, 165, 42, 0.28); transform: scale(1.08); }
.sl-music.off {
  border-color: rgba(150, 160, 172, 0.35);
  background: rgba(255, 255, 255, 0.05);
  animation: none;
  filter: grayscale(1);
  opacity: .65;
}
@keyframes bgmPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(240, 165, 42, 0.45); }
  50%      { box-shadow: 0 0 0 4px rgba(240, 165, 42, 0); }
}
.sl-body {
  padding: 8px 10px;
  max-height: 560px;
  overflow-y: auto;
  scroll-behavior: smooth;
}
.sl-body::-webkit-scrollbar { width: 5px; }
.sl-body::-webkit-scrollbar-thumb {
  background: rgba(240, 165, 42, 0.35);
  border-radius: 3px;
}
.sl-turn {
  margin: 8px 0 5px;
  text-align: center;
  font-size: 10px;
  letter-spacing: 2px;
  color: #5c6672;
  border-top: 1px solid rgba(120, 80, 30, 0.25);
  padding-top: 6px;
}
.sl-line {
  padding: 3px 6px;
  font-size: 11.5px;
  line-height: 1.55;
  color: #9aa4b0;
  border-left: 2px solid transparent;
  border-radius: 2px;
  word-break: break-word;
}
.sl-line + .sl-line { margin-top: 1px; }
/* 我方: 金色调 / 敌方: 红色调 / 系统: 灰 */
.sl-line.mine {
  color: #d9dee5;
  border-left-color: rgba(240, 165, 42, 0.75);
  background: rgba(240, 165, 42, 0.07);
}
.sl-line.foe {
  color: #c9a0a0;
  border-left-color: rgba(226, 85, 79, 0.7);
  background: rgba(226, 85, 79, 0.06);
}
.sl-line.sys { color: #7c8794; font-style: italic; }
.sl-empty { padding: 20px 0; text-align: center; font-size: 11px; color: #5c6672; }
.side-log.collapsed .sl-body { display: none; }
.battle::before {
  content: "";
  position: fixed;
  inset: 0;
  background: url('/bg/battle.jpg') center / cover no-repeat;
  filter: saturate(0.85) brightness(0.75);
  opacity: 0.36;
  z-index: -1;
  pointer-events: none;
}
.battle::after {
  content: "";
  position: fixed;
  inset: 0;
  background: radial-gradient(1000px 600px at 50% 45%, transparent 0%, rgba(6, 8, 10, 0.55) 65%, rgba(4, 6, 8, 0.88) 100%);
  z-index: -1;
  pointer-events: none;
}

/* === 英雄信息条 === */
.hero-bar {
  display: flex;
  justify-content: flex-end;
  padding: 8px 16px;
  border-radius: 8px;
  margin-bottom: 8px;
}
.foe-bar { background: rgba(100, 30, 30, 0.25); border: 1px solid rgba(226, 85, 79, 0.4); }
.me-bar { background: rgba(30, 60, 100, 0.25); border: 1px solid rgba(79, 214, 230, 0.4); margin-top: 8px; }
.foe-bar { flex-direction: row-reverse; }

.hero-info { display: flex; align-items: center; gap: 12px; }
.hero-avatar {
  width: 52px; height: 52px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
  border: 2px solid var(--gold);
  box-shadow: 0 0 10px rgba(240, 165, 42, 0.35);
  flex-shrink: 0;
}
.hero-avatar img {
  width: 88%; height: 88%;
  object-fit: contain;
  object-position: center bottom;
}
.foe-avatar { background: linear-gradient(135deg, #7a1a1a, #3d0a0a); }
.me-avatar { background: linear-gradient(135deg, #1a3d7a, #0a1f3d); }
.hero-detail { display: flex; flex-direction: column; gap: 2px; }
.hero-name { font-weight: 600; color: #ffc45c; font-size: 15px; }
.hero-stat { font-size: 12px; color: #8d97a3; letter-spacing: 0.5px; }
.hp-low { color: var(--blood); text-shadow: 0 0 4px rgba(226, 85, 79, 0.5); }
/* 疲劳标记: 牌库抽空后出现, 层数越高下次抽牌掉血越多 */
.fatigue-tag {
  display: inline-block; margin-left: 8px; padding: 1px 7px; border-radius: 999px;
  font-size: 11px; font-weight: 600; letter-spacing: .3px;
  color: #ffd8d8; background: rgba(163, 45, 45, .85); border: 1px solid rgba(247, 193, 193, .5);
  vertical-align: middle;
}
.mana-num { margin-left: 10px; color: #8fc4f5; }

/* === 法力水晶 === */
.crystals {
  display: flex;
  gap: 3px;
  align-items: center;
  margin-left: 18px;
}
.foe-bar .crystals { margin-left: 0; margin-right: 18px; }
/* 法力水晶: 菱形切割的蓝水晶(炉石式) */
.crystal {
  width: 13px;
  height: 18px;
  clip-path: polygon(50% 0%, 100% 28%, 100% 72%, 50% 100%, 0% 72%, 0% 28%);
  background: linear-gradient(160deg, #1b2229, #0b0e11);
  box-shadow: inset 0 0 0 1px rgba(70, 82, 95, 0.9);
  transition: all 0.25s;
}
/* 已用完的空槽(本回合最大法力内) */
.crystal.charged {
  background: linear-gradient(160deg, rgba(120, 180, 240, 0.35), rgba(25, 60, 110, 0.5));
  box-shadow: inset 0 0 0 1px rgba(120, 200, 240, 0.55), 0 0 4px rgba(79, 214, 230, 0.25);
}
/* 可用水晶: 蓝白发光 */
.crystal.active {
  background: linear-gradient(160deg, #eaf6ff 5%, #6fd8f0 45%, #2a63b8 100%);
  box-shadow: inset 0 0 0 1px #bff2ff, 0 0 8px rgba(110, 214, 240, 0.9);
}
.own .crystal.active {
  background: linear-gradient(160deg, #f2fbff 5%, #7fdcff 45%, #2f6fd0 100%);
  box-shadow: inset 0 0 0 1px #d6f6ff, 0 0 11px rgba(126, 210, 255, 0.95);
}
/* 法力数字 */
.mana-num {
  margin-left: 8px;
  padding: 1px 8px;
  border-radius: 9px;
  background: rgba(20, 40, 70, 0.7);
  border: 1px solid rgba(120, 200, 240, 0.45);
  font-size: 11px;
  color: #a9dcff;
  letter-spacing: 1px;
}

/* 费用不足的手牌: 灰显, 费用球转红 */
.hand-card.unaffordable { filter: grayscale(0.6) brightness(0.72); }
.hand-card.unaffordable:hover { filter: grayscale(0.4) brightness(0.85); transform: translateY(-4px); }
.cost-orb.no-mana {
  background: radial-gradient(circle at 35% 30%, rgba(255, 170, 170, 0.9), rgba(160, 40, 40, 0.95) 55%, rgba(80, 15, 15, 1));
  box-shadow: 0 0 8px rgba(226, 85, 79, 0.75), 0 2px 6px rgba(0, 0, 0, 0.6);
}

/* === 木板纹路战场 === */
.board {
  min-height: 138px;
  display: flex; gap: 10px; justify-content: center; flex-wrap: wrap;
  padding: 16px 12px;
  margin: 4px 0;
  border-radius: 10px;
  background:
    repeating-linear-gradient(90deg,
      rgba(0, 0, 0, 0.18) 0px, rgba(0, 0, 0, 0.18) 1px,
      transparent 1px, transparent 42px),
    linear-gradient(180deg, #4a3018 0%, #2d1d0d 100%);
  border: 1px solid rgba(120, 80, 30, 0.55);
  box-shadow: inset 0 2px 6px rgba(0, 0, 0, 0.6);
}
.board-empty {
  align-self: center; color: #6b5d4b; font-size: 12px; letter-spacing: 4px;
}

/* === 中央控制 === */
.center-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 20px;
  margin: 6px 0;
  background: linear-gradient(90deg, rgba(0,0,0,0.4), rgba(26, 31, 36, 0.75), rgba(0,0,0,0.4));
  border-top: 1px solid rgba(240, 165, 42, 0.25);
  border-bottom: 1px solid rgba(240, 165, 42, 0.25);
  border-radius: 4px;
}
.center-left { display: flex; gap: 14px; align-items: center; }
.center-right { display: flex; gap: 10px; }

.turn-badge {
  padding: 4px 14px;
  background: linear-gradient(180deg, #b8781a, #6b5020);
  border-radius: 4px;
  font-size: 13px; font-weight: 600;
  color: #100d07; letter-spacing: 2px;
  box-shadow: 0 2px 6px rgba(165, 132, 47, 0.4);
}
.turn-tag {
  font-size: 12px; letter-spacing: 3px;
  color: #6b5d4b;
}
.turn-tag.my-turn {
  color: #ffc45c;
  text-shadow: 0 0 6px rgba(255, 196, 92, 0.5);
}

.btn-battle {
  padding: 6px 16px;
  border: 1px solid var(--gold-dark);
  border-radius: 4px;
  background: rgba(26, 31, 36, 0.9);
  color: #ffc45c;
  font-size: 12px; font-weight: 600; letter-spacing: 1px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-battle:hover:not(:disabled) {
  background: rgba(240, 165, 42, 0.2);
  box-shadow: 0 0 10px rgba(240, 165, 42, 0.4);
}
.btn-battle:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-battle.danger { border-color: var(--blood); color: #ffdada; }
.btn-battle.danger:hover:not(:disabled) { background: rgba(226, 85, 79, 0.25); box-shadow: 0 0 10px rgba(226, 85, 79, 0.5); }
.btn-battle.danger-solid {
  background: linear-gradient(180deg, #a82a2a, #6b1a1a);
  border-color: #7a1f1f;
  color: #fff;
}
.btn-battle.danger-solid:hover:not(:disabled) { box-shadow: 0 0 12px rgba(226, 85, 79, 0.5); }
/* 英雄技能按钮: 青色能量感; armed=正在选目标 */
.btn-battle.hero-btn { border-color: #4fd6e6; color: #a4eef6; }
.btn-battle.hero-btn:hover:not(:disabled) { background: rgba(79, 214, 230, 0.22); box-shadow: 0 0 12px rgba(79, 214, 230, 0.5); }
.btn-battle.hero-btn.armed {
  background: linear-gradient(180deg, #2f8fa3, #1a5a68);
  border-color: #a4eef6;
  color: #eaffff;
  animation: heroPulse 1s ease-in-out infinite;
}
@keyframes heroPulse {
  0%, 100% { box-shadow: 0 0 6px rgba(79, 214, 230, 0.4); }
  50% { box-shadow: 0 0 18px rgba(164, 238, 246, 0.9); }
}

/* 英雄技能选目标: 敌方随从 / 敌方英雄条 高亮成可点状态 */
.hp-target { cursor: crosshair !important; }
.foe-bar.hp-target {
  border-color: #a4eef6 !important;
  box-shadow: 0 0 16px rgba(79, 214, 230, 0.55), inset 0 0 24px rgba(79, 214, 230, 0.2);
}
.foe-card.hp-target {
  box-shadow: 0 0 0 3px #4fd6e6, 0 0 18px rgba(79, 214, 230, 0.8) !important;
}

/* === 卡牌基础 === */
.card {
  position: relative;
  width: 100px; height: 140px;
  border-radius: 8px;
  background-color: #22334d;
  background-size: cover; background-position: center;
  cursor: pointer;
  color: #fff;
  box-shadow:
    0 3px 10px rgba(0, 0, 0, 0.6),
    0 0 0 2px rgba(0, 0, 0, 0.4);
  transition: transform 0.18s, box-shadow 0.18s, filter 0.18s;
}
/* 压暗渐变已由 CardFace 组件的 ::after 统一处理, 这里不再叠加 */
.card-inner {
  position: absolute; inset: 0;
  padding: 8px;
  display: flex; flex-direction: column; justify-content: flex-end;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.9);
}
.ability-ico {
  position: absolute;
  top: 4px;
  right: 4px;
  z-index: 6;
  font-size: 13px;
  filter: drop-shadow(0 0 5px rgba(255, 196, 92, 0.85));
  cursor: help;
}
.card-name {
  font-size: 12px;
  font-weight: 600;
  text-align: center;
  padding: 3px 4px;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 3px;
  margin-top: 18px;
  letter-spacing: 0.5px;
}

/* 角标 */
.atk-badge {
  position: absolute; top: -6px; right: -6px;
  width: 24px; height: 24px;
  background: linear-gradient(135deg, #f2ad36, #b8781a);
  border: 2px solid #100d07;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 13px;
  color: #100d07;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.6);
  z-index: 2;
}
.hp-badge {
  position: absolute; top: -6px; left: -6px;
  width: 24px; height: 24px;
  background: linear-gradient(135deg, #3cc24a, #1a6b24);
  border: 2px solid #051a08;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 13px;
  color: #fff;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.6);
  z-index: 2;
}
.hp-badge.hp-low {
  background: linear-gradient(135deg, #e2554f, #6b1a1a);
  animation: heartbeat 1.2s ease-in-out infinite;
}
@keyframes heartbeat {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

/* 稀有度边框发光 */
.r1 { box-shadow: 0 3px 10px rgba(0,0,0,0.6), 0 0 0 2px #7a7a7a; }
.r2 { box-shadow: 0 3px 10px rgba(0,0,0,0.6), 0 0 0 2px #4fd6e6, 0 0 12px rgba(74,144,217,0.4); }
.r3 { box-shadow: 0 3px 10px rgba(0,0,0,0.6), 0 0 0 2px #b04ad9, 0 0 14px rgba(176,74,217,0.5); }
.r4 {
  box-shadow: 0 3px 10px rgba(0,0,0,0.6), 0 0 0 2px #e6a23c, 0 0 18px rgba(230,162,60,0.6);
  animation: r4glow 3s ease-in-out infinite;
}
@keyframes r4glow {
  0%, 100% { filter: brightness(1); }
  50% { filter: brightness(1.15); }
}

/* 手牌专属 —— 费用宝珠 */
.cost-orb {
  position: absolute; top: -8px; left: -8px;
  width: 26px; height: 26px;
  background:
    radial-gradient(circle at 35% 30%, rgba(180,220,255,0.9) 0%, rgba(74,144,217,0.95) 45%, rgba(30,70,130,1) 100%);
  border: 2px solid #051530;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-weight: 800; font-size: 14px;
  color: #fff;
  box-shadow: 0 0 10px rgba(74,144,217,0.7), 0 2px 6px rgba(0,0,0,0.6);
  z-index: 2;
  text-shadow: 0 -1px 1px rgba(0,0,0,0.5);
}

/* 手牌 hover 仰起 */
.hand-area { margin-top: 4px; position: relative; }

/* === 牌库牌堆: 抽牌动画的出发地 === */
.deck-pile {
  position: absolute;
  right: 10px;
  bottom: 26px;
  width: 52px;
  height: 72px;
  cursor: default;
  user-select: none;
}
.dp-card {
  position: absolute;
  inset: 0;
  border-radius: 5px;
  background:
    repeating-linear-gradient(45deg, rgba(240, 165, 42, 0.18) 0 4px, transparent 4px 8px),
    linear-gradient(160deg, #1c3a63, #0c1729 70%);
  border: 1px solid rgba(240, 165, 42, 0.55);
  box-shadow: inset 0 0 10px rgba(0, 0, 0, 0.7), 0 2px 6px rgba(0, 0, 0, 0.6);
}
/* 三张牌叠出"厚度", 每次抽牌依次错开回位 */
.dp-card.c1 { transform: translate(-5px, -4px) rotate(-5deg); }
.dp-card.c2 { transform: translate(-2px, -2px) rotate(-2deg); }
.dp-card.c3 { transform: translate(0, 0); }
.dp-num {
  position: absolute;
  right: -7px;
  bottom: -7px;
  z-index: 2;
  min-width: 20px;
  padding: 0 5px;
  border-radius: 9px;
  background: linear-gradient(180deg, #e6b94f, #a5842f);
  border: 1px solid #6b4f14;
  color: #1a1005;
  font-size: 11px;
  font-weight: 800;
  text-align: center;
  line-height: 16px;
}
.deck-pile.drawing .dp-card.c3 { animation: deckPop 0.5s ease-out; }
.deck-pile.drawing .dp-card.c2 { animation: deckPop 0.5s ease-out 0.06s; }
.deck-pile.drawing .dp-card.c1 { animation: deckPop 0.5s ease-out 0.12s; }
@keyframes deckPop {
  0%   { transform: translate(0, 0) rotate(0); }
  35%  { transform: translate(16px, -16px) rotate(10deg); }
  100% { transform: translate(0, 0) rotate(0); }
}
.deck-pile.drawing { animation: deckShake 0.45s ease-out; }
@keyframes deckShake {
  0%, 100% { transform: translate(0, 0); }
  30% { transform: translate(-3px, 1px); }
  60% { transform: translate(2px, -1px); }
}

/* === 抽到的牌: 从牌库方向飞进手牌 === */
.hand-card.just-drawn { animation: drawIn 0.55s cubic-bezier(0.16, 0.84, 0.3, 1) both; }
@keyframes drawIn {
  0% {
    transform: translate(170px, -34px) rotate(16deg) scale(0.4);
    opacity: 0;
    filter: brightness(2.4) saturate(1.5);
    box-shadow: 0 0 24px rgba(255, 214, 120, 0.95);
  }
  45% { opacity: 1; }
  100% {
    transform: translate(0, 0) rotate(0) scale(1);
    opacity: 1;
    filter: none;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.6);
  }
}
/* 对方抽牌: 牌库数字闪一下金光 */
.deck-num.deck-draw { animation: foeDeckDraw 0.65s ease-out; }
@keyframes foeDeckDraw {
  0%   { color: #fff2c4; text-shadow: 0 0 12px rgba(255, 214, 120, 1); transform: scale(1.35); }
  100% { color: inherit; text-shadow: none; transform: scale(1); }
}

/* 卡牌速览提示: 很小很淡, 不抢卡牌的视觉重心 */
.hand-tip {
  text-align: center;
  font-size: 10px;
  letter-spacing: 1px;
  color: rgba(200, 184, 146, 0.45);
  margin-top: 2px;
  user-select: none;
}
.hand {
  display: flex; gap: -10px; justify-content: center; flex-wrap: nowrap;
  padding: 16px 0 4px;
}
.hand.full .hand-card { margin-right: -16px; }
.hand-card:hover {
  transform: translateY(-30px) scale(1.1);
  z-index: 10;
  box-shadow: 0 8px 30px rgba(255, 196, 92, 0.5);
  filter: brightness(1.15);
}

/* 我方战场卡牌 hover（可操作提示） */
.can-act:hover { filter: brightness(1.1); transform: translateY(-3px); }
/* 本回合已行动/召唤失调 —— 灰显 */
.no-act { filter: grayscale(0.7) brightness(0.65); cursor: default; }
/* 已攻击过(与召唤失调的 💤 灰白区分开): 压暗但不完全褪色 */
.acted { filter: brightness(0.62) saturate(0.85); cursor: default; }

/* 选中的攻击方 —— 浮起 + 发光 */
.card.sel {
  transform: translateY(-12px);
  box-shadow:
    0 6px 20px rgba(0, 0, 0, 0.6),
    0 0 0 3px #ffc45c,
    0 0 22px rgba(255, 196, 92, 0.8) !important;
  animation: selPulse 1s ease-in-out infinite;
}
@keyframes selPulse {
  0%, 100% { box-shadow: 0 6px 20px rgba(0,0,0,0.6), 0 0 0 3px #ffc45c, 0 0 18px rgba(240,204,109,0.6) !important; }
  50%       { box-shadow: 0 6px 20px rgba(0,0,0,0.6), 0 0 0 3px #fff1c4, 0 0 30px rgba(240,204,109,1) !important; }
}
.target-hint {
  position: absolute;
  bottom: -20px; left: 50%; transform: translateX(-50%);
  font-size: 11px; color: #ffc45c; letter-spacing: 2px;
  white-space: nowrap;
  text-shadow: 0 0 6px rgba(240,204,109,0.6);
}

/* 敌方卡牌 hover（可被攻击） */
.mine-target:hover {
  filter: brightness(1.1) saturate(1.2);
  box-shadow:
    0 3px 10px rgba(0,0,0,0.6),
    0 0 0 3px #f56c6c,
    0 0 18px rgba(245, 108, 108, 0.7) !important;
}

/* === 日志 === */
.logs {
  max-width: 640px;
  margin: 16px auto 0;
  padding: 10px 16px;
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(120, 80, 30, 0.5);
  border-radius: 8px;
  min-height: 70px;
  font-size: 12px;
  color: #b3bbc6;
  box-shadow: inset 0 1px 4px rgba(0,0,0,0.6);
}
.log-line { line-height: 1.7; }
.log-line .t { color: #6b7683; margin-right: 4px; }
.log-line.hint { color: #ffc45c; margin-top: 4px; font-weight: 600; }

/* === 结算弹窗 Q 版小人 === */
.result-body { text-align: center; }
.result-mascot {
  width: 150px; height: 150px;
  object-fit: contain;
  filter: drop-shadow(0 8px 16px rgba(255, 196, 92, 0.35));
}
.result-text { font-size: 14px; color: #d9dee5; margin-top: 4px; }
.ak-credit { font-size: 10px; color: #6b5d4b; margin-top: 10px; letter-spacing: 0.5px; }

/* === 攻击动画 === */
.board { position: relative; }
.hero-bar { position: relative; }
.dmg-num {
  display: inline-block;
  font-size: 26px;
  font-weight: 900;
  color: #ff5a5a;
  -webkit-text-stroke: 1px rgba(60, 0, 0, 0.8);
  text-shadow: 0 0 10px rgba(255, 70, 70, 0.8), 0 2px 4px rgba(0, 0, 0, 0.9);
  pointer-events: none;
  animation: dmgFloat 0.8s ease-out forwards;
}
.hero-dmg { position: absolute; top: 6px; right: 26%; z-index: 8; }
.me-bar .hero-dmg { right: auto; left: 38%; }
.hero-hit { animation: heroShake 0.4s ease-out; }

/* 攻击者突进: 我方向上, 敌方向下 */
.card.attacking { z-index: 7; animation: lungeUp 0.5s ease-in-out; }
.card.foe-card.attacking { animation: lungeDown 0.5s ease-in-out; }
.card.attacking { filter: brightness(1.25); }
@keyframes lungeUp {
  0%   { transform: translateY(0) scale(1); }
  35%  { transform: translateY(-66px) scale(1.16); box-shadow: 0 0 30px rgba(255, 210, 110, 1); }
  65%  { transform: translateY(-54px) scale(1.1); }
  100% { transform: translateY(0) scale(1); }
}
@keyframes lungeDown {
  0%   { transform: translateY(0) scale(1); }
  35%  { transform: translateY(66px) scale(1.16); box-shadow: 0 0 30px rgba(255, 90, 90, 1); }
  65%  { transform: translateY(54px) scale(1.1); }
  100% { transform: translateY(0) scale(1); }
}

/* 受击: 抖动 + 红光闪烁 */
.card.hit { animation: hitShake 0.45s ease-out; }
.card.hit::after {
  content: '';
  position: absolute; inset: 0;
  border-radius: 6px;
  background: radial-gradient(circle at 50% 45%, rgba(255, 120, 120, 0.85), rgba(180, 20, 20, 0.25) 60%, transparent 75%);
  animation: hitFlash 0.45s ease-out forwards;
  pointer-events: none;
}
@keyframes hitShake {
  0%, 100% { transform: translateX(0) rotate(0); }
  20% { transform: translateX(-7px) rotate(-2.5deg); }
  40% { transform: translateX(6px) rotate(2deg); }
  60% { transform: translateX(-4px) rotate(-1deg); }
  80% { transform: translateX(3px) rotate(0.5deg); }
}
@keyframes hitFlash {
  0% { opacity: 0; }
  25% { opacity: 1; }
  100% { opacity: 0; }
}
@keyframes heroShake {
  0%, 100% { transform: translate(0, 0); }
  25% { transform: translate(-5px, 1px); }
  50% { transform: translate(5px, -1px); }
  75% { transform: translate(-3px, 0); }
}

/* 伤害数字槽位定位 + 斩击光效 + 死亡爆点 */
.fx-dmg {
  position: absolute;
  top: 28%;
  transform: translateX(-50%);
  z-index: 9;
  pointer-events: none;
}
.slash {
  position: absolute;
  left: 50%; top: 42%;
  width: 112px; height: 4px;
  border-radius: 2px;
  background: linear-gradient(90deg, transparent, #fff 45%, #ffd98a 55%, transparent);
  box-shadow: 0 0 14px rgba(255, 255, 255, 0.9);
  transform: translate(-50%, -50%) rotate(-38deg) scaleX(0);
  animation: slashAnim 0.38s ease-out forwards;
}
@keyframes slashAnim {
  0% { transform: translate(-50%, -50%) rotate(-38deg) scaleX(0); opacity: 0; }
  35% { transform: translate(-50%, -50%) rotate(-38deg) scaleX(1); opacity: 1; }
  100% { transform: translate(-50%, -50%) rotate(-38deg) scaleX(1.15); opacity: 0; }
}
.death-puff {
  position: absolute;
  left: 50%; top: 45%;
  width: 90px; height: 90px;
  margin: -45px 0 0 -45px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 190, 120, 0.85), rgba(226, 85, 79, 0.4) 45%, transparent 70%);
  animation: deathPuff 0.65s ease-out forwards;
}
@keyframes deathPuff {
  0% { transform: scale(0.2); opacity: 0; }
  40% { transform: scale(1); opacity: 1; }
  100% { transform: scale(1.8); opacity: 0; }
}
@keyframes dmgFloat {
  0% { opacity: 0; transform: translateY(10px) scale(0.7); }
  25% { opacity: 1; transform: translateY(-6px) scale(1.3); }
  60% { opacity: 1; transform: translateY(-22px) scale(1.05); }
  100% { opacity: 0; transform: translateY(-48px) scale(1); }
}

/* 大伤害(>=5): 字号更大 + 落地时轻微屏震, 让重击有分量 */
.dmg-num.big { font-size: 38px; color: #ff7a3c; }
.dmg-num.big ~ * { pointer-events: none; }
.board:has(.dmg-num.big) { animation: boardJolt 0.3s ease-out; }
@keyframes boardJolt {
  0%, 100% { transform: translate(0, 0); }
  30% { transform: translate(-3px, 2px); }
  60% { transform: translate(3px, -1px); }
}

/* 治疗飘字: 吸血时攻击方英雄条冒绿色 +N */
.heal-num {
  position: absolute;
  top: 6px;
  z-index: 8;
  display: inline-block;
  font-size: 24px;
  font-weight: 900;
  color: #6ee787;
  -webkit-text-stroke: 1px rgba(0, 50, 20, 0.8);
  text-shadow: 0 0 10px rgba(110, 231, 135, 0.85), 0 2px 4px rgba(0, 0, 0, 0.9);
  pointer-events: none;
  animation: healFloat 0.9s ease-out forwards;
}
.foe-bar .hero-heal { right: 16%; }
.me-bar .hero-heal { right: auto; left: 30%; }
@keyframes healFloat {
  0% { opacity: 0; transform: translateY(8px) scale(0.7); }
  25% { opacity: 1; transform: translateY(-4px) scale(1.25); }
  60% { opacity: 1; transform: translateY(-18px) scale(1.05); }
  100% { opacity: 0; transform: translateY(-44px) scale(1); }
}

/**
 * 圣盾破碎: 白光炸开一圈。
 * 刻意不用 ::before —— 卡面的嘲讽斜纹与传说流光已经占用了它, 抢用会让这些效果在动画期间闪断。
 * 这里改用 box-shadow, 并与受击抖动合并成一条 animation(同一属性只能写一次)。
 */
.card.hit.shield-break { animation: hitShake 0.45s ease-out, shieldBreak 0.5s ease-out; }
@keyframes shieldBreak {
  0% { box-shadow: 0 0 0 0 rgba(255, 255, 255, 0); }
  30% { box-shadow: 0 0 24px 7px rgba(210, 235, 255, 0.95); }
  100% { box-shadow: 0 0 0 0 rgba(255, 255, 255, 0); }
}

/* 随从阵亡: 除爆点外再崩出 6 片碎块 */
.shard {
  position: absolute;
  left: 50%; top: 45%;
  width: 12px; height: 12px;
  margin: -6px 0 0 -6px;
  background: linear-gradient(135deg, #ffd9a0, #d6473c);
  box-shadow: 0 0 6px rgba(255, 160, 90, 0.8);
  animation: shardFly 0.7s ease-out forwards;
  pointer-events: none;
}
.shard.s1 { --sx: -46px; --sy: -34px; }
.shard.s2 { --sx: 38px; --sy: -40px; }
.shard.s3 { --sx: 52px; --sy: 12px; }
.shard.s4 { --sx: -40px; --sy: 26px; }
.shard.s5 { --sx: 6px; --sy: -48px; }
.shard.s6 { --sx: -8px; --sy: 44px; }
/* 剧毒: 碎块变荧绿, 并叠一圈毒雾扩散环 + 骷髅标记 */
.shard.poison {
  background: linear-gradient(135deg, #d6ffb0, #2fa84a);
  box-shadow: 0 0 8px rgba(110, 240, 130, 0.85);
}
.dmg-num.poison {
  color: #6fe07a;
  -webkit-text-stroke: 1px rgba(0, 50, 0, 0.8);
  text-shadow: 0 0 12px rgba(90, 230, 110, 0.9), 0 2px 4px rgba(0, 0, 0, 0.9);
}
.poison-ring {
  position: absolute; left: 50%; top: 50%;
  width: 26px; height: 26px; margin: -13px 0 0 -13px;
  border: 2px solid rgba(120, 240, 140, 0.85);
  border-radius: 50%;
  box-shadow: 0 0 14px rgba(90, 230, 110, 0.8), inset 0 0 10px rgba(90, 230, 110, 0.5);
  pointer-events: none;
  animation: poisonRing 0.75s ease-out forwards;
}
@keyframes poisonRing {
  0% { opacity: 0.9; transform: scale(0.35); }
  70% { opacity: 0.35; transform: scale(2.6); }
  100% { opacity: 0; transform: scale(3.2); }
}
.poison-mark {
  position: absolute; left: 50%; top: -6px;
  transform: translate(-50%, -100%);
  font-size: 20px; color: #8bf59a;
  text-shadow: 0 0 10px rgba(90, 230, 110, 0.95);
  pointer-events: none;
  animation: poisonMark 0.9s ease-out forwards;
}
@keyframes poisonMark {
  0% { opacity: 0; transform: translate(-50%, -60%) scale(0.5); }
  35% { opacity: 1; transform: translate(-50%, -100%) scale(1.15); }
  100% { opacity: 0; transform: translate(-50%, -150%) scale(1); }
}
@keyframes shardFly {
  0% { opacity: 0; transform: translate(0, 0) scale(0.3) rotate(0deg); }
  25% { opacity: 1; }
  100% {
    opacity: 0;
    transform: translate(var(--sx), var(--sy)) scale(1) rotate(220deg);
  }
}

/* 突进高光: 同样避开 ::before, 用 drop-shadow 让冲出去的瞬间更醒目 */
.card.attacking { filter: brightness(1.28) drop-shadow(0 0 12px rgba(255, 214, 120, 0.7)); }

/* ===== 平板竖屏 ===== */
@media (max-width: 980px) {
  /* 屏幕不够宽: 日志从左侧改为底部一条, 战场回到单列 */
  .battle { max-width: 100%; padding: 12px; grid-template-columns: minmax(0, 1fr); }
  .battle > :not(.side-log) { grid-column: 1; }
  .side-log {
    grid-column: 1;
    grid-row: auto;
    order: 98;            /* 排到手牌之后、提示之前 */
    position: static;
  }
  .sl-body { max-height: 148px; }
  .cface.md { width: 96px; height: 128px; }
  .cface.sm { width: 78px; height: 104px; }
  .logs { max-width: 100%; }
}

/* ===== 手机竖屏: 整页压缩成一手可操作 ===== */
@media (max-width: 680px) {
  .battle { padding: 6px 6px 12px; }

  /* 英雄条: 头像变小, 水晶条缩小,  stacks 竖排 */
  .hero-bar { padding: 5px 8px; margin-bottom: 4px; }
  .me-bar { margin-top: 4px; }
  .hero-avatar { width: 38px; height: 38px; border-width: 1px; }
  .hero-name { font-size: 12px; }
  .hero-stat { font-size: 10px; letter-spacing: 0; }
  .hero-info { gap: 6px; }
  .crystals { margin-left: 6px; gap: 2px; }
  .foe-bar .crystals { margin-right: 6px; }
  .crystal { width: 9px; height: 13px; }
  .mana-num { margin-left: 4px; padding: 0 5px; font-size: 9px; letter-spacing: 0; }

  /* 卡面整体缩小(战场 3~4 只一行) */
  .cface.md { width: 76px; height: 102px; }
  .cface.md .cf-mana { min-width: 19px; height: 19px; font-size: 10px; }
  .cface.md .cf-atk, .cface.md .cf-hp { min-width: 21px; height: 21px; font-size: 11px; }
  .cface.md .cf-kw { width: 15px; height: 15px; font-size: 8px; }
  .cface.sm { width: 62px; height: 84px; }
  .cface.sm .cf-mana { min-width: 17px; height: 17px; font-size: 9px; }
  .cface.sm .cf-atk, .cface.sm .cf-hp { min-width: 19px; height: 19px; font-size: 10px; }

  /* 战场: 允许换行 + 高度压缩 */
  .board { min-height: 88px; gap: 5px; padding: 10px 6px; margin: 2px 0; border-radius: 8px; }

  /* 中央控制: 左信息 + 右按钮, 按钮自动换行 */
  .center-bar { flex-direction: column; gap: 8px; padding: 8px 10px; margin: 4px 0; }
  .center-left, .center-right { width: 100%; justify-content: center; flex-wrap: wrap; }
  .center-right { gap: 6px; }
  .btn-battle { padding: 7px 10px; font-size: 11px; letter-spacing: 0; }
  .turn-badge { padding: 3px 9px; font-size: 11px; letter-spacing: 1px; }
  .turn-tag { font-size: 11px; letter-spacing: 1px; }

  /* 手牌: 换行摆放, 一行放不下自动折行 */
  .hand { flex-wrap: wrap; gap: 3px; padding: 10px 0 2px; }
  .hand.full .hand-card { margin-right: -6px; }
  .hand-card:hover { transform: translateY(-14px) scale(1.06); }
  /* 手机竖屏: 牌堆缩到不挡手牌 */
  .deck-pile { width: 34px; height: 48px; right: 2px; bottom: 18px; }
  .dp-num { min-width: 16px; font-size: 9px; line-height: 13px; right: -5px; bottom: -5px; }

  .logs { margin: 10px auto 0; padding: 8px 10px; min-height: 52px; font-size: 11px; }
  .result-mascot { width: 108px; height: 108px; }
}

/* === 攻击光束: 全屏 SVG, 金色=我方出手 / 红色=敌方出手 === */
.atk-beam {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1200;
}
.atk-beam line {
  stroke-width: 4;
  stroke-linecap: round;
  stroke-dasharray: 7 9;
  animation: beamRun 0.72s ease-out forwards;
}
.atk-beam line.me { stroke: #ffd98a; filter: drop-shadow(0 0 6px rgba(255, 214, 120, 0.9)); }
.atk-beam line.foe { stroke: #ff7a5c; filter: drop-shadow(0 0 6px rgba(255, 110, 80, 0.9)); }
.beam-tip { animation: beamFade 0.72s ease-out forwards; }
.beam-tip.me { fill: #ffd98a; filter: drop-shadow(0 0 8px rgba(255, 214, 120, 1)); }
.beam-tip.foe { fill: #ff7a5c; filter: drop-shadow(0 0 8px rgba(255, 110, 80, 1)); }
/* 虚线向目标方向流动 + 整体淡出, 形成一道"飞过去"的轨迹 */
@keyframes beamRun {
  0%   { stroke-dashoffset: 0;    opacity: 0; }
  12%  { opacity: 1; }
  75%  { opacity: 0.95; }
  100% { stroke-dashoffset: -96;  opacity: 0; }
}
@keyframes beamFade {
  0% { opacity: 0; } 18% { opacity: 1; } 75% { opacity: 0.9; } 100% { opacity: 0; }
}

/* 我方英雄挨打: 全屏红晕闪一下, 受击感知拉满 */
.hurt-vignette {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 1100;
  box-shadow: inset 0 0 130px rgba(255, 45, 45, 0.55);
  animation: vignetteFlash 0.6s ease-out forwards;
}
@keyframes vignetteFlash {
  0% { opacity: 0; }
  18% { opacity: 1; }
  100% { opacity: 0; }
}

/* === 进对局前的占位/失败提示 === */
.battle-loading {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 16px;
}
.bl-box {
  width: min(420px, 92vw);
  padding: 30px 26px;
  text-align: center;
  background: rgba(10, 13, 17, 0.85);
  border: 1px solid rgba(120, 80, 30, 0.55);
  border-radius: 10px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.5);
}
.bl-spin {
  width: 34px; height: 34px;
  margin: 0 auto 16px;
  border-radius: 50%;
  border: 3px solid rgba(240, 165, 42, 0.25);
  border-top-color: var(--gold);
  animation: blSpin 0.9s linear infinite;
}
@keyframes blSpin { to { transform: rotate(360deg); } }
.bl-icon { font-size: 30px; margin-bottom: 12px; color: #e6a23c; }
.bl-title { margin: 0 0 8px; font-size: 16px; letter-spacing: 2px; color: var(--gold-light); }
.bl-sub { margin: 0 0 18px; font-size: 12px; line-height: 1.7; color: #9aa4b0; word-break: break-all; }
.bl-actions { display: flex; gap: 12px; justify-content: center; }

/* === 矮屏(笔记本 768p / 录屏窗口): 竖向全面收紧, 对局一屏放得下, 不出滚动条 === */
@media (max-height: 820px) {
  .battle { padding: 8px 14px; gap: 8px; }
  .side-log { top: 8px; }
  .sl-head { padding: 7px 12px; }
  .sl-body { max-height: 62vh; }
  .hero-bar { padding: 5px 14px; margin-bottom: 3px; }
  .hero-avatar { width: 42px; height: 42px; }
  .board { min-height: 104px; padding: 6px 10px; margin: 2px 0; gap: 8px; }
  .board .cface.md { width: 100px; height: 132px; }
  .center-bar { padding: 6px 16px; margin: 3px 0; }
  .hand { padding: 7px 0 1px; }
  .hand .cface.sm { width: 82px; height: 110px; }
  .deck-pile { width: 42px; height: 58px; bottom: 22px; }
  .dp-num { font-size: 10px; line-height: 14px; min-width: 18px; }
  .hand-card:hover { transform: translateY(-16px) scale(1.06); }
  .hand-tip { margin-top: 0; }
  .logs { margin-top: 5px; padding: 6px 14px; }
}
</style>
