<template>
  <div class="lobby">
    <!-- 顶部导航 -->
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark">◆</span>
        <span class="brand-name">罗德岛酒馆</span>
        <span class="brand-sub">RHODES TAVERN</span>
      </div>
      <div class="user-chip">
        <img class="avatar-chip" :src="user.avatar || '/cards/1.jpg'"
             title="个人资料" @click="openProfile" alt="头像" />
        <span class="nick">{{ user.nickname }}</span>
        <span class="stat tier-stat">
          <TierBadge :rating="user.rating" /> 天梯 {{ user.rating }}
        </span>
        <span class="stat">{{ user.winCount }}胜 / {{ user.loseCount }}负</span>
        <span class="stat coin">🪙 {{ wallet.balance }}</span>
        <span class="stat crystal">💎 {{ wallet.crystal || 0 }}</span>
        <button class="btn-admin tutor" @click="startTutorial">新手教程</button>
        <button v-if="user.role === 'admin'" class="btn-admin" @click="$router.push('/admin')">管理后台</button>
        <button class="btn-sfx" :class="{ off: !sfxOn }" @click="toggleSfx"
                :title="sfxOn ? '点击关闭音效' : '点击开启音效'">
          {{ sfxOn ? '♪ 音效开' : '♪ 已静音' }}
        </button>
        <button class="btn-sfx bgm" :class="{ off: !bgmOn }" @click="toggleBgm"
                :title="bgmOn ? '点击关闭背景音乐' : '点击开启背景音乐'">
          {{ bgmOn ? '♫ 音乐开' : '♫ 音乐关' }}
        </button>
        <button class="btn-recharge" @click="rechargeOpen = true">充 值</button>
      </div>
    </header>

    <div class="main">
      <!-- 中央英雄区 -->
      <section class="hero">
        <div class="hero-title">
          <span class="hero-line"></span>
          <span class="hero-deco">◆</span>
          <h1>罗 德 岛 酒 馆</h1>
          <span class="hero-deco">◆</span>
          <span class="hero-line"></span>
        </div>
        <p class="hero-sub">推开门，找个对手，打一局</p>

        <button v-if="!matching" class="btn-match" @click="join">
          <span class="match-icon">⚔</span>
          <span class="match-main">开 始 匹 配</span>
          <span class="match-sub">FIND A MATCH</span>
        </button>
        <button v-else class="btn-match matching" @click="leave">
          <span class="match-icon">⏳</span>
          <span class="match-main">匹 配 中 …</span>
          <span class="match-sub">点击取消</span>
        </button>
        <div v-if="matching" class="matching-mascot">
          <img src="/ak/fly.gif" class="mascot-img" alt="匹配中" />
          <span class="mascot-text">纸飞机正在为你寻找对手 …</span>
          <span class="mascot-sub">
            已等待 {{ matchStatus.waited }}s · 队列中 {{ matchStatus.waiting }} 人
            <span v-if="matchStatus.waited >= 20" class="mascot-hint">（已放宽分差限制，继续等待会匹配到任何人）</span>
          </span>
          <p class="mascot-note">提示：请用<b>两个不同账号</b>分别在电脑和手机上匹配，
            同一个账号在两台设备登录只会算一个人，永远撮合不上。</p>
        </div>

        <!-- 快捷入口: 全部使用明日方舟开源 Q 版形象做图标, 不用 emoji -->
        <div class="quick-actions">
          <button class="btn-quick bot" @click="botOpen = true">
            <span class="q-icon"><img src="/ak/pepe.gif" alt="" /></span>
            <span class="q-label">人机对战</span>
          </button>
          <button class="btn-quick shop" @click="$router.push('/shop')">
            <span class="q-icon"><img src="/ak/explore.png" alt="" /></span>
            <span class="q-label">卡牌商店</span>
          </button>
          <button class="btn-quick bonus" @click="openBonus">
            <span class="q-icon"><img src="/ak/photo.gif" alt="" /></span>
            <span class="q-label">每日福利</span>
            <span class="q-dot" v-if="bonusDot"></span>
          </button>
          <button class="btn-quick" @click="$router.push('/deck')">
            <span class="q-icon"><img src="/ak/sit.png" alt="" /></span>
            <span class="q-label">卡组编辑</span>
          </button>
          <button class="btn-quick" @click="$router.push('/records')">
            <span class="q-icon"><img src="/ak/read.gif" alt="" /></span>
            <span class="q-label">对局记录</span>
          </button>
          <button class="btn-quick rank" @click="openRank">
            <span class="q-icon">
              <svg class="trophy-svg" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M7 4h10v5a5 5 0 0 1-10 0V4z" />
                <path d="M7 6H4.6A2.6 2.6 0 0 0 7 8.6" />
                <path d="M17 6h2.4A2.6 2.6 0 0 1 17 8.6" />
                <path d="M12 14v3" />
                <path d="M9.5 20h5" />
                <path d="M10 17h4v3h-4z" />
              </svg>
            </span>
            <span class="q-label">天梯排行</span>
          </button>
          <button class="btn-quick data" @click="$router.push('/stats')">
            <span class="q-icon"><img src="/ak/photo.gif" alt="" /></span>
            <span class="q-label">数据中心</span>
          </button>
          <button class="btn-quick" @click="$router.push('/spectate')">
            <span class="q-icon"><img src="/ak/explore.png" alt="" /></span>
            <span class="q-label">观战大厅</span>
          </button>
          <button class="btn-quick danger" @click="logout">
            <span class="q-icon"><img src="/ak/fly.gif" alt="" /></span>
            <span class="q-label">退出登录</span>
          </button>
        </div>

        <div class="announce">
          回合上限 120 · 胜负天梯分 ±25 · 重复卡自动回收 20 金币<br />
          金币：充值获得，可买皮肤/抽卡 · 水晶：每日签到与任务获得，抽卡每抽比金币贵 100<br />
          <span class="ak-credit">
            干员半身像素材来自开源仓库 yuanyan3060/ArknightsGameResource ·
            Q 版形象与干员立绘版权归《明日方舟》(© Hypergryph / 上海鹰角网络科技有限公司) · 仅用于学习演示
          </span>
        </div>
      </section>

      <!-- 右侧天梯: 完整榜单收进奖杯按钮, 这里只放"我的战绩 + 前三名" -->
      <aside class="rank-col">
        <div class="rank-head">
          <span>🏆 天梯排行</span>
          <span class="rank-refresh">每 10 秒刷新</span>
        </div>

        <!-- 我的战绩 -->
        <div class="my-rank">
          <div class="mr-tier">
            <TierBadge :rating="myRank.rating || user.rating || 0" />
            <span class="mr-tier-next" v-if="myTier.next">
              距 {{ myTier.next }} 还差 {{ myTier.toNext }} 分
            </span>
            <span class="mr-tier-next" v-else>已达最高段位</span>
          </div>
          <div class="mr-row">
            <div class="mr-cell">
              <span class="mr-num">{{ myRank.rank > 0 ? myRank.rank : '—' }}</span>
              <span class="mr-label">我的排名</span>
            </div>
            <div class="mr-cell">
              <span class="mr-num gold">{{ myRank.rating || user.rating || 0 }}</span>
              <span class="mr-label">天梯分</span>
            </div>
            <div class="mr-cell">
              <span class="mr-num">{{ winRate }}</span>
              <span class="mr-label">胜率</span>
            </div>
          </div>
          <div class="mr-foot">{{ user.winCount || 0 }} 胜 / {{ user.loseCount || 0 }} 负</div>
        </div>

        <!-- 前三名预览 -->
        <ul class="rank-list rank-list-top3">
          <li v-for="(r, i) in rank.slice(0, 3)" :key="r.userId" class="rank-item"
              :class="{ me: r.userId === user.id }">
            <span class="rk" :class="'rk-' + (i+1)">{{ ['🥇','🥈','🥉'][i] }}</span>
            <img class="r-avatar" :src="r.avatar || '/cards/1.jpg'" alt="" />
            <span class="rnick">{{ r.nickname }}</span>
            <TierBadge :rating="r.rating" :show-name="false" />
            <span class="rscore">{{ r.rating }}</span>
          </li>
          <li v-if="rank.length === 0" class="rank-empty">虚位以待，快来上榜</li>
        </ul>

        <!-- 奖杯按钮: 点进去看完整榜单 -->
        <div class="trophy-wrap">
          <button class="btn-trophy" @click="openRank">
            <svg class="trophy-svg big" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M7 4h10v5a5 5 0 0 1-10 0V4z" />
              <path d="M7 6H4.6A2.6 2.6 0 0 0 7 8.6" />
              <path d="M17 6h2.4A2.6 2.6 0 0 1 17 8.6" />
              <path d="M12 14v3" />
              <path d="M9.5 20h5" />
              <path d="M10 17h4v3h-4z" />
            </svg>
            <span class="t-main">查看完整榜单</span>
            <span class="t-sub">TOP 50 · LEADERBOARD</span>
          </button>
        </div>
      </aside>
    </div>

    <!-- 人机难度选择 -->
    <el-dialog v-model="botOpen" title="选择对手强度" width="400px" center>
      <div class="bot-levels">
        <button v-for="lv in BOT_LEVELS" :key="lv.key"
                class="bot-lv" :class="['lv-' + lv.key, { active: botLevel === lv.key }]"
                @click="botLevel = lv.key">
          <span class="bl-name">{{ lv.name }}</span>
          <span class="bl-desc">{{ lv.desc }}</span>
        </button>
      </div>
      <template #footer>
        <button class="btn-main" :disabled="botStarting" @click="joinBot">
          {{ botStarting ? '开 局 中 …' : '开 始 对 战' }}
        </button>
      </template>
    </el-dialog>

    <!-- 天梯排行榜: 由主页面奖杯按钮打开 -->
    <el-dialog v-model="rankOpen" width="560px" center class="rank-dialog" @open="loadRank">
      <template #header>
        <div class="lb-title">
          <svg class="trophy-svg" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M7 4h10v5a5 5 0 0 1-10 0V4z" />
            <path d="M7 6H4.6A2.6 2.6 0 0 0 7 8.6" />
            <path d="M17 6h2.4A2.6 2.6 0 0 1 17 8.6" />
            <path d="M12 14v3" />
            <path d="M9.5 20h5" />
            <path d="M10 17h4v3h-4z" />
          </svg>
          <span>天梯排行榜</span>
          <span class="lb-count">TOP {{ rank.length }}</span>
        </div>
      </template>

      <!-- 领奖台: 银 / 金 / 铜 -->
      <div class="podium" v-if="rank.length >= 3">
        <div class="pd pd-2">
          <img class="pd-av" :src="rank[1].avatar || '/cards/1.jpg'" alt="" />
          <span class="pd-medal">🥈</span>
          <span class="pd-nick">{{ rank[1].nickname }}</span>
          <TierBadge :rating="rank[1].rating" />
          <span class="pd-score">{{ rank[1].rating }}</span>
        </div>
        <div class="pd pd-1">
          <img class="pd-av" :src="rank[0].avatar || '/cards/1.jpg'" alt="" />
          <span class="pd-medal">🥇</span>
          <span class="pd-nick">{{ rank[0].nickname }}</span>
          <TierBadge :rating="rank[0].rating" />
          <span class="pd-score">{{ rank[0].rating }}</span>
        </div>
        <div class="pd pd-3">
          <img class="pd-av" :src="rank[2].avatar || '/cards/1.jpg'" alt="" />
          <span class="pd-medal">🥉</span>
          <span class="pd-nick">{{ rank[2].nickname }}</span>
          <TierBadge :rating="rank[2].rating" />
          <span class="pd-score">{{ rank[2].rating }}</span>
        </div>
      </div>

      <!-- 完整榜单 -->
      <ul class="lb-list">
        <li v-for="(r, i) in rank" :key="r.userId" class="lb-item"
            :class="{ me: r.userId === user.id, top3: i < 3 }">
          <span class="lb-rk" :class="'rk-' + (i+1)">{{ ['🥇','🥈','🥉'][i] || (i + 1) }}</span>
          <img class="r-avatar" :src="r.avatar || '/cards/1.jpg'" alt="" />
          <span class="rnick">{{ r.nickname }}</span>
          <TierBadge :rating="r.rating" :show-name="false" />
          <span class="rscore">{{ r.rating }}</span>
          <span class="rwl">{{ r.winCount }}/{{ r.loseCount }}</span>
        </li>
        <li v-if="rank.length === 0" class="rank-empty">虚位以待，快来上榜</li>
      </ul>

      <!-- 我的排名: 不在榜内也要能看到自己 -->
      <div class="lb-me" v-if="myRank.rank > 0">
        <span class="lb-me-tag">我</span>
        <span class="lb-me-txt">
          第 <b>{{ myRank.rank }}</b> 名 · 天梯 <b>{{ myRank.rating }}</b> · 胜率 {{ winRate }}
        </span>
      </div>
      <div class="lb-tip">天梯分 ±25 / 局，榜单每 10 秒自动刷新</div>
    </el-dialog>

    <!-- 个人资料 / 头像自选 -->
    <el-dialog v-model="profileOpen" title="👤 个人资料" width="420px" center>
      <div class="profile">
        <img class="profile-avatar" :src="user.avatar || '/cards/1.jpg'" alt="头像" />
        <div class="profile-info">
          <div class="p-nick">{{ user.nickname }}</div>
          <div class="p-line">账号：{{ user.username }}</div>
          <div class="p-line">手机号：{{ user.phone || '未绑定' }}</div>
          <div class="p-line">天梯 {{ user.rating }} · {{ user.winCount }} 胜 / {{ user.loseCount }} 负</div>
          <div class="p-line coin">🪙 {{ wallet.balance }} 金币</div>
        </div>
      </div>
      <div class="avatar-title">选择头像</div>
      <div class="avatar-grid">
        <img v-for="a in avatars" :key="a" :src="a" class="avatar-opt"
             :class="{ on: user.avatar === a }" @click="chooseAvatar(a)" alt="预设头像" />
      </div>
      <div class="avatar-credit">Q 版形象与干员立绘素材来自《明日方舟》(© Hypergryph) · 仅用于学习演示</div>
    </el-dialog>

    <!-- 每日福利: 签到 + 任务 -->
    <el-dialog v-model="bonusOpen" title="🎁 每日福利" width="460px" center>
      <div class="bonus-head">
        <div class="bh-crystal">💎 {{ bonus.crystal || 0 }} <span class="bh-unit">水晶</span></div>
        <div class="bh-streak" v-if="bonus.streak > 0">已连续签到 {{ bonus.streak }} 天</div>
      </div>

      <button class="sign-btn" :class="{ done: bonus.signedToday }" :disabled="bonus.signedToday" @click="doSign">
        <span class="sign-main">{{ bonus.signedToday ? '今日已签到' : '立即签到' }}</span>
        <span class="sign-sub">{{ bonus.signedToday ? '明天再来领水晶吧' : `+${bonus.signReward || 50} 水晶` }}</span>
      </button>

      <div class="task-title">每 日 任 务</div>
      <div class="task-list">
        <div v-for="t in bonus.tasks || []" :key="t.key" class="task-item">
          <div class="t-info">
            <span class="t-desc">{{ t.desc }}</span>
            <div class="t-progress">
              <div class="t-bar"><div class="t-bar-in" :style="{ width: (t.progress / t.target * 100) + '%' }"></div></div>
              <span class="t-num">{{ t.progress }}/{{ t.target }}</span>
            </div>
          </div>
          <button class="t-claim" :class="{ done: t.claimed, ready: !t.claimed && t.progress >= t.target }"
                  :disabled="t.claimed || t.progress < t.target" @click="doClaim(t)">
            {{ t.claimed ? '已领取' : (t.progress >= t.target ? `领 ${t.reward}💎` : `${t.reward}💎`) }}
          </button>
        </div>
      </div>
      <div class="bonus-tip">水晶可用于商店抽卡（每抽比金币贵 100），金币可充值用于买皮肤与抽卡</div>
    </el-dialog>

    <!-- 卡组检测: 没有合规卡组时引导处理 -->
    <el-dialog v-model="deckOpen" title="🃏 卡组检测" width="420px" center :close-on-click-modal="false">
      <div class="dlg-body">
        <p class="deck-warn">你的卡组还不完整，无法开始匹配</p>
        <p class="deck-detail">{{ deckMsg }}</p>
        <p class="deck-tip">一副合规卡组需要：<b>恰好 30 张</b>，且全部是你已经解锁的卡牌。</p>
      </div>
      <template #footer>
        <button class="btn-plain-sm" @click="deckOpen = false">取 消</button>
        <button class="btn-main-sm" :disabled="deckGenerating" @click="autoDeck">
          {{ deckGenerating ? '生成中…' : '一键生成卡组' }}
        </button>
        <button class="btn-main-sm" @click="$router.push('/deck')">去卡组编辑</button>
      </template>
    </el-dialog>

    <!-- 新手教程(新号可选, 也可跳过) -->
    <el-dialog v-model="tutorAskOpen" title="🎉 欢迎来到罗德岛酒馆" width="420px" center
               :close-on-click-modal="false" :show-close="false">
      <div class="dlg-body">
        <p class="deck-detail">首次进入，要不要先看看 30 秒的新手教程？</p>
        <p class="deck-tip">随时可以在大厅右上角「新手教程」重新查看。</p>
      </div>
      <template #footer>
        <button class="btn-plain-sm" @click="skipTutorial">跳过，直接开始</button>
        <button class="btn-main-sm" @click="startTutorial">进入新手教程</button>
      </template>
    </el-dialog>

    <el-dialog v-model="tutorOpen" :title="'📖 新手教程 ' + (tutorStep + 1) + ' / ' + tutorSteps.length"
               width="480px" center :close-on-click-modal="false">
      <div class="tutor-body">
        <div class="tutor-icon">{{ tutorSteps[tutorStep].icon }}</div>
        <div class="tutor-title">{{ tutorSteps[tutorStep].title }}</div>
        <p class="tutor-text">{{ tutorSteps[tutorStep].text }}</p>
        <div class="tutor-dots">
          <span v-for="(s, i) in tutorSteps" :key="i" class="dot" :class="{ on: i === tutorStep }"></span>
        </div>
      </div>
      <template #footer>
        <button class="btn-plain-sm" @click="skipTutorial">跳过教程</button>
        <button v-if="tutorStep > 0" class="btn-plain-sm" @click="tutorStep--">上一步</button>
        <button class="btn-main-sm" @click="nextTutor">
          {{ tutorStep === tutorSteps.length - 1 ? '开始游戏' : '下一步' }}
        </button>
      </template>
    </el-dialog>

    <!-- 模拟充值弹窗 -->
    <el-dialog v-model="rechargeOpen" title="💰 金币充值（模拟支付）" width="360px" center>
      <div class="recharge-body">
        <div class="recharge-tip">演示环境不产生真实扣费</div>
        <div class="recharge-grid">
          <button v-for="t in tiers" :key="t.coins"
                  class="recharge-opt" :class="{ on: rechargeAmt === t.coins }"
                  @click="rechargeAmt = t.coins">
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http, { idemKey } from '../api/request'
import { connect, removeListener } from '../api/ws'
import sfx from '../utils/sound'
import { rankTier } from '../utils/tier'
import TierBadge from '../components/TierBadge.vue'

