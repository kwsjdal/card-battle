<template>
  <div class="stats">
    <div class="toolbar">
      <button class="btn-tb" @click="$router.push('/lobby')">← 返回大厅</button>
      <span class="title">个 人 数 据 中 心</span>
      <button class="btn-tb" @click="load">⟳ 刷新</button>
    </div>

    <div v-if="!data" class="loading">数据加载中…</div>

    <template v-else>
      <!-- ===== 生涯概览 ===== -->
      <section class="panel">
        <div class="panel-head"><span class="ph-title">生 涯 概 览</span><span class="ph-sub">CAREER</span></div>
        <div class="kpi-row">
          <div class="kpi">
            <span class="kpi-num gold">{{ data.user.rating }}</span>
            <span class="kpi-lbl">天梯分</span>
          </div>
          <div class="kpi">
            <span class="kpi-num">{{ data.user.rank ? '#' + data.user.rank : '未上榜' }}</span>
            <span class="kpi-lbl">天梯名次</span>
          </div>
          <div class="kpi">
            <span class="kpi-num">{{ data.user.total }}</span>
            <span class="kpi-lbl">总场次</span>
          </div>
          <div class="kpi">
            <span class="kpi-num win">{{ data.user.win }}</span>
            <span class="kpi-lbl">胜</span>
          </div>
          <div class="kpi">
            <span class="kpi-num lose">{{ data.user.lose }}</span>
            <span class="kpi-lbl">负</span>
          </div>
          <div class="kpi">
            <span class="kpi-num cyan">{{ data.user.winRate }}%</span>
            <span class="kpi-lbl">胜率</span>
          </div>
        </div>

        <!-- 胜率条 -->
        <div class="wl-bar" v-if="data.user.total > 0">
          <div class="wl-in" :style="{ width: data.user.winRate + '%' }"></div>
          <span class="wl-text">{{ data.user.win }} 胜 / {{ data.user.lose }} 负</span>
        </div>
        <div class="wl-tip" v-else>还没有对局记录，先去大厅打一局吧</div>
        <div class="join-date">注册时间：{{ data.user.createTime || '—' }}</div>
      </section>

      <!-- ===== 收藏与消费 ===== -->
      <div class="two-col">
        <section class="panel">
          <div class="panel-head"><span class="ph-title">收 藏 进 度</span><span class="ph-sub">COLLECTION</span></div>
          <div class="coll-top">
            <span class="coll-big">{{ data.collection.ownedCount }}<i>/{{ data.collection.totalCount }}</i></span>
            <span class="coll-pct">{{ data.collection.percent }}%</span>
          </div>
          <div class="coll-bar">
            <div class="coll-bar-in" :style="{ width: data.collection.percent + '%' }"></div>
          </div>
          <ul class="rarity-list">
            <li v-for="r in rarities" :key="r.key">
              <span class="rr-name" :class="'c' + r.key">{{ r.label }}</span>
              <div class="rr-bar">
                <div class="rr-bar-in" :class="'c' + r.key"
                     :style="{ width: r.total ? (r.own / r.total * 100) + '%' : '0%' }"></div>
              </div>
              <span class="rr-num">{{ r.own }}/{{ r.total }}</span>
            </li>
          </ul>
          <div class="mini-row">
            <span>卡组 {{ data.collection.deckCount }} 套</span>
            <span>皮肤 {{ data.collection.skinCount }} 件</span>
          </div>
        </section>

        <section class="panel">
          <div class="panel-head"><span class="ph-title">消 费 与 资 产</span><span class="ph-sub">ECONOMY</span></div>
          <ul class="eco-list">
            <li><span class="el-lbl">累计充值</span><span class="el-val gold">🪙 {{ data.economy.rechargeTotal }}</span></li>
            <li><span class="el-lbl">抽卡累计消耗</span><span class="el-val">🪙 {{ data.economy.gachaCost }}</span></li>
            <li><span class="el-lbl">累计抽卡</span><span class="el-val">{{ data.economy.pulls }} 抽</span></li>
            <li class="sep"></li>
            <li><span class="el-lbl">当前金币</span><span class="el-val gold">🪙 {{ data.economy.balance }}</span></li>
            <li><span class="el-lbl">当前水晶</span><span class="el-val crystal">💎 {{ data.economy.crystal }}</span></li>
          </ul>
          <div class="eco-tip">水晶来自每日签到与任务，金币来自充值，两者都可用于抽卡</div>
        </section>
      </div>

      <!-- ===== 近 14 天趋势 ===== -->
      <section class="panel">
        <div class="panel-head"><span class="ph-title">近 14 天 对 局</span><span class="ph-sub">TREND</span></div>
        <div class="trend">
          <div v-for="d in data.trend" :key="d.date" class="tcol">
            <div class="tbar-wrap">
              <div class="tbar win" :style="{ height: barH(d.wins) }"></div>
              <div class="tbar all" :style="{ height: barH(d.games - d.wins) }"></div>
            </div>
            <span class="tdate">{{ d.date }}</span>
            <span class="tnum">{{ d.games || '' }}</span>
          </div>
        </div>
        <div class="legend">
          <span class="lg"><i class="sw win"></i>胜场</span>
          <span class="lg"><i class="sw all"></i>其余对局</span>
          <span class="lg-tip">柱高 = 当日对局数</span>
        </div>
      </section>

      <!-- ===== 天梯分走势 ===== -->
      <section class="panel">
        <div class="panel-head">
          <span class="ph-title">天 梯 分 走 势</span><span class="ph-sub">RATING</span>
        </div>
        <div v-if="rtPoints.length >= 2" class="rt-wrap">
          <svg class="rt-chart" :viewBox="`0 0 ${RT_W} ${RT_H}`" preserveAspectRatio="none">
            <!-- 横向网格: 标出每个段位门槛, 越过哪条线就升段 -->
            <g class="rt-grid">
              <line v-for="g in rtTierLines" :key="'g'+g.rating"
                    :x1="RT_PAD" :x2="RT_W - RT_PAD" :y1="g.y" :y2="g.y" />
              <text v-for="g in rtTierLines" :key="'gt'+g.rating"
                    :x="RT_PAD - 4" :y="g.y + 3" text-anchor="end">{{ g.name }}</text>
            </g>
            <!-- 面积 -->
            <polygon class="rt-area" :points="rtAreaPoints" />
            <!-- 折线 -->
            <polyline class="rt-line" :points="rtLinePoints" />
            <!-- 数据点: 加分用红、掉分用绿(沿用国内涨红跌绿的读数习惯) -->
            <circle v-for="(p, i) in rtPoints" :key="i"
                    :cx="p.x" :cy="p.y" r="3.2"
                    :class="p.result === 'win' ? 'up' : (p.result === 'lose' ? 'down' : 'flat')">
              <title>{{ p.time }} · {{ p.rating }} 分（{{ p.delta > 0 ? '+' + p.delta : p.delta }}）</title>
            </circle>
          </svg>
          <div class="rt-axis">
            <span>{{ rtPoints[0].time }}</span>
            <span class="rt-cur">当前 {{ rtPoints[rtPoints.length - 1].rating }} 分</span>
            <span>{{ rtPoints[rtPoints.length - 1].time }}</span>
          </div>
          <div class="legend">
            <span class="lg"><i class="sw up"></i>胜 +25</span>
            <span class="lg"><i class="sw down"></i>负 -25</span>
            <span class="lg-tip">共 {{ rtPoints.length }} 局 · 虚线为段位门槛</span>
          </div>
        </div>
        <div v-else class="rt-empty">打满 2 局后这里会画出你的天梯分曲线</div>
      </section>

      <!-- ===== 卡组胜率 ===== -->
      <section class="panel">
        <div class="panel-head">
          <span class="ph-title">卡 组 胜 率</span><span class="ph-sub">DECKS</span>
        </div>
        <div v-if="deckStats.length" class="dk-list">
          <div v-for="d in deckStats" :key="d.deckId" class="dk-row">
            <span class="dk-name" :title="d.name">{{ d.name }}</span>
            <span class="dk-bar">
              <i class="dk-fill" :style="{ width: d.winRate + '%' }"></i>
            </span>
            <span class="dk-rate">{{ d.winRate }}%</span>
            <span class="dk-num">{{ d.win }}胜 {{ d.lose }}负<template v-if="d.draw"> {{ d.draw }}平</template> · 共{{ d.total }}局</span>
          </div>
        </div>
        <div v-else class="rt-empty">本功能上线后打一局, 这里就会按卡组统计胜负</div>
      </section>

      <!-- ===== 最近对局 ===== -->
      <section class="panel">
        <div class="panel-head"><span class="ph-title">最 近 对 局</span><span class="ph-sub">RECENT</span></div>
        <table class="rec-table">
          <thead>
            <tr><th>时 间</th><th>对 手</th><th>结 果</th><th>回 合</th></tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.recent" :key="i" :class="{ win: r.result === 'win', lose: r.result === 'lose' }">
              <td class="t-time">{{ r.time }}</td>
              <td class="t-opp">{{ r.opponent }}</td>
              <td><span class="r-tag" :class="tagCls(r.result)">{{ tagText(r.result) }}</span></td>
              <td class="t-rounds">{{ r.rounds }}</td>
            </tr>
            <tr v-if="data.recent.length === 0">
              <td colspan="4" class="t-empty">暂无对局记录</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import http from '../api/request'