const router = useRouter()
const sfxOn = ref(sfx.isOn())
function toggleSfx() {
  const on = sfx.toggle()
  sfxOn.value = on
  if (on) sfx.play('ding')
}
const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))
const matching = ref(false)
const rank = ref([])
const wallet = ref({ balance: 0 })
const rechargeOpen = ref(false)
const profileOpen = ref(false)
const avatars = ref([])
const rechargeAmt = ref(300)
const recharging = ref(false)
const bonusOpen = ref(false)
const bonus = ref({})
const bonusDot = ref(false)
const rankOpen = ref(false)          // 完整榜单弹窗(由奖杯按钮打开)
const myRank = ref({ rank: -1, rating: 0 })
const tiers = [
  { coins: 60, label: '¥6 · 金币' },
  { coins: 300, label: '¥30 · 金币' },
  { coins: 980, label: '¥98 · 超值礼包' },
  { coins: 3280, label: '¥328 · 豪华礼包' }
]
let timer = null

async function loadRank() {
  rank.value = await http.get('/rank/top')
}

/** 我的天梯排名(ZSet 里查名次, 不在榜内也能看到自己是第几名) */
async function loadMyRank() {
  try {
    // userId 由服务端从 JWT 取, 不传参(否则可以枚举他人排名)
    const r = await http.get('/rank/myRank')
    myRank.value = r || { rank: -1, rating: 0 }
  } catch { myRank.value = { rank: -1, rating: 0 } }
}

/** 打开完整榜单: 顺手把榜单与我的名次都刷一遍 */
function openRank() {
  rankOpen.value = true
  loadRank()
  loadMyRank()
}

/** 我的段位(距下一段还差多少分) */
const myTier = computed(() => rankTier(myRank.value.rating || user.value.rating || 0))

/** 胜率(无对局时显示 —, 避免出现 0% 的误导) */
const winRate = computed(() => {
  const w = user.value.winCount || 0
  const l = user.value.loseCount || 0
  if (w + l === 0) return '—'
  return Math.round((w / (w + l)) * 100) + '%'
})

async function loadWallet() {
  const w = await http.get('/wallet/me')
  wallet.value = w
  // 刷新本地用户(role/balance 可能被后台改动)
  const u = { ...user.value, role: w.role, balance: w.balance, crystal: w.crystal }
  user.value = u
  localStorage.setItem('user', JSON.stringify(u))
}

async function loadBonus() {
  try {
    bonus.value = await http.get('/bonus/info')
    // 未签到或有待领取任务 -> 小红点
    bonusDot.value = !bonus.value.signedToday ||
      (bonus.value.tasks || []).some(t => !t.claimed && t.progress >= t.target)
  } catch { /* 忽略, 未登录时不显示 */ }
}

function openBonus() { bonusOpen.value = true; loadBonus() }

async function doSign() {
  const r = await http.post('/bonus/sign')
  wallet.value.crystal = r.crystal
  localStorage.setItem('user', JSON.stringify({ ...user.value, crystal: r.crystal }))
  ElMessage.success(`签到成功, +${r.reward} 水晶 (连续 ${r.streak} 天)`)
  await loadBonus()
}