import { TIERS } from '../utils/tier'

const data = ref(null)

/** 各卡组胜率(后端按 match_records.deck_id 聚合, 无数据时给空数组) */
const deckStats = computed(() => (data.value && data.value.deckStats) || [])

const NAMES = { 1: '普通', 2: '稀有', 3: '史诗', 4: '传说' }
const rarities = computed(() => {
  if (!data.value) return []
  const total = data.value.collection.totalByRarity || {}
  const own = data.value.collection.ownByRarity || {}
  return [4, 3, 2, 1].map(k => ({
    key: k,
    label: NAMES[k],
    total: total[k] || total[String(k)] || 0,
    own: own[k] || own[String(k)] || 0
  }))
})

/** 柱状高度: 以最大值为 100% 归一化, 最小 4px 便于看到痕迹 */
function barH(n) {
  const max = Math.max(1, ...(data.value.trend || []).map(d => d.games))
  const px = (n / max) * 92
  return (n > 0 ? Math.max(6, px) : 0) + 'px'
}
function tagCls(r) { return r === 'win' ? 'r1' : r === 'lose' ? 'r-1' : 'r0' }
function tagText(r) { return r === 'win' ? '胜' : r === 'lose' ? '负' : '平' }

/* ===== 天梯分走势折线图(手绘 SVG, 不引图表库) ===== */
const RT_W = 620, RT_H = 200, RT_PAD = 46