async function doClaim(t) {
  const r = await http.post('/bonus/tasks/claim', { key: t.key })
  wallet.value.crystal = r.crystal
  localStorage.setItem('user', JSON.stringify({ ...user.value, crystal: r.crystal }))
  ElMessage.success(`任务完成, +${r.reward} 水晶`)
  await loadBonus()
}

async function openProfile() {
  if (avatars.value.length === 0) avatars.value = await http.get('/user/avatars')
  profileOpen.value = true
}
async function chooseAvatar(a) {
  await http.post('/user/avatar', { avatar: a })
  user.value = { ...user.value, avatar: a }
  localStorage.setItem('user', JSON.stringify(user.value))
}

/* ===== 卡组检测 ===== */
const deckOpen = ref(false)
const deckMsg = ref('')
const deckGenerating = ref(false)
/** 开始匹配前先本地检查一次, 后端 /match/join 也会再校验一次 */
async function checkDeck() {
  const decks = await http.get('/decks').catch(() => [])
  const ok = (decks || []).some(d => {
    try { return (JSON.parse(d.cardIds) || []).length === 30 } catch { return false }
  })
  if (ok) return true
  deckMsg.value = decks && decks.length
    ? `当前有 ${decks.length} 套卡组，但没有一套是合规的 30 张（可能因卡牌被回收导致张数不足）。`
    : '还没有任何卡组。'
  deckOpen.value = true
  return false
}
async function autoDeck() {
  deckGenerating.value = true
  try {
    await http.post('/decks/auto')
    deckOpen.value = false
    ElMessage.success('已生成 30 张合规卡组，可以开始匹配了')
  } finally { deckGenerating.value = false }
}

const matchStatus = ref({ waiting: 0, waited: 0, inQueue: false })
let matchTimer = null
async function join() {
  if (!await checkDeck()) return
  await http.post('/match/join')
  matching.value = true
  await refreshMatchStatus()
  matchTimer = setInterval(refreshMatchStatus, 3000)
}
async function refreshMatchStatus() {
  matchStatus.value = await http.get('/match/status').catch(() => ({ waiting: 0, waited: 0 }))
}
async function leave() {
  await http.post('/match/leave')
  matching.value = false
  if (matchTimer) { clearInterval(matchTimer); matchTimer = null }
}
/** 人机对战: 先选难度(影响 AI 出牌/攻击/用技能的强度), 再开局 */
const botOpen = ref(false)
const botLevel = ref('normal')
const botStarting = ref(false)

const BOT_LEVELS = [
  { key: 'easy', name: '简单', desc: '每回合最多出 2 张牌，只用一半随从攻击，不放英雄技能' },
  { key: 'normal', name: '普通', desc: '贪心铺场、优先斩杀、会用英雄技能补刀' },
  { key: 'hard', name: '困难', desc: '在普通之上，斩杀时优先解决威胁最大的随从' }
]

async function joinBot() {
  botStarting.value = true
  try {
    const roomId = await http.post(`/match/bot?level=${botLevel.value}`)
    // 服务端返回的是房间号字符串; 拿不到就明确报错, 不要静默卡在弹窗里
    if (!roomId || typeof roomId !== 'string') throw new Error('服务端未返回房间号')
    localStorage.setItem('roomId', roomId)
    botOpen.value = false
    await router.push('/battle')
  } catch (e) {
    ElMessage.error('进入人机对战失败: ' + (e?.message || '未知错误'))
    console.error('[bot] 创建人机房间失败', e)
  } finally {
    botStarting.value = false
  }
}
async function doRecharge() {
  recharging.value = true
  try {
    const rc = await http.post('/wallet/recharge', { amount: rechargeAmt.value, idemKey: idemKey() })
    const balance = rc.balance
    wallet.value.balance = balance
    const u = { ...user.value, balance }
    user.value = u
    localStorage.setItem('user', JSON.stringify(u))
    rechargeOpen.value = false
  } finally {
    recharging.value = false
  }
}
async function logout() {
  // 主动退出: 通知后端清除会话标记, 这样换设备登录不会被提示"被挤下线"
  try { await http.post('/auth/logout') } catch (e) { /* 忽略: 本地仍会清理登录态 */ }
  localStorage.clear()
  router.push('/login')
}

/* ===== 新手教程(新号可选: 可以看也可以直接跳过) ===== */
const tutorAskOpen = ref(false)
const tutorOpen = ref(false)
const tutorStep = ref(0)
const tutorSteps = [
  { icon: '🃏', title: '① 组建你的卡组', text: '点「卡组编辑」用已解锁的干员凑满 30 张：把卡牌拖到上方卡组区，也可以右击卡面加入。带 👑 的是出战卡组，匹配对局就使用它。' },
  { icon: '🤖', title: '② 先打一局人机练手', text: '点「人机对战」直接开局，熟悉法力水晶、⚔攻击、❤生命。注意「召唤失调」：随从上场当回合不能攻击，要下个回合才行，拥有「冲锋」⚡的上场即可攻击。' },
  { icon: '⚔', title: '③ 开始匹配真人', text: '点「开始匹配」进入队列，等待越久匹配范围越宽（分差限制会自动放宽）。回合开始时法力回满，上限 10 点。' },
  { icon: '🎁', title: '④ 签到与商店', text: '「每日福利」签到领水晶、做每日任务也能拿水晶；商店可用金币或水晶抽卡解锁更多干员，重复卡自动回收成金币。' },
  { icon: '👤', title: '⑤ 干员档案与时装', text: '点卡面打开干员档案，能看到战吼 / 亡语 / 嘲讽 / 圣盾 / 冲锋 / 吸血等技能，还能试穿并购买时装；每个干员各穿各的皮肤，互不干扰。' }
]
function skipTutorial() {
  tutorAskOpen.value = false
  tutorOpen.value = false
  localStorage.setItem('tutorialDone', '1')
  localStorage.removeItem('tutorialPending')
}
function startTutorial() { tutorAskOpen.value = false; tutorStep.value = 0; tutorOpen.value = true }
function nextTutor() {
  if (tutorStep.value < tutorSteps.length - 1) { tutorStep.value++; return }
  skipTutorial()
  ElMessage.success('教程完成，祝你玩得开心！')
}

function onWsMsg(msg) {
  if (msg.event === 'match_found') {
    matching.value = false
    if (matchTimer) { clearInterval(matchTimer); matchTimer = null }
    localStorage.setItem('roomId', msg.roomId)
    router.push('/battle')
  }
}

/** 背景音乐: 大厅和对局共用同一引擎(utils/sound.js), 偏好存 localStorage.bgmOn */
const bgmOn = ref(sfx.isBgmOn())
function toggleBgm() {
  bgmOn.value = !bgmOn.value
  sfx.setBgmOn(bgmOn.value)
  if (bgmOn.value) { sfx.startBgm(); sfx.play('ding') } else { sfx.stopBgm(); sfx.play('click') }
}

onMounted(() => {
  if (bgmOn.value) sfx.startBgm()   // 进大厅就铺底(浏览器未解锁时内部会等手势)
  loadRank(); loadMyRank(); loadWallet(); loadBonus()
  timer = setInterval(() => { loadRank(); loadMyRank() }, 10000)
  connect(onWsMsg)
  // 新注册账号进大厅时询问是否看教程(可跳过)
  if (localStorage.getItem('tutorialPending') === '1') tutorAskOpen.value = true
})
onUnmounted(() => {
  clearInterval(timer)
  if (matchTimer) clearInterval(matchTimer)
  removeListener(onWsMsg)
  sfx.stopBgm()   // 大厅↔对局切换时由对局页接力, 不算真正停止
})
</script>

<style scoped>
.lobby { position: relative; max-width: 1180px; margin: 0 auto; padding: 20px 24px 40px; }

/* === 背景 === */
.lobby::before {
  content: "";
  position: fixed;
  inset: 0;
  background: url('/bg/lobby.jpg') center / cover no-repeat;
  filter: saturate(0.78) brightness(0.72);
  opacity: 0.42;
  z-index: -1;
  pointer-events: none;
}
.lobby::after {
  content: "";
  position: fixed;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(8, 10, 12, 0.45) 0%, rgba(8, 10, 12, 0.72) 55%, rgba(6, 8, 10, 0.9) 100%),
    radial-gradient(800px 420px at 50% 38%, rgba(240, 165, 42, 0.06), transparent 70%);
  z-index: -1;
  pointer-events: none;
}

/* === 顶部导航 === */
.topbar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: linear-gradient(90deg, rgba(30, 35, 41, 0.94), rgba(12, 15, 18, 0.88));
  border: 1px solid var(--line);
  border-left: 3px solid var(--gold);
  border-radius: 2px;
  box-shadow: 0 12px 34px rgba(0, 0, 0, 0.55), inset 0 1px 0 rgba(255, 255, 255, 0.05);
  overflow: hidden;
}
.topbar::after {
  content: "";
  position: absolute;
  left: 0; top: 0;
  width: 140px; height: 4px;
  background: repeating-linear-gradient(135deg, var(--gold) 0 8px, #1d2228 8px 16px);
  opacity: 0.8;
}
.brand { display: flex; align-items: baseline; gap: 10px; }
.brand-mark { color: var(--gold); font-size: 13px; text-shadow: 0 0 8px rgba(240, 165, 42, 0.8); }
.brand-name {
  font-size: 19px;
  font-weight: 700;
  color: var(--gold-light);
  letter-spacing: 5px;
  text-shadow: 0 0 10px rgba(255, 196, 92, 0.35);
}
.brand-sub { font-size: 10px; letter-spacing: 3px; color: #5d6773; }

.user-chip { display: flex; gap: 16px; align-items: center; font-size: 13px; }
.user-chip .nick { font-size: 16px; font-weight: 600; color: var(--gold-light); }
.avatar-chip {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
  object-position: 50% 12%;   /* 立绘是 1:2 半身图, 取上方露出干员面部 */
  background: #1b2027;
  border: 2px solid rgba(255, 196, 92, 0.65);
  box-shadow: 0 0 12px rgba(255, 196, 92, 0.35);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.avatar-chip:hover { transform: scale(1.08); box-shadow: 0 0 18px rgba(255, 196, 92, 0.6); }
.user-chip .stat { color: #8d97a3; }
.user-chip .stat.coin { color: #ffb545; font-weight: 600; }
.user-chip .stat.crystal { color: #8fd7ff; font-weight: 600; }
.btn-recharge {
  padding: 5px 12px;
  border-radius: 2px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-recharge {
  background: linear-gradient(180deg, #f2ad36, #b8781a);
  border: 1px solid #7a5416;
  color: #100d07;
  font-weight: 700;
  letter-spacing: 2px;
}
.btn-recharge:hover { box-shadow: 0 0 12px rgba(255, 196, 92, 0.6); }
.btn-admin {
  padding: 5px 12px;
  border-radius: 2px;
  font-size: 12px;
  cursor: pointer;
  background: rgba(240, 165, 42, 0.12);
  border: 1px solid var(--gold);
  color: var(--gold-light);
  font-weight: 700;
  letter-spacing: 2px;
  transition: all 0.15s;
}
.btn-admin:hover { background: rgba(240, 165, 42, 0.28); box-shadow: 0 0 12px rgba(240, 165, 42, 0.5); }
/* 「新手教程」用青色区分于「管理后台」 */
.btn-admin.tutor { border-color: #2f6b78; color: #6fd8f0; background: rgba(111, 216, 240, 0.1); }
.btn-admin.tutor:hover { background: rgba(111, 216, 240, 0.24); box-shadow: 0 0 12px rgba(111, 216, 240, 0.5); }
/* 音效开关 */
.btn-sfx {
  padding: 5px 12px;
  border-radius: 2px;
  font-size: 12px;
  cursor: pointer;
  letter-spacing: 1px;
  background: rgba(79, 214, 230, 0.1);
  border: 1px solid rgba(79, 214, 230, 0.5);
  color: #a4eef6;
  font-weight: 700;
  transition: all 0.15s;
}
.btn-sfx:hover { background: rgba(79, 214, 230, 0.22); }
.btn-sfx.off { background: rgba(120, 130, 140, 0.1); border-color: #4a535e; color: #7b848f; }
/* 背景音乐按钮: 金色系与音效的青色区分开 */
.btn-sfx.bgm { background: rgba(240, 165, 42, 0.1); border-color: rgba(240, 165, 42, 0.5); color: #f0cc6d; }
.btn-sfx.bgm.off { background: rgba(120, 130, 140, 0.1); border-color: #4a535e; color: #7b848f; }

/* === 卡组检测 / 新手教程弹窗 === */
.dlg-body { padding: 4px 2px; }
.deck-warn { margin: 0 0 8px; font-size: 15px; font-weight: 700; color: #e6a23c; text-align: center; }
.deck-detail { margin: 0 0 8px; font-size: 13px; color: var(--text-mute); text-align: center; line-height: 1.7; }
.deck-tip { margin: 10px 0 0; font-size: 12px; color: var(--text-mute); text-align: center; line-height: 1.7; }
.btn-main-sm, .btn-plain-sm {
  padding: 6px 14px; font-size: 12px; cursor: pointer; border-radius: 2px; font-weight: 600;
}
.btn-main-sm { background: var(--gold); color: #1a1005; border: 1px solid var(--gold); }
.btn-main-sm:hover:not(:disabled) { background: var(--gold-light); }
.btn-main-sm:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-plain-sm { background: transparent; color: var(--text-mute); border: 1px solid var(--line); }
.btn-plain-sm:hover { color: var(--gold-light); border-color: var(--gold); }

.tutor-body { text-align: center; padding: 6px 4px 2px; }
.tutor-icon { font-size: 40px; line-height: 1; margin-bottom: 10px; }
.tutor-title { font-size: 16px; font-weight: 700; color: var(--gold-light); margin-bottom: 10px; }
.tutor-text { margin: 0; font-size: 13px; color: var(--text-mute); line-height: 1.9; text-align: left; }
.tutor-dots { display: flex; justify-content: center; gap: 6px; margin-top: 14px; }
.tutor-dots .dot { width: 6px; height: 6px; border-radius: 50%; background: rgba(255,255,255,0.18); }
.tutor-dots .dot.on { background: var(--gold); box-shadow: 0 0 6px rgba(240,165,42,0.6); }

/* === 主体两栏 === */
.main { display: flex; gap: 28px; align-items: stretch; margin-top: 28px; }

/* === 中央英雄区 === */
.hero {
  flex: 1;
  min-width: 0;            /* 关键: 允许被压缩, 否则快捷菜单一行 1120px 会把天梯栏挤出屏幕 */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px 32px;
}
.hero-title {
  display: flex;
  align-items: center;
  gap: 18px;
}
.hero-title h1 {
  margin: 0;
  font-size: 42px;
  font-weight: 800;
  letter-spacing: 16px;
  text-indent: 16px; /* 视觉居中补偿 */
  color: var(--gold-light);
  text-shadow:
    0 0 14px rgba(255, 196, 92, 0.55),
    0 0 44px rgba(240, 165, 42, 0.28),
    0 2px 6px rgba(0, 0, 0, 0.8);
  white-space: nowrap;
}
.hero-deco {
  color: var(--gold);
  font-size: 14px;
  text-shadow: 0 0 10px rgba(240, 165, 42, 0.9);
}
.hero-line {
  width: 72px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(240, 165, 42, 0.7));
}
.hero-title .hero-line:last-child {
  background: linear-gradient(90deg, rgba(240, 165, 42, 0.7), transparent);
}
.hero-sub {
  margin: 14px 0 42px;
  font-size: 13px;
  letter-spacing: 8px;
  text-indent: 8px;
  color: #8d97a3;
}

/* === 匹配主按钮（表面质感由 ark.css 统一） === */
.btn-match {
  width: 100%;
  max-width: 420px;
  padding: 38px 0 30px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
}
.btn-match:hover:not(:disabled) { transform: translateY(-2px); }
.btn-match:active:not(:disabled) { transform: translateY(0); }
.btn-match.matching { animation: pulse 1.8s ease-in-out infinite; }
.match-icon { display: block; font-size: 34px; margin-bottom: 12px; }
.match-main {
  display: block;
  font-size: 27px;
  font-weight: 800;
  letter-spacing: 14px;
  text-indent: 14px;
}
.match-sub {
  display: block;
  margin-top: 10px;
  font-size: 11px;
  letter-spacing: 5px;
  text-indent: 5px;
  opacity: 0.75;
}
@keyframes pulse {
  0%, 100% { box-shadow: 0 10px 38px rgba(0, 0, 0, 0.6); }
  50%      { box-shadow: 0 10px 48px rgba(226, 85, 79, 0.4); }
}

/* === 快捷入口 === */
.quick-actions {
  display: flex;
  gap: 14px;
  margin-top: 38px;
  flex-wrap: wrap;              /* 窗口不够宽时换行, 不许把天梯栏挤出去 */
  justify-content: center;
}
.btn-quick {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex: 0 1 112px;              /* 可收缩: 先整体缩窄, 真放不下才换行 */
  min-width: 88px;
  padding: 16px 10px;
  cursor: pointer;
  transition: transform 0.15s, border-color 0.15s, box-shadow 0.15s;
}
.btn-quick:hover { transform: translateY(-2px); }
/* 图标换成明日方舟 Q 版形象图片(立绘素材来自开源仓库) */
.q-icon { display: flex; align-items: center; justify-content: center; height: 28px; }
.q-icon img { max-height: 28px; max-width: 28px; object-fit: contain; }
.q-label { font-size: 13px; letter-spacing: 2px; }
.btn-quick.bot { border-color: rgba(79, 214, 230, 0.35) !important; }
.btn-quick.bot:hover {
  border-color: #4fd6e6 !important;
  box-shadow: 0 6px 20px rgba(79, 214, 230, 0.3) !important;
  color: #a4eef6 !important;
}
.btn-quick.shop { border-color: rgba(242, 173, 54, 0.4) !important; }
.btn-quick.bonus { border-color: rgba(143, 215, 255, 0.4) !important; position: relative; }
.btn-quick.bonus:hover {
  border-color: #8fd7ff !important;
  box-shadow: 0 6px 20px rgba(143, 215, 255, 0.3) !important;
  color: #c8ecff !important;
}
.q-dot {
  position: absolute;
  top: 10px; right: 14px;
  width: 8px; height: 8px;
  border-radius: 50%;
  background: #ff5a52;
  box-shadow: 0 0 8px rgba(255, 90, 82, 0.9);
  animation: dotPulse 1.6s ease-in-out infinite;
}
@keyframes dotPulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.35); opacity: 0.7; }
}

/* === 每日福利弹窗 === */
.bonus-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 14px; }
.bh-crystal { font-size: 26px; font-weight: 800; color: #8fd7ff; }
.bh-unit { font-size: 12px; color: #6b7683; font-weight: 400; margin-left: 4px; }
.bh-streak { font-size: 12px; color: #c9a95e; }
.sign-btn {
  width: 100%;
  padding: 16px 0;
  border-radius: 8px;
  cursor: pointer;
  background: linear-gradient(160deg, rgba(143, 215, 255, 0.22), rgba(11, 14, 17, 0.9));
  border: 1px solid rgba(143, 215, 255, 0.55);
  color: #c8ecff;
  transition: all 0.15s;
}
.sign-btn:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(143, 215, 255, 0.25); }
.sign-btn.done { border-color: rgba(120, 200, 120, 0.5); color: #9fd99f; cursor: default; }
.sign-main { display: block; font-size: 17px; font-weight: 700; letter-spacing: 4px; }
.sign-sub { display: block; margin-top: 5px; font-size: 12px; opacity: 0.8; }
.task-title { margin: 20px 0 10px; font-size: 13px; color: #c3cad3; letter-spacing: 3px; }
.task-list { display: flex; flex-direction: column; gap: 10px; }
.task-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  background: rgba(11, 14, 17, 0.7);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.t-info { flex: 1; }
.t-desc { font-size: 13px; color: #d9dee5; }
.t-progress { display: flex; align-items: center; gap: 10px; margin-top: 8px; }
.t-bar { flex: 1; height: 6px; background: rgba(0, 0, 0, 0.5); border-radius: 3px; overflow: hidden; }
.t-bar-in { height: 100%; background: linear-gradient(90deg, #4a90d9, #8fd7ff); transition: width 0.3s; }
.t-num { font-size: 11px; color: #8d97a3; }
.t-claim {
  min-width: 76px;
  padding: 8px 0;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  background: rgba(11, 14, 17, 0.9);
  border: 1px solid var(--line);
  color: #8d97a3;
  transition: all 0.15s;
}
.t-claim.ready {
  background: linear-gradient(180deg, #8fd7ff, #3a7ca8);
  border-color: #2a5f84;
  color: #04222f;
  font-weight: 700;
}
.t-claim.ready:hover { box-shadow: 0 0 14px rgba(143, 215, 255, 0.5); }
.t-claim.done { border-color: rgba(120, 200, 120, 0.4); color: #9fd99f; cursor: default; }
.t-claim:disabled { opacity: 0.7; cursor: default; }
.bonus-tip { margin-top: 16px; font-size: 11px; color: #6b7683; text-align: center; line-height: 1.8; }
.btn-quick.danger { border-color: rgba(226, 85, 79, 0.4) !important; }
.btn-quick.data { border-color: rgba(179, 142, 240, 0.4) !important; }
.btn-quick.data:hover {
  border-color: #b38ef0 !important;
  box-shadow: 0 6px 20px rgba(179, 142, 240, 0.3) !important;
  color: #d9c4fb !important;
}
.btn-quick.danger:hover {
  border-color: var(--blood) !important;
  box-shadow: 0 6px 20px rgba(226, 85, 79, 0.3) !important;
  color: #ffdada !important;
}

.announce {
  margin-top: 34px;
  padding-top: 16px;
  text-align: center;
  line-height: 2;
  font-size: 12px;
  color: #6b7683;
  letter-spacing: 1px;
  border-top: 1px dashed rgba(240, 165, 42, 0.22);
}
.ak-credit { font-size: 10px; color: #4c5561; letter-spacing: 0.5px; }

/* === 匹配等待 Q 版小人 === */
.matching-mascot {
  margin-top: 22px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.mascot-img {
  width: 110px;
  height: 110px;
  object-fit: contain;
  filter: drop-shadow(0 6px 14px rgba(255, 196, 92, 0.3));
  animation: bob 2s ease-in-out infinite;
}
.mascot-text { font-size: 12px; color: #8d97a3; letter-spacing: 3px; }
.mascot-sub { display: block; margin-top: 6px; font-size: 11px; color: #6fd8f0; letter-spacing: 1px; }
.mascot-hint { color: var(--gold-light); }
.mascot-note {
  margin: 8px auto 0; max-width: 460px;
  font-size: 11px; color: #8a7b62; line-height: 1.7; letter-spacing: 0;
}
.mascot-note b { color: #e6a23c; }
@keyframes bob {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

/* === 天梯侧栏（表面质感由 ark.css 统一） === */
.rank-col {
  width: clamp(240px, 27vw, 320px);   /* 窄窗口(笔记本)自动收窄, 保证天梯栏始终在屏内 */
  flex-shrink: 0;
  align-self: stretch;
  overflow: hidden;
}
.rank-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 14px 18px;
  background: rgba(0, 0, 0, 0.3);
  border-bottom: 1px solid rgba(240, 165, 42, 0.3);
  font-size: 16px;
  font-weight: 700;
  color: var(--gold-light);
  letter-spacing: 3px;
}
.rank-refresh { font-size: 10px; color: #6b7683; letter-spacing: 1px; font-weight: 400; }
.rank-list {
  list-style: none;
  padding: 8px 0;
  margin: 0;
  max-height: 560px;
  overflow-y: auto;
}
.rank-item {
  display: grid;
  grid-template-columns: 36px 26px 1fr 60px 50px;
  gap: 6px;
  align-items: center;
  padding: 9px 18px;
  font-size: 13px;
  border-bottom: 1px solid rgba(51, 58, 68, 0.3);
  color: #b3bbc6;
}
.rank-item:last-child { border-bottom: none; }
.rank-item:hover { background: rgba(240, 165, 42, 0.07); }
.rank-item.me { background: rgba(240, 165, 42, 0.14); color: var(--gold-light); font-weight: 600; }
.rk { font-size: 16px; text-align: center; }
.r-avatar {
  width: 24px; height: 24px;
  border-radius: 50%;
  object-fit: cover;
  object-position: 50% 12%;   /* 立绘是 1:2 半身图, 取上方露出干员面部 */
  background: #1b2027;
  border: 1px solid rgba(255, 196, 92, 0.4);
}
.rk-1 { text-shadow: 0 0 6px #ffc45c; }
.rk-2 { text-shadow: 0 0 4px #ccc; }
.rk-3 { text-shadow: 0 0 4px #d4874a; }
.rnick { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rscore { color: var(--gold-light); font-weight: 600; text-align: right; }
.rwl { color: #6b7683; font-size: 11px; text-align: right; }
.rank-empty { text-align: center; padding: 28px 0; color: #6b7683; font-size: 13px; }

/* === 奖杯图标(内联 SVG, 与其它按钮的 Q 版形象图标风格统一为线性描边) === */
.trophy-svg {
  width: 26px; height: 26px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
  color: var(--gold-light);
}
.trophy-svg.big { width: 34px; height: 34px; }
.btn-quick.rank { border-color: rgba(240, 165, 42, 0.4) !important; }
.btn-quick.rank:hover {
  border-color: var(--gold-light) !important;
  box-shadow: 0 6px 20px rgba(240, 165, 42, 0.32) !important;
  color: var(--gold-light) !important;
}

/* === 我的战绩卡 === */
/* 顶栏天梯分旁挂段位徽章 */
.tier-stat { display: inline-flex; align-items: center; gap: 5px; }
.mr-tier {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 12px;
}
.mr-tier-next { font-size: 10px; color: #6b7683; letter-spacing: 0.5px; }
.my-rank {
  padding: 14px 18px 12px;
  background: rgba(0, 0, 0, 0.22);
  border-bottom: 1px solid rgba(51, 58, 68, 0.4);
}
.mr-row { display: flex; }
.mr-cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}
.mr-num {
  font-size: 20px;
  font-weight: 700;
  color: #dfe5ec;
  line-height: 1.1;
}
.mr-num.gold { color: var(--gold-light); }
.mr-label { font-size: 10px; color: #6b7683; letter-spacing: 1px; }
.mr-foot {
  margin-top: 10px;
  text-align: center;
  font-size: 11px;
  color: #6b7683;
  letter-spacing: 1px;
}

/* === 前三名预览(只显示 3 条, 完整榜单在弹窗里) === */
.rank-list-top3 { max-height: none; padding: 6px 0; }
/* 列: 名次 / 头像 / 昵称 / 段位 / 分数 */
.rank-list-top3 .rank-item { grid-template-columns: 34px 26px 1fr 22px 56px; }

/* === 奖杯按钮 === */
.trophy-wrap { padding: 12px 14px 16px; }
.btn-trophy {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 14px 10px;
  background: linear-gradient(180deg, rgba(240, 165, 42, 0.16), rgba(240, 165, 42, 0.05));
  border: 1px solid rgba(240, 165, 42, 0.45);
  border-radius: 4px;
  color: var(--gold-light);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.btn-trophy:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(240, 165, 42, 0.3);
}
.t-main { font-size: 14px; font-weight: 600; letter-spacing: 2px; }
.t-sub { font-size: 9px; color: #8b949e; letter-spacing: 2px; }

/* === 人机难度选择 === */
.bot-levels { display: flex; flex-direction: column; gap: 8px; }
.bot-lv {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 14px;
  text-align: left;
  background: rgba(11, 14, 17, 0.6);
  border: 1px solid rgba(120, 80, 30, 0.4);
  border-radius: 4px;
  color: #b3bbc6;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.bot-lv:hover { background: rgba(240, 165, 42, 0.08); }
.bot-lv.active { border-color: var(--gold-light); background: rgba(240, 165, 42, 0.14); }
.bl-name { font-size: 14px; font-weight: 700; letter-spacing: 2px; }
.bl-desc { font-size: 11px; color: #7c8794; line-height: 1.5; }
.lv-easy.active .bl-name { color: #7fd67f; }
.lv-normal.active .bl-name { color: var(--gold-light); }
.lv-hard.active .bl-name { color: #ff7a5c; }
.bot-lv.active { border-color: currentColor; }

/* === 排行榜弹窗 === */
.lb-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 3px;
  color: var(--gold-light);
}
.lb-count {
  font-size: 10px;
  font-weight: 400;
  letter-spacing: 1px;
  color: #6b7683;
}
/* 领奖台: 中间(金牌)高出一截 */
.podium {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 10px;
  padding: 6px 0 16px;
  border-bottom: 1px solid rgba(51, 58, 68, 0.4);
}
.pd {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 6px;
  border-radius: 4px;
  background: rgba(11, 14, 17, 0.55);
}
.pd-1 { padding-bottom: 22px; background: rgba(240, 165, 42, 0.12); }
.pd-av {
  width: 40px; height: 40px;
  border-radius: 50%;
  object-fit: cover;
  object-position: 50% 12%;
  border: 1px solid rgba(255, 196, 92, 0.5);
  background: #1b2027;
}
.pd-1 .pd-av { width: 50px; height: 50px; }
.pd-medal { font-size: 15px; }
.pd-nick {
  font-size: 12px;
  color: #dfe5ec;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pd-score { font-size: 13px; font-weight: 700; color: var(--gold-light); }
.lb-list {
  list-style: none;
  margin: 0;
  padding: 8px 0;
  max-height: 320px;
  overflow-y: auto;
}
.lb-item {
  display: grid;
  /* 列: 名次 / 头像 / 昵称 / 段位 / 分数 / 胜负 */
  grid-template-columns: 40px 28px 1fr 24px 62px 52px;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  font-size: 13px;
  border-bottom: 1px solid rgba(51, 58, 68, 0.3);
  color: #b3bbc6;
}
.lb-item.top3 { background: rgba(240, 165, 42, 0.05); }
.lb-item.me { background: rgba(240, 165, 42, 0.16); color: var(--gold-light); font-weight: 600; }
.lb-rk { font-size: 14px; text-align: center; }
.lb-me {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 10px 12px;
  background: rgba(240, 165, 42, 0.12);
  border: 1px solid rgba(240, 165, 42, 0.35);
  border-radius: 4px;
  font-size: 13px;
  color: var(--gold-light);
}
.lb-me-tag {
  flex-shrink: 0;
  width: 22px; height: 22px;
  line-height: 22px;
  text-align: center;
  border-radius: 50%;
  background: var(--gold-light);
  color: #1b2027;
  font-size: 11px;
  font-weight: 700;
}
.lb-me-txt b { color: #ffd98a; }
.lb-tip { margin-top: 8px; text-align: center; font-size: 11px; color: #6b7683; }

@media (max-width: 768px) {
  .lb-item { grid-template-columns: 34px 24px 1fr 22px 52px 44px; font-size: 12px; }
  .btn-trophy { padding: 12px 8px; }
}

/* === 个人资料 / 头像自选 === */
.profile {
  display: flex;
  gap: 18px;
  align-items: center;
  padding: 14px;
  background: rgba(11, 14, 17, 0.6);
  border: 1px solid var(--line);
  border-radius: 2px;
}
.profile-avatar {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  object-fit: cover;
  object-position: 50% 12%;   /* 立绘是 1:2 半身图, 取上方露出干员面部 */
  background: #1b2027;
  border: 2px solid rgba(255, 196, 92, 0.65);
  box-shadow: 0 0 16px rgba(255, 196, 92, 0.35);
}
.profile-info { flex: 1; }
.p-nick { font-size: 18px; font-weight: 700; color: var(--gold-light); margin-bottom: 6px; }
.p-line { font-size: 12px; color: #8d97a3; line-height: 1.9; }
.p-line.coin { color: #ffb545; font-weight: 600; }

.avatar-title { margin: 18px 0 10px; font-size: 13px; color: #c3cad3; letter-spacing: 2px; }
.avatar-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; }
.avatar-opt {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 50%;
  object-fit: cover;
  object-position: 50% 12%;   /* 立绘是 1:2 半身图, 取上方露出干员面部 */
  background: #1b2027;
  border: 2px solid rgba(51, 58, 68, 0.9);
  cursor: pointer;
  transition: transform 0.15s, border-color 0.15s, box-shadow 0.15s;
}
.avatar-opt:hover { transform: scale(1.08); border-color: var(--gold); }
.avatar-opt.on { border-color: var(--gold-light); box-shadow: 0 0 12px rgba(255, 196, 92, 0.7); }
.avatar-credit { margin-top: 12px; font-size: 10px; color: #4c5561; text-align: center; letter-spacing: 0.5px; }

/* === 充值弹窗 === */
.recharge-tip { font-size: 12px; color: #6b7683; text-align: center; margin-bottom: 16px; }
.recharge-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.recharge-opt {
  padding: 16px 0;
  background: rgba(11, 14, 17, 0.85);
  border: 1px solid var(--line);
  border-radius: 2px;
  cursor: pointer;
  transition: all 0.15s;
  color: #d9dee5;
}
.recharge-opt:hover { border-color: var(--gold); }
.recharge-opt.on {
  border-color: #f2ad36;
  background: linear-gradient(160deg, rgba(242, 173, 54, 0.2), rgba(11, 14, 17, 0.9));
  box-shadow: 0 0 12px rgba(242, 173, 54, 0.35);
}
.recharge-opt .amt { display: block; font-size: 18px; font-weight: 700; color: var(--gold-light); }
.recharge-opt .bonus { display: block; margin-top: 4px; font-size: 11px; color: #6b7683; }
.recharge-body + .btn-main, .recharge-body ~ .btn-main { letter-spacing: 3px; }

/* ===== 平板竖屏: 主体改单栏, 天梯落到下方 ===== */
@media (max-width: 980px) {
  .main { flex-direction: column; gap: 20px; }
  .rank-col { width: 100%; }
  .rank-list { max-height: 340px; }
  .quick-actions { flex-wrap: wrap; justify-content: center; }
  .btn-quick { width: 104px; }
}

/* ===== 手机竖屏 ===== */
@media (max-width: 680px) {
  .lobby { padding: 10px 12px 28px; }

  /* 顶栏: 品牌与用户信息分行, 信息区自动换行 */
  .topbar { flex-direction: column; align-items: stretch; gap: 10px; padding: 10px 12px; }
  .brand { justify-content: center; }
  .brand-name { font-size: 16px; letter-spacing: 3px; }
  .brand-sub { display: none; }
  .user-chip {
    flex-wrap: wrap; gap: 6px 10px;
    justify-content: center; font-size: 12px;
  }
  .user-chip .nick { font-size: 14px; }
  .avatar-chip { width: 34px; height: 34px; }
  .btn-admin, .btn-sfx, .btn-recharge { padding: 4px 9px; font-size: 11px; letter-spacing: 1px; }

  /* 英雄区收紧 */
  .hero { padding: 22px 6px 18px; }
  .hero-title { gap: 10px; }
  .hero-title h1 { font-size: 23px; letter-spacing: 5px; text-indent: 5px; }
  .hero-line { width: 26px; }
  .hero-sub { margin: 10px 0 22px; font-size: 11px; letter-spacing: 4px; text-indent: 4px; }

  /* 匹配按钮 */
  .btn-match { padding: 24px 0 18px; }
  .match-icon { font-size: 26px; margin-bottom: 8px; }
  .match-main { font-size: 20px; letter-spacing: 8px; text-indent: 8px; }
  .match-sub { font-size: 10px; letter-spacing: 3px; }

  /* 快捷入口: 一行 3 个 */
  .quick-actions { gap: 8px; margin-top: 20px; width: 100%; }
  .btn-quick { width: calc(33.33% - 6px); padding: 12px 2px; gap: 6px; }
  .q-icon img { max-height: 24px; max-width: 24px; }
  .q-label { font-size: 12px; letter-spacing: 1px; }
  .q-dot { top: 8px; right: 10px; }

  .mascot-img { width: 82px; height: 82px; }
  .mascot-text { font-size: 11px; letter-spacing: 1px; }
  .mascot-note { font-size: 10px; max-width: 100%; padding: 0 4px; }

  .announce { margin-top: 22px; font-size: 10px; line-height: 1.9; letter-spacing: 0; }
  .ak-credit { font-size: 9px; }

  /* 天梯列表缩小栅格 */
  .rank-head { padding: 10px 12px; font-size: 14px; }
  .rank-item { grid-template-columns: 28px 22px 1fr 46px 46px; gap: 4px; padding: 8px 10px; font-size: 12px; }
  .rank-list { max-height: 300px; }

  /* 头像选择 4 列 */
  .avatar-grid { grid-template-columns: repeat(4, 1fr); gap: 8px; }
  .profile { gap: 12px; padding: 10px; }
  .profile-avatar { width: 64px; height: 64px; }

  /* 福利弹窗里的按钮 */
  .t-claim { min-width: 64px; font-size: 11px; }
}
</style>