const rtPoints = computed(() => {
  const raw = data.value?.ratingTrend || []
  if (raw.length < 2) return []
  const scores = raw.map(d => d.rating)
  // 把所有段位门槛也纳入范围, 让"越过门槛"在图上可见
  const candidates = scores.concat(TIERS.map(t => t.min).filter(m => m > 0))
  let min = Math.min(...candidates)
  let max = Math.max(...candidates)
  if (max === min) { max = min + 50; min = min - 50 }
  const span = max - min
  const innerW = RT_W - RT_PAD - 16
  const innerH = RT_H - 24 - 18
  return raw.map((d, i) => ({
    ...d,
    x: RT_PAD + (raw.length === 1 ? innerW / 2 : (i / (raw.length - 1)) * innerW),
    y: 18 + innerH - ((d.rating - min) / span) * innerH
  }))
})

/** 段位门槛横线: 只画落在当前分数区间内的, 避免一堆线挤在一起 */
const rtTierLines = computed(() => {
  if (!rtPoints.value.length) return []
  const scores = rtPoints.value.map(p => p.rating)
  const lo = Math.min(...scores), hi = Math.max(...scores)
  const pad = (hi - lo) * 0.12 || 25
  const span = (hi + pad) - (lo - pad)
  const innerH = RT_H - 24 - 18
  return TIERS
    .filter(t => t.min > 0 && t.min >= lo - pad && t.min <= hi + pad)
    .map(t => ({
      rating: t.min,
      name: t.name,
      y: 18 + innerH - ((t.min - (lo - pad)) / span) * innerH
    }))
})

const rtLinePoints = computed(() =>
  rtPoints.value.map(p => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' '))

const rtAreaPoints = computed(() => {
  const ps = rtPoints.value
  if (!ps.length) return ''
  const base = RT_H - 18
  return `${ps[0].x.toFixed(1)},${base} `
    + ps.map(p => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ')
    + ` ${ps[ps.length - 1].x.toFixed(1)},${base}`
})

async function load() {
  try { data.value = await http.get('/user/stats') }
  catch (e) { data.value = null }
}
onMounted(load)
</script>

<style scoped>
.stats { max-width: 960px; margin: 0 auto; padding: 20px; color: #d9dee5; }

.toolbar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 22px;
  background: linear-gradient(90deg, rgba(30, 35, 41, 0.94), rgba(12, 15, 18, 0.88));
  border: 1px solid var(--line);
  border-left: 3px solid var(--gold);
  border-radius: 2px;
  margin-bottom: 16px;
}
.toolbar .title {
  font-size: 18px; font-weight: 700;
  color: var(--gold-light); letter-spacing: 6px;
  text-shadow: 0 0 8px rgba(255, 196, 92, 0.35);
}
.btn-tb {
  padding: 6px 14px;
  background: transparent;
  border: 1px solid var(--gold-dark);
  border-radius: 2px;
  color: #d8c9a8; font-size: 13px; cursor: pointer;
  transition: all 0.15s;
}
.btn-tb:hover { background: rgba(240, 165, 42, 0.16); color: var(--gold-light); }

.panel {
  background: linear-gradient(155deg, rgba(30, 35, 41, 0.86), rgba(12, 15, 18, 0.92));
  border: 1px solid var(--line);
  border-radius: 4px;
  padding: 16px 18px 18px;
  margin-bottom: 16px;
  box-shadow: 0 8px 26px rgba(0, 0, 0, 0.45);
}
.panel-head {
  display: flex; align-items: baseline; justify-content: space-between;
  padding-bottom: 10px; margin-bottom: 14px;
  border-bottom: 1px solid rgba(240, 165, 42, 0.22);
}
.ph-title { font-size: 14px; font-weight: 700; color: var(--gold-light); letter-spacing: 5px; }
.ph-sub { font-size: 10px; color: #4c5561; letter-spacing: 3px; }

/* === KPI === */
.kpi-row { display: flex; flex-wrap: wrap; gap: 10px; }
.kpi {
  flex: 1; min-width: 92px;
  display: flex; flex-direction: column; align-items: center;
  padding: 12px 6px;
  background: rgba(10, 13, 16, 0.6);
  border: 1px solid var(--line-soft);
  border-radius: 3px;
}
.kpi-num { font-size: 24px; font-weight: 700; color: #fff; }
.kpi-num.gold { color: var(--gold-light); text-shadow: 0 0 8px rgba(255, 196, 92, 0.4); }
.kpi-num.win { color: #6ee26e; }
.kpi-num.lose { color: #ff6e6e; }
.kpi-num.cyan { color: #6fd8f0; }
.kpi-lbl { font-size: 11px; color: #6b7683; letter-spacing: 2px; margin-top: 4px; }

.wl-bar {
  position: relative; height: 20px; margin-top: 14px;
  background: rgba(226, 85, 79, 0.35);
  border: 1px solid rgba(226, 85, 79, 0.4);
  border-radius: 3px; overflow: hidden;
}
.wl-in {
  height: 100%;
  background: linear-gradient(90deg, #2f8f45, #6ee26e);
  transition: width 0.4s;
}
.wl-text {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; color: #fff; letter-spacing: 2px;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.9);
}
.wl-tip { margin-top: 14px; text-align: center; font-size: 12px; color: #6b7683; letter-spacing: 1px; }
.join-date { margin-top: 8px; text-align: center; font-size: 11px; color: #4c5561; }

/* === 两栏 === */
.two-col { display: flex; gap: 16px; }
.two-col .panel { flex: 1; margin-bottom: 16px; }

/* === 收藏 === */
.coll-top { display: flex; align-items: baseline; justify-content: space-between; }
.coll-big { font-size: 26px; font-weight: 700; color: var(--gold-light); }
.coll-big i { font-size: 13px; color: #6b7683; font-style: normal; }
.coll-pct { font-size: 13px; color: #6fd8f0; font-weight: 600; }
.coll-bar {
  height: 8px; margin: 10px 0 14px;
  background: rgba(0, 0, 0, 0.55); border-radius: 4px; overflow: hidden;
}
.coll-bar-in {
  height: 100%;
  background: linear-gradient(90deg, #b8781a, #ffc45c);
  box-shadow: 0 0 10px rgba(240, 165, 42, 0.6);
  transition: width 0.4s;
}
.rarity-list { list-style: none; margin: 0; padding: 0; }
.rarity-list li { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.rr-name { width: 42px; font-size: 12px; }
.rr-name.c4 { color: #e6a23c; }
.rr-name.c3 { color: #b04ad9; }
.rr-name.c2 { color: #4fd6e6; }
.rr-name.c1 { color: #8d97a3; }
.rr-bar { flex: 1; height: 6px; background: rgba(0, 0, 0, 0.5); border-radius: 3px; overflow: hidden; }
.rr-bar-in { height: 100%; transition: width 0.4s; }
.rr-bar-in.c4 { background: #e6a23c; }
.rr-bar-in.c3 { background: #b04ad9; }
.rr-bar-in.c2 { background: #4fd6e6; }
.rr-bar-in.c1 { background: #7a828c; }
.rr-num { width: 52px; text-align: right; font-size: 11px; color: #6b7683; }
.mini-row {
  display: flex; gap: 18px; margin-top: 12px; padding-top: 10px;
  border-top: 1px dashed rgba(240, 165, 42, 0.18);
  font-size: 12px; color: #8d97a3;
}

/* === 消费 === */
.eco-list { list-style: none; margin: 0; padding: 0; }
.eco-list li { display: flex; justify-content: space-between; align-items: baseline; padding: 7px 0; font-size: 13px; }
.eco-list li.sep { border-top: 1px dashed rgba(240, 165, 42, 0.18); margin-top: 6px; padding-top: 12px; }
.el-lbl { color: #8d97a3; }
.el-val { font-weight: 700; color: #d9dee5; }
.el-val.gold { color: #ffb545; }
.el-val.crystal { color: #8fd7ff; }
.eco-tip { margin-top: 12px; font-size: 11px; color: #4c5561; line-height: 1.8; }

/* === 趋势图 === */
.trend { display: flex; align-items: flex-end; gap: 4px; height: 120px; }
.tcol { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; height: 100%; }
.tbar-wrap { display: flex; flex-direction: column; justify-content: flex-end; height: 100px; }
.tbar { width: 14px; transition: height 0.4s; }
.tbar.win { background: linear-gradient(180deg, #6ee26e, #2f8f45); border-radius: 2px 2px 0 0; }
.tbar.all { background: linear-gradient(180deg, #7a828c, #3b424b); border-radius: 0 0 2px 2px; }
.tdate { margin-top: 6px; font-size: 9px; color: #6b7683; }
.tnum { font-size: 10px; color: #8d97a3; }
.legend { display: flex; gap: 16px; align-items: center; margin-top: 10px; font-size: 11px; color: #6b7683; }
.lg { display: flex; align-items: center; gap: 5px; }
.sw { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }
.sw.win { background: #6ee26e; }
.sw.all { background: #7a828c; }
.sw.up { background: #ff5a5a; }    /* 加分 */
.sw.down { background: #4fd66b; }  /* 掉分 */
.lg-tip { margin-left: auto; color: #4c5561; }

/* === 天梯分走势 === */
.rt-wrap { padding: 4px 2px 0; }
.rt-chart {
  width: 100%;
  height: 200px;
  display: block;
  overflow: visible;   /* 段位标签在左padding里, 别被裁掉 */
}
.rt-grid line {
  stroke: rgba(240, 165, 42, 0.22);
  stroke-width: 1;
  stroke-dasharray: 4 4;
}
.rt-grid text {
  fill: #6b7683;
  font-size: 9px;
  letter-spacing: 0.5px;
}
.rt-area { fill: rgba(240, 165, 42, 0.13); stroke: none; }
.rt-line {
  fill: none;
  stroke: var(--gold-light);
  stroke-width: 2;
  stroke-linejoin: round;
  stroke-linecap: round;
  filter: drop-shadow(0 0 4px rgba(240, 165, 42, 0.45));
}
/* 加分红、掉分绿(国内习惯); 平局灰 */
.rt-chart circle { stroke: #0b0e11; stroke-width: 1.2; }
.rt-chart circle.up { fill: #ff5a5a; }
.rt-chart circle.down { fill: #4fd66b; }
.rt-chart circle.flat { fill: #8b949e; }
.rt-axis {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 10px;
  color: #5c6672;
}
.rt-axis .rt-cur { color: var(--gold-light); font-weight: 600; }
.rt-empty {
  padding: 26px 0;
  text-align: center;
  font-size: 12px;
  color: #5c6672;
}

/* ===== 卡组胜率 ===== */
.dk-list { display: flex; flex-direction: column; gap: 10px; padding: 4px 2px 2px; }
.dk-row {
  display: grid;
  grid-template-columns: minmax(70px, 110px) minmax(60px, 1fr) 44px minmax(96px, auto);
  align-items: center;
  gap: 10px;
  font-size: 12px;
}
.dk-name {
  color: #d9cba8; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.dk-bar {
  height: 8px; border-radius: 4px; overflow: hidden;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(212, 168, 71, 0.25);
}
.dk-fill {
  display: block; height: 100%;
  background: linear-gradient(90deg, #d4473c, #f0a04b);
  box-shadow: 0 0 8px rgba(240, 140, 70, 0.55);
  transition: width .4s ease;
}
.dk-rate { color: #f0cc6d; font-weight: 700; text-align: right; font-variant-numeric: tabular-nums; }
.dk-num { color: #7d858f; font-size: 11px; text-align: right; }

@media (max-width: 680px) {
  .dk-row { grid-template-columns: 72px 1fr 40px; row-gap: 2px; }
  .dk-num { grid-column: 1 / -1; text-align: left; }
}

/* === 最近对局 === */
.rec-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.rec-table thead tr { background: rgba(0, 0, 0, 0.35); }
.rec-table th {
  padding: 10px 12px; text-align: left;
  font-size: 12px; letter-spacing: 3px; font-weight: 600;
  color: var(--gold-light);
  border-bottom: 1px solid rgba(240, 165, 42, 0.22);
}
.rec-table tbody tr { border-bottom: 1px solid rgba(51, 58, 68, 0.5); }
.rec-table tbody tr.win { background: rgba(110, 226, 110, 0.07); }
.rec-table tbody tr.lose { background: rgba(255, 110, 110, 0.07); }
.rec-table td { padding: 9px 12px; color: #b3bbc6; }
.t-time { font-size: 12px; color: #6b7683; }
.t-opp { color: #d9dee5; }
.t-rounds { text-align: center; }
.t-empty { text-align: center; padding: 24px; color: #4c5561; letter-spacing: 2px; }
.r-tag {
  display: inline-block; padding: 2px 12px; border-radius: 3px;
  font-size: 12px; font-weight: 600; letter-spacing: 2px;
}
.r-tag.r1 { background: rgba(110, 226, 110, 0.18); color: #8eff8e; border: 1px solid rgba(110, 226, 110, 0.45); }
.r-tag.r-1 { background: rgba(255, 110, 110, 0.18); color: #ff8e8e; border: 1px solid rgba(255, 110, 110, 0.45); }
.r-tag.r0 { background: rgba(180, 180, 180, 0.15); color: #d0d0d0; border: 1px solid rgba(180, 180, 180, 0.35); }

.loading { text-align: center; padding: 60px 0; color: #6b7683; letter-spacing: 3px; }

/* ===== 手机 ===== */
@media (max-width: 680px) {
  .stats { padding: 10px 12px 24px; }
  .toolbar { padding: 10px 12px; gap: 8px; }
  .toolbar .title { font-size: 15px; letter-spacing: 3px; }
  .btn-tb { padding: 5px 10px; font-size: 12px; }
  .panel { padding: 12px 12px 14px; }
  .ph-title { font-size: 13px; letter-spacing: 3px; }
  .kpi { min-width: calc(33.33% - 8px); padding: 10px 4px; }
  .kpi-num { font-size: 20px; }
  .kpi-lbl { font-size: 10px; letter-spacing: 1px; }
  .two-col { flex-direction: column; gap: 0; }
  .two-col .panel { margin-bottom: 16px; }
  .trend { gap: 2px; height: 104px; }
  .tbar-wrap { height: 84px; }
  .tbar { width: 9px; }
  .tdate { font-size: 8px; }
  .rec-table { font-size: 12px; }
  .rec-table th, .rec-table td { padding: 8px 6px; }
  .rec-table th { letter-spacing: 1px; }
}
</style>
