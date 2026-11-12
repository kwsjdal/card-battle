<template>
  <div class="admin">
    <!-- 顶部 -->
    <div class="toolbar">
      <div class="tl-left">
        <button class="btn-tb" @click="exitAdmin">← 退出管理端</button>
      </div>
      <div class="tl-center">
        <span class="page-title">管 理 后 台</span>
      </div>
      <div class="tl-right">
        <button class="btn-tb" @click="loadAll">刷新数据</button>
      </div>
    </div>

    <!-- 统计卡 + 近7日迷你趋势 -->
    <div class="stat-row">
      <div class="stat-card">
        <span class="stat-num">{{ stats.userCount ?? '-' }}</span>
        <span class="stat-label">注册用户</span>
        <svg class="spark" viewBox="0 0 150 34" preserveAspectRatio="none">
          <path :d="sparkUsers.area" fill="rgba(240, 165, 42, 0.14)" />
          <polyline :points="sparkUsers.line" fill="none" stroke="rgb(240, 165, 42)" stroke-width="1.5" />
          <circle v-if="sparkUsers.last" :cx="sparkUsers.last[0]" :cy="sparkUsers.last[1]" r="2.6" fill="#ffc45c" />
        </svg>
        <span class="spark-label">近7日 +{{ sum(trend.newUsers) }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-num">{{ stats.recordCount ?? '-' }}</span>
        <span class="stat-label">累计对局</span>
        <svg class="spark" viewBox="0 0 150 34" preserveAspectRatio="none">
          <path :d="sparkMatches.area" fill="rgba(79, 214, 230, 0.12)" />
          <polyline :points="sparkMatches.line" fill="none" stroke="rgb(79, 214, 230)" stroke-width="1.5" />
          <circle v-if="sparkMatches.last" :cx="sparkMatches.last[0]" :cy="sparkMatches.last[1]" r="2.6" fill="#9ef1fb" />
        </svg>
        <span class="spark-label">近7日 +{{ sum(trend.matches) }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-num">🪙 {{ stats.rechargeTotal ?? '-' }}</span>
        <span class="stat-label">充值总额</span>
        <svg class="spark" viewBox="0 0 150 34" preserveAspectRatio="none">
          <path :d="sparkCoins.area" fill="rgba(126, 217, 87, 0.12)" />
          <polyline :points="sparkCoins.line" fill="none" stroke="rgb(126, 217, 87)" stroke-width="1.5" />
          <circle v-if="sparkCoins.last" :cx="sparkCoins.last[0]" :cy="sparkCoins.last[1]" r="2.6" fill="#b6f29a" />
        </svg>
        <span class="spark-label">近7日 +{{ trend.coinSum?.length ? sum(trend.coinSum) : 0 }}</span>
      </div>
    </div>

    <!-- 三大板块 -->
    <el-tabs v-model="tab" class="admin-tabs">
      <el-tab-pane label="用户管理" name="users">
        <div class="tab-tools">
          <el-input v-model="userKw" placeholder="账号 / 昵称 / 手机号" clearable
                    style="width: 220px" @keyup.enter="searchUsers" @clear="searchUsers" />
          <el-select v-model="userStatus" style="width: 130px" @change="searchUsers">
            <el-option label="全部状态" value="all" />
            <el-option label="正常" value="normal" />
            <el-option label="已封禁" value="banned" />
          </el-select>
          <button class="btn-main-sm btn-sm" @click="searchUsers">查询</button>
          <span class="tools-total">共 {{ userTotal }} 人</span>
        </div>

        <el-table :data="users" size="small" class="dark-table" @row-click="openDetail">
          <el-table-column prop="id" label="ID" width="46" />
          <el-table-column prop="username" label="账号" min-width="84" />
          <el-table-column prop="nickname" label="昵称" min-width="84" />
          <el-table-column label="角色" width="54">
            <template #default="{ row }">
              <span class="role-tag" :class="row.role === 'admin' ? 'r-admin' : ''">
                {{ row.role === 'admin' ? '管理员' : '玩家' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="rating" label="天梯分" width="56" />
          <el-table-column label="手机号" width="88">
            <template #default="{ row }">{{ row.phone || '-' }}</template>
          </el-table-column>
          <el-table-column label="战绩" width="80">
            <template #default="{ row }">{{ row.winCount }} 胜 / {{ row.loseCount }} 负</template>
          </el-table-column>
          <el-table-column label="余额" width="62">
            <template #default="{ row }">🪙 {{ row.balance }}</template>
          </el-table-column>
          <el-table-column label="注册时间" min-width="92">
            <template #default="{ row }">{{ fmt(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="132">
            <template #default="{ row }">
              <span class="status-tag" :class="row.status === 1 ? 'banned' : 'normal'">
                {{ row.status === 1 ? '已封禁' : '正常' }}
              </span>
              <div v-if="row.status === 1 && row.bannedUntil" class="ban-until">
                {{ fmtShort(row.bannedUntil) }} 解封
              </div>
              <div v-if="row.status === 1 && row.banReason" class="ban-reason" :title="row.banReason">
                {{ row.banReason }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="300">
            <template #default="{ row }">
              <template v-if="row.role !== 'admin'">
                <div class="op-btns">
                  <button class="btn-op" @click.stop="openCoins(row)">金币</button>
                  <button class="btn-op" @click.stop="openRating(row)">天梯</button>
                  <button class="btn-op pwd" @click.stop="openViewPwd(row)">查看密码</button>
                  <button class="btn-op" @click.stop="openResetPwd(row)">重置密码</button>
                  <button v-if="row.status !== 1" class="btn-ban" @click.stop="openBan(row)">封禁</button>
                  <button v-else class="btn-unban" @click.stop="unban(row)">解封</button>
                  <button class="btn-del" @click.stop="openDelete(row)">注销</button>
                </div>
              </template>
              <span v-else class="op-none">-</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination background layout="prev, pager, next" :total="userTotal"
                         :page-size="userSize" :current-page="userPage"
                         @current-change="(p) => { userPage = p; loadUsers() }" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="对局记录" name="records">
        <el-table :data="records" size="small" class="dark-table">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="roomId" label="房间号" width="130" />
          <el-table-column label="玩家1" min-width="120">
            <template #default="{ row }">{{ nameMap[row.player1Id] || row.player1Id }}</template>
          </el-table-column>
          <el-table-column label="玩家2" min-width="120">
            <template #default="{ row }">{{ nameMap[row.player2Id] || row.player2Id }}</template>
          </el-table-column>
          <el-table-column label="胜者" min-width="120">
            <template #default="{ row }">
              <span v-if="row.winnerId === null" class="draw-tag">平局</span>
              <span v-else class="win-tag">{{ nameMap[row.winnerId] || row.winnerId }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="rounds" label="回合" width="70" />
          <el-table-column label="时间" min-width="150">
            <template #default="{ row }">{{ fmt(row.createTime) }}</template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination background layout="prev, pager, next" :total="recordTotal"
                         :page-size="recordSize" :current-page="recordPage"
                         @current-change="(p) => { recordPage = p; loadRecords() }" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="充值流水" name="recharges">
        <el-table :data="recharges" size="small" class="dark-table">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="nickname" label="用户" min-width="120" />
          <el-table-column label="金额" width="100">
            <template #default="{ row }">🪙 {{ row.amount }}</template>
          </el-table-column>
          <el-table-column label="充值后余额" width="110">
            <template #default="{ row }">🪙 {{ row.balanceAfter }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="渠道" min-width="140" />
          <el-table-column label="时间" min-width="150">
            <template #default="{ row }">{{ fmt(row.createTime) }}</template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination background layout="prev, pager, next" :total="rechargeTotal"
                         :page-size="rechargeSize" :current-page="rechargePage"
                         @current-change="(p) => { rechargePage = p; loadRecharges() }" />
        </div>
      </el-tab-pane>

      <!-- 验证码流水: 演示环境没有短信网关时, 管理员在此查码 -->
      <el-tab-pane label="验证码查询" name="sms">
        <div class="tab-tools">
          <el-input v-model="smsPhone" placeholder="要查询的手机号（11 位）" maxlength="11"
                    style="width: 220px" @keyup.enter="querySms" />
          <button class="btn-main-sm btn-sm" @click="querySms">查 询</button>
          <span class="tool-tip">接口不再把验证码下发给前端，演示环境由此处查看（接入短信网关后应删除）</span>
        </div>
        <div v-if="smsResult" class="sms-box">
          <div class="sms-phone">{{ smsResult.phone }}</div>
          <div v-if="smsResult.list && smsResult.list.length" class="sms-list">
            <div v-for="(l, i) in smsResult.list" :key="i" class="sms-item">
              <span class="si-time">{{ l.split(' ')[0] }} {{ l.split(' ')[1] }}</span>
              <span class="si-code">{{ l.split(' ')[2] }}</span>
              <span class="si-scene">{{ l.split(' ')[3] }}</span>
            </div>
          </div>
          <div v-else class="sms-empty">该手机号最近没有验证码记录（验证码 5 分钟后自动清除）</div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 用户详情抽屉 -->
    <el-drawer v-model="detailOpen" size="440px" :with-header="false" class="detail-drawer">
      <div v-if="!detail" class="dt-loading">加载中…</div>
      <div v-else class="dt">
        <div class="dt-head">
          <img class="dt-avatar" :src="detail.user.avatar || '/avatars/1.png'" alt="" />
          <div class="dt-head-info">
            <div class="dt-name">
              {{ detail.user.nickname }}
              <span class="role-tag" :class="detail.user.role === 'admin' ? 'r-admin' : ''">
                {{ detail.user.role === 'admin' ? '管理员' : '玩家' }}
              </span>
            </div>
            <div class="dt-sub">{{ detail.user.username }} · ID {{ detail.user.id }}</div>
          </div>
          <button class="dt-close" @click="detailOpen = false">关闭</button>
        </div>

        <div v-if="detail.user.status === 1" class="dt-ban">
          已封禁 · 解封时间 {{ fmt(detail.user.bannedUntil) }} · 原因 {{ detail.user.banReason || '-' }}
        </div>

        <div class="dt-grid">
          <div class="dt-item"><span class="k">天梯分</span><span class="v gold">{{ detail.user.rating }}</span></div>
          <div class="dt-item"><span class="k">金币余额</span><span class="v gold">🪙 {{ detail.user.balance }}</span></div>
          <div class="dt-item"><span class="k">战绩</span><span class="v">{{ detail.user.winCount }} 胜 / {{ detail.user.loseCount }} 负</span></div>
          <div class="dt-item"><span class="k">卡牌解锁</span><span class="v">{{ detail.cardCount }} 张</span></div>
          <div class="dt-item"><span class="k">手机号</span><span class="v">{{ detail.user.phone || '-' }}</span></div>
          <div class="dt-item"><span class="k">注册时间</span><span class="v">{{ fmt(detail.user.createTime) }}</span></div>
        </div>

        <div class="dt-sec">最近对局 <span class="dt-sec-n">{{ detail.records.length }}</span></div>
        <div v-if="!detail.records.length" class="dt-empty">暂无对局记录</div>
        <div v-for="r in detail.records" :key="r.id" class="dt-row">
          <span class="dt-row-main">vs {{ otherName(r) }}</span>
          <span class="dt-row-tag" :class="resultClass(r)">{{ resultText(r) }}</span>
          <span class="dt-row-time">{{ fmtShort(r.createTime) }}</span>
        </div>

        <div class="dt-sec">最近充值 <span class="dt-sec-n">{{ detail.recharges.length }}</span></div>
        <div v-if="!detail.recharges.length" class="dt-empty">暂无充值记录</div>
        <div v-for="l in detail.recharges" :key="l.id" class="dt-row">
          <span class="dt-row-main">🪙 {{ l.amount }}</span>
          <span class="dt-row-sub">{{ l.remark }}</span>
          <span class="dt-row-time">{{ fmtShort(l.createTime) }}</span>
        </div>
      </div>
    </el-drawer>

    <!-- 封禁: 选时长+原因 → 二次确认 -->
    <el-dialog v-model="banOpen" title="🚫 封禁账号" width="420px" center @closed="banStep = 1">
      <div v-if="banStep === 1" class="dlg-body">
        <p class="dlg-tip">封禁「{{ banTarget?.nickname }}」，到期后自动解封</p>
        <div class="dur-grid">
          <button v-for="d in durations" :key="d.days"
                  class="dur-opt" :class="{ on: banDays === d.days }"
                  @click="banDays = d.days">{{ d.label }}</button>
        </div>
        <el-input v-model="banReason" class="ban-input" maxlength="60" show-word-limit
                  placeholder="请填写封禁原因（必填）" />
      </div>
      <div v-else class="dlg-body">
        <div class="cf-title">请再次确认封禁信息</div>
        <div class="cf-line"><span>账号</span><b>{{ banTarget?.username }}</b></div>
        <div class="cf-line"><span>昵称</span><b>{{ banTarget?.nickname }}</b></div>
        <div class="cf-line"><span>时长</span><b>{{ banDays }} 天（到期自动解封）</b></div>
        <div class="cf-line"><span>原因</span><b>{{ banReason }}</b></div>
        <div class="cf-warn">封禁后该玩家立即无法登录，操作会记录在案。</div>
      </div>
      <template #footer>
        <button v-if="banStep === 1" class="btn-main-sm" @click="toBanConfirm">下一步</button>
        <template v-else>
          <button class="btn-op" @click="banStep = 1">返回修改</button>
          <button class="btn-danger-solid" @click="doBan">确认封禁</button>
        </template>
      </template>
    </el-dialog>

    <!-- 调整金币 -->
    <el-dialog v-model="coinsOpen" title="🪙 调整金币" width="360px" center>
      <div class="dlg-body">
        <p class="dlg-tip">「{{ editTarget?.nickname }}」当前金币 🪙 {{ editTarget?.balance }}</p>
        <el-input-number v-model="coinsVal" :min="0" :max="99999999" :step="100" style="width: 100%" />
      </div>
      <template #footer>
        <button class="btn-main-sm" @click="doCoins">确认修改</button>
      </template>
    </el-dialog>

    <!-- 调整天梯分 -->
    <el-dialog v-model="ratingOpen" title="🏆 调整天梯分" width="360px" center>
      <div class="dlg-body">
        <p class="dlg-tip">「{{ editTarget?.nickname }}」当前天梯分 {{ editTarget?.rating }}</p>
        <el-input-number v-model="ratingVal" :min="0" :max="99999" :step="25" style="width: 100%" />
      </div>
      <template #footer>
        <button class="btn-main-sm" @click="doRating">确认修改</button>
      </template>
    </el-dialog>

    <!-- 查看密码(明文) -->
    <el-dialog v-model="viewPwdOpen" title="👁 查看玩家密码" width="420px" center>
      <div class="dlg-body">
        <p class="dlg-tip">账号 <b class="hl">{{ viewPwdTarget?.username }}</b>
          （{{ viewPwdTarget?.nickname }}）的当前密码</p>
        <div v-if="viewPwdLoading" class="pwd-result"><div class="pwd-label">读取中…</div></div>
        <div v-else-if="viewPwdVal" class="pwd-result">
          <div class="pwd-label">当前密码</div>
          <div class="pwd-box">
            <span class="pwd-text">{{ viewPwdVal }}</span>
            <button class="btn-copy" @click="copyText(viewPwdVal)">复制</button>
          </div>
        </div>
        <p v-else class="pwd-tip warn">{{ viewPwdTip || '读取失败' }}</p>
        <p class="pwd-tip">⚠️ 密码仅管理员可见，请勿外泄</p>
      </div>
      <template #footer>
        <button class="btn-main-sm" @click="viewPwdOpen = false">关 闭</button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="resetPwdOpen" title="🔑 重置玩家密码" width="400px" center>
      <div class="dlg-body">
        <p class="dlg-tip">将为「{{ resetPwdTarget?.nickname }}」生成新密码</p>
        <div v-if="resetPwdVal" class="pwd-result">
          <div class="pwd-label">新密码</div>
          <div class="pwd-box">
            <span class="pwd-text">{{ resetPwdVal }}</span>
            <button class="btn-copy" @click="copyPwd">复制</button>
          </div>
          <p class="pwd-tip">请将此密码转告玩家，玩家登录后可自行修改</p>
        </div>
      </div>
      <template #footer>
        <button v-if="!resetPwdVal" class="btn-main-sm" @click="doResetPwd">确认重置</button>
        <button v-else class="btn-main-sm" @click="resetPwdOpen = false">关闭</button>
      </template>
    </el-dialog>

    <!-- 注销账号(级联删除, 需输入账号名确认) -->
    <el-dialog v-model="delOpen" title="⚠ 注销玩家账号" width="440px" center>
      <div class="dlg-body">
        <p class="dlg-tip">
          将永久删除账号 <b class="hl">{{ delTarget?.username }}</b>，
          该玩家的<b class="danger">卡牌、卡组、皮肤、抽卡记录、充值流水、对局记录</b>会一并清除，<b class="danger">不可恢复</b>。
        </p>
        <div class="del-stat" v-if="delStat">
          <span>卡牌 {{ delStat.cards }}</span>
          <span>卡组 {{ delStat.decks }}</span>
          <span>皮肤 {{ delStat.skins }}</span>
          <span>装配 {{ delStat.operatorSkins }}</span>
          <span>抽卡 {{ delStat.gachaLogs }}</span>
          <span>流水 {{ delStat.rechargeLogs }}</span>
          <span>对局 {{ delStat.matches }}</span>
        </div>
        <p class="dlg-tip">确认请输入账号名：<b class="hl">{{ delTarget?.username }}</b></p>
        <el-input v-model="delConfirm" :placeholder="delTarget?.username" />
      </div>
      <template #footer>
        <button class="btn-plain-sm" @click="delOpen = false">取 消</button>
        <button class="btn-danger-sm" :disabled="delConfirm !== delTarget?.username" @click="doDelete">确 认 注 销</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/request'

const router = useRouter()

/** 退出管理端: 回到玩家大厅, 不登出(管理员本身是前端用户) */
function exitAdmin() {
  router.push('/lobby')
}

const tab = ref('users')
const stats = ref({})
const trend = ref({ days: [], newUsers: [], matches: [], coinSum: [] })

const users = ref([])
const userKw = ref('')
const userStatus = ref('all')
const userPage = ref(1)
const userSize = 10
const userTotal = ref(0)

const records = ref([])
const nameMap = ref({})
const recordPage = ref(1)
const recordSize = 10
const recordTotal = ref(0)

const recharges = ref([])
const rechargePage = ref(1)
const rechargeSize = 10
const rechargeTotal = ref(0)

const detailOpen = ref(false)
const detail = ref(null)

const banOpen = ref(false)
const banTarget = ref(null)
const banDays = ref(3)
const banStep = ref(1)
const banReason = ref('')
const durations = [
  { days: 3, label: '3 天' },
  { days: 7, label: '7 天' },
  { days: 30, label: '30 天' },
  { days: 365, label: '1 年' },
  { days: 3650, label: '10 年' }
]
const coinsOpen = ref(false)
const ratingOpen = ref(false)
const editTarget = ref(null)
const coinsVal = ref(0)
const ratingVal = ref(1000)

const fmt = (t) => t ? String(t).slice(0, 16).replace('T', ' ') : '-'
/** 封禁到期日只取 月-日 时:分, 避免窄列换行 */
const fmtShort = (t) => t ? String(t).slice(5, 16).replace('T', ' ') : '-'
const sum = (arr) => (arr || []).reduce((a, b) => a + b, 0)

/** 迷你折线: 返回 polyline 点串 / 面积路径 / 末点坐标 */
function spark(arr) {
  const w = 150, h = 34
  const data = arr && arr.length ? arr : [0]
  const max = Math.max(...data, 1)
  const step = data.length > 1 ? w / (data.length - 1) : 0
  const pts = data.map((v, i) => [
    +(i * step).toFixed(1),
    +(h - 3 - (v / max) * (h - 8)).toFixed(1)
  ])
  return {
    line: pts.map(p => p.join(',')).join(' '),
    area: 'M0,' + h + ' ' + pts.map(p => 'L' + p[0] + ',' + p[1]).join(' ') + ' L' + w + ',' + h + ' Z',
    last: pts[pts.length - 1]
  }
}
const sparkUsers = computed(() => spark(trend.value.newUsers))
const sparkMatches = computed(() => spark(trend.value.matches))
const sparkCoins = computed(() => spark(trend.value.coinSum))

async function loadUsers() {
  const r = await http.get('/admin/users', {
    params: { keyword: userKw.value, status: userStatus.value, page: userPage.value, size: userSize }
  })
  users.value = r.list
  userTotal.value = r.total
}

async function loadRecords() {
  const r = await http.get('/admin/records', { params: { page: recordPage.value, size: recordSize } })
  records.value = r.records
  nameMap.value = r.nameMap
  recordTotal.value = r.total
}

async function loadRecharges() {
  const r = await http.get('/admin/recharges', { params: { page: rechargePage.value, size: rechargeSize } })
  recharges.value = r.list
  rechargeTotal.value = r.total
}

/* ===== 验证码查询(演示环境无短信网关时代替"收短信") ===== */
const smsPhone = ref('')
const smsResult = ref(null)
async function querySms() {
  if (!/^1[3-9]\d{9}$/.test(smsPhone.value)) return ElMessage.error('请输入 11 位手机号')
  try {
    smsResult.value = await http.get('/admin/sms-trace', { params: { phone: smsPhone.value } })
  } catch (e) {
    smsResult.value = null
  }
}

async function loadTrend() {
  trend.value = await http.get('/admin/stats/trend')
}

async function loadAll() {
  stats.value = await http.get('/admin/stats')
  await Promise.all([loadTrend(), loadUsers(), loadRecords(), loadRecharges()])
}

function searchUsers() {
  userPage.value = 1
  loadUsers()
}

async function openDetail(row) {
  detail.value = null
  detailOpen.value = true
  detail.value = await http.get('/admin/users/' + row.id + '/detail')
}

const otherName = (r) => nameMapOfDetail.value[r.player1Id === detail.value.user.id ? r.player2Id : r.player1Id] || '未知'
const nameMapOfDetail = computed(() => detail.value?.nameMap || {})
const resultText = (r) => r.winnerId === null ? '平' : (r.winnerId === detail.value.user.id ? '胜' : '负')
const resultClass = (r) => r.winnerId === null ? 'draw' : (r.winnerId === detail.value.user.id ? 'win' : 'lose')

function openBan(row) {
  banTarget.value = row
  banDays.value = 3
  banReason.value = ''
  banStep.value = 1
  banOpen.value = true
}
function toBanConfirm() {
  if (!banReason.value.trim()) {
    alert('请填写封禁原因')
    return
  }
  banReason.value = banReason.value.trim()
  banStep.value = 2
}
async function doBan() {
  await http.post('/admin/ban', {
    id: banTarget.value.id, days: banDays.value, reason: banReason.value
  })
  banOpen.value = false
  await loadUsers()
  if (detailOpen.value && detail.value?.user.id === banTarget.value.id) {
    detail.value = await http.get('/admin/users/' + banTarget.value.id + '/detail')
  }
}
async function unban(row) {
  await http.post('/admin/unban', { id: row.id })
  await loadUsers()
  if (detailOpen.value && detail.value?.user.id === row.id) {
    detail.value = await http.get('/admin/users/' + row.id + '/detail')
  }
}

function openCoins(row) { editTarget.value = row; coinsVal.value = row.balance; coinsOpen.value = true }
async function doCoins() {
  await http.post('/admin/coins', { id: editTarget.value.id, amount: coinsVal.value })
  coinsOpen.value = false
  await loadUsers()
}
function openRating(row) { editTarget.value = row; ratingVal.value = row.rating; ratingOpen.value = true }
async function doRating() {
  await http.post('/admin/rating', { id: editTarget.value.id, rating: ratingVal.value })
  ratingOpen.value = false
  await loadUsers()
}

/* ===== 查看密码(明文) ===== */
const viewPwdOpen = ref(false)
const viewPwdTarget = ref(null)
const viewPwdVal = ref('')
const viewPwdTip = ref('')
const viewPwdLoading = ref(false)
async function openViewPwd(row) {
  viewPwdTarget.value = row
  viewPwdVal.value = ''
  viewPwdTip.value = ''
  viewPwdLoading.value = true
  viewPwdOpen.value = true
  try {
    const r = await http.post('/admin/user-password', { id: row.id })
    viewPwdVal.value = r.password || ''
    viewPwdTip.value = r.tip || ''
  } catch (e) { viewPwdTip.value = '读取失败，请稍后重试' }
  viewPwdLoading.value = false
}

const resetPwdOpen = ref(false)
const resetPwdTarget = ref(null)
const resetPwdVal = ref('')
function openResetPwd(row) {
  resetPwdTarget.value = row
  resetPwdVal.value = ''
  resetPwdOpen.value = true
}
async function doResetPwd() {
  const r = await http.post('/admin/reset-password', { id: resetPwdTarget.value.id })
  resetPwdVal.value = r.password
}
/* ===== 注销账号(级联删除关联数据) ===== */
const delOpen = ref(false)
const delTarget = ref(null)
const delConfirm = ref('')
const delStat = ref(null)
function openDelete(row) {
  delTarget.value = row
  delConfirm.value = ''
  delStat.value = null
  delOpen.value = true
}
async function doDelete() {
  if (delConfirm.value !== delTarget.value?.username) return ElMessage.error('账号名不一致')
  if (!confirm(`最后确认：注销「${delTarget.value.username}」及其全部数据？此操作不可恢复！`)) return
  try {
    const r = await http.post('/admin/delete-user', {
      id: delTarget.value.id,
      username: delConfirm.value
    })
    delStat.value = r
    ElMessage.success(`已注销「${r.username}」：卡牌 ${r.cards} · 卡组 ${r.decks} · 流水 ${r.rechargeLogs} · 对局 ${r.matches}`)
    delOpen.value = false
    await loadUsers()
  } catch (e) { console.warn('delete user failed:', e) }
}

/** 复制文本(查看密码 / 重置密码共用) */
function copyText(txt) {
  if (!txt) return
  navigator.clipboard.writeText(txt).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    const ta = document.createElement('textarea')
    ta.value = txt
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('已复制到剪贴板')
  })
}
function copyPwd() { copyText(resetPwdVal.value) }

onMounted(loadAll)
</script>

<style scoped>
.admin { position: relative; max-width: 1180px; margin: 0 auto; padding: 20px; }

/* 独立管理端背景: 布局与游戏区分, 配色沿用前端的钢板暗色 + 琥珀主色 */
.admin::before {
  content: "";
  position: fixed;
  inset: 0;
  background-color: #0e1013;
  background-image:
    radial-gradient(circle at 15% -8%, rgba(240, 165, 42, 0.10), transparent 55%),
    radial-gradient(circle at 88% 108%, rgba(79, 214, 230, 0.05), transparent 50%),
    repeating-linear-gradient(0deg, rgba(240, 165, 42, 0.035) 0 1px, transparent 1px 44px),
    repeating-linear-gradient(90deg, rgba(240, 165, 42, 0.035) 0 1px, transparent 1px 44px);
  z-index: -1;
  pointer-events: none;
}

.toolbar {
  display: grid;
  grid-template-columns: 140px 1fr 140px;
  align-items: center;
  padding: 14px 22px;
  margin-bottom: 16px;
}
.tl-left { text-align: left; }
.tl-right { text-align: right; }
.btn-tb { padding: 6px 14px; font-size: 13px; cursor: pointer; }
.page-title { font-size: 22px; letter-spacing: 8px; }

/* 统计卡 */
.stat-row { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 16px; margin-bottom: 20px; }
.stat-card {
  position: relative;
  padding: 16px 0 12px;
  text-align: center;
  background: linear-gradient(180deg, rgba(30, 35, 41, 0.94), rgba(14, 17, 21, 0.94));
  border: 1px solid var(--line);
  border-left: 3px solid var(--gold);
  border-radius: 2px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.55);
}
.stat-num { display: block; font-size: 24px; font-weight: 700; color: var(--gold-light); }
.stat-label { display: block; margin-top: 4px; font-size: 12px; color: var(--text-dim); letter-spacing: 3px; }
.spark { display: block; width: 150px; height: 34px; margin: 8px auto 0; }
.spark-label { display: block; margin-top: 4px; font-size: 11px; color: var(--text-mute); letter-spacing: 1px; }

/* 表格工具条 */
.tab-tools { display: flex; align-items: center; gap: 10px; padding: 4px 0 12px; flex-wrap: wrap; }
/* === 验证码查询 === */
.tool-tip { font-size: 11px; color: var(--text-mute); }
.sms-box {
  margin-top: 6px;
  padding: 14px 16px;
  background: rgba(11, 14, 17, 0.7);
  border: 1px solid var(--line);
  border-left: 3px solid #6fd8f0;
  border-radius: 2px;
}
.sms-phone { font-size: 15px; font-weight: 700; color: #a4eef6; margin-bottom: 10px; letter-spacing: 2px; }
.sms-list { display: flex; flex-direction: column; gap: 8px; }
.sms-item {
  display: flex; align-items: center; gap: 14px;
  padding: 8px 12px;
  background: rgba(0, 0, 0, 0.35);
  border: 1px solid var(--line-soft);
  border-radius: 3px;
  font-size: 13px;
}
.si-time { color: var(--text-mute); font-size: 12px; min-width: 118px; }
.si-code {
  color: #ffc45c; font-weight: 800; letter-spacing: 3px; font-size: 15px;
  text-shadow: 0 0 8px rgba(240, 165, 42, 0.4);
}
.si-scene { color: var(--text-dim); font-size: 11px; }
.sms-empty { font-size: 12px; color: var(--text-mute); padding: 6px 0; }
.btn-sm { padding: 7px 18px; font-size: 13px; }
.tools-total { margin-left: auto; font-size: 12px; color: var(--text-dim); letter-spacing: 1px; }

/* 分页 */
.pager { display: flex; justify-content: flex-end; padding: 12px 0 4px; }
.pager :deep(.el-pagination) { --el-pagination-bg-color: rgba(20, 24, 29, 0.9); }
.pager :deep(.el-pager li) {
  background: rgba(20, 24, 29, 0.9) !important;
  color: #c3cad3 !important;
  border: 1px solid var(--line);
  margin: 0 3px;
}
.pager :deep(.el-pager li.is-active) {
  background: linear-gradient(180deg, #f2ad36, #b8781a) !important;
  color: #100d07 !important;
  border-color: #7a5416;
}
.pager :deep(.btn-prev), .pager :deep(.btn-next) {
  background: rgba(20, 24, 29, 0.9) !important;
  color: #c3cad3 !important;
  border: 1px solid var(--line);
}
.pager :deep(.btn-prev:disabled), .pager :deep(.btn-next:disabled) { color: #4c545e !important; }

/* 标签 */
.role-tag { font-size: 12px; color: #9ec7f0; }
.role-tag.r-admin { color: var(--gold-light); font-weight: 700; }
.win-tag { color: #7ed957; font-weight: 600; }
.draw-tag { color: var(--text-dim); }

/* 状态与操作 */
.status-tag { font-size: 12px; font-weight: 600; }
.status-tag.normal { color: #7ed957; }
.status-tag.banned { color: var(--blood); }
.ban-until { margin-top: 3px; font-size: 11px; color: #b98a8a; }
.ban-reason {
  margin-top: 2px;
  font-size: 11px;
  color: #8d6a6a;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.btn-op, .btn-ban, .btn-unban {
  padding: 3px 9px;
  font-size: 12px;
  border-radius: 2px;
  cursor: pointer;
  transition: all 0.15s;
  margin: 2px 4px 2px 0;
  background: transparent;
}
.op-btns { display: flex; flex-wrap: wrap; align-items: center; }
.btn-op { border: 1px solid #3a424d; color: #c3cad3; }
.btn-op:hover { border-color: var(--gold); color: var(--gold-light); background: rgba(240, 165, 42, 0.1); }
.btn-ban { border: 1px solid rgba(196, 58, 58, 0.6); color: #e88a8a; }
.btn-ban:hover { background: rgba(196, 58, 58, 0.25); color: #ffb3b3; }
.btn-unban { border: 1px solid rgba(126, 217, 87, 0.5); color: #9fe07a; }
.btn-unban:hover { background: rgba(126, 217, 87, 0.18); }
.op-none { color: var(--text-mute); font-size: 12px; }
.btn-del { border: 1px solid rgba(196, 58, 58, 0.75); color: #ff8a8a; background: rgba(196, 58, 58, 0.12); }
.btn-del:hover { background: rgba(196, 58, 58, 0.3); color: #fff; }

/* 弹窗 */
.dlg-body { padding: 4px 0 8px; }
.dlg-tip { margin: 0 0 16px; font-size: 13px; color: var(--text-dim); text-align: center; line-height: 1.9; }
.dlg-tip .hl { color: var(--gold-light); letter-spacing: 1px; }
.dlg-tip .danger { color: #ff9a9a; }
.del-stat {
  display: flex; flex-wrap: wrap; gap: 8px; justify-content: center;
  margin: 0 0 16px; padding: 10px;
  background: rgba(196, 58, 58, 0.08);
  border: 1px solid rgba(196, 58, 58, 0.25);
  border-radius: 4px;
  font-size: 12px; color: #e88a8a;
}
.btn-plain-sm, .btn-danger-sm {
  padding: 8px 20px; border-radius: 3px; font-size: 13px; letter-spacing: 2px; cursor: pointer;
}
.btn-plain-sm { background: transparent; border: 1px solid #3a424d; color: #c3cad3; }
.btn-plain-sm:hover { border-color: var(--gold); color: var(--gold-light); }
.btn-danger-sm {
  background: linear-gradient(180deg, #c93b3b, #8f2020);
  border: 1px solid #6d1414; color: #fff; font-weight: 700;
}
.btn-danger-sm:hover:not(:disabled) { filter: brightness(1.12); }
.btn-danger-sm:disabled { opacity: 0.4; cursor: not-allowed; }
.pwd-result { margin-top: 12px; }
.pwd-label { font-size: 12px; color: var(--text-mute); margin-bottom: 6px; }
.pwd-box {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px;
  background: rgba(9, 11, 14, 0.9);
  border: 1px solid var(--gold);
  border-radius: 3px;
}
.pwd-text { font-size: 20px; font-weight: 700; color: var(--gold-light); letter-spacing: 3px; font-family: var(--font-num); }
.btn-copy {
  padding: 5px 14px; font-size: 12px; cursor: pointer;
  background: var(--gold); color: #1a1005; border: none; border-radius: 2px; font-weight: 600;
}
.btn-copy:hover { background: var(--gold-light); }
.pwd-tip { margin: 12px 0 0; font-size: 12px; color: var(--text-mute); text-align: center; }
.pwd-tip.warn { color: #e6a23c; }
/* 「查看密码」按钮用青色区分于其它操作 */
.btn-op.pwd { border-color: #2f6b78; color: #6fd8f0; }
.btn-op.pwd:hover { border-color: #6fd8f0; color: #6fd8f0; background: rgba(111, 216, 240, 0.12); }
.dur-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; }
.dur-opt {
  padding: 12px 0;
  background: rgba(11, 14, 17, 0.85);
  border: 1px solid var(--line);
  border-radius: 2px;
  color: #d9dee5;
  font-size: 13px;
  letter-spacing: 1px;
  cursor: pointer;
  transition: all 0.15s;
}
.dur-opt:hover { border-color: var(--gold); }
.dur-opt.on {
  border-color: var(--blood);
  background: linear-gradient(160deg, rgba(226, 85, 79, 0.22), rgba(11, 14, 17, 0.9));
  color: #ffc9c6;
  box-shadow: 0 0 12px rgba(226, 85, 79, 0.3);
}
.ban-input { margin-top: 16px; }
.cf-title { margin-bottom: 14px; font-size: 14px; color: var(--gold-light); letter-spacing: 2px; text-align: center; }
.cf-line { display: flex; justify-content: space-between; padding: 7px 4px; font-size: 13px; border-bottom: 1px dashed var(--line-soft); }
.cf-line span { color: var(--text-dim); }
.cf-line b { color: var(--text); font-weight: 600; max-width: 240px; text-align: right; word-break: break-all; }
.cf-warn { margin-top: 14px; font-size: 12px; color: #c98a8a; text-align: center; }
.btn-danger-solid {
  padding: 8px 26px;
  background: linear-gradient(180deg, #d05550, #8f2f2b);
  border: 1px solid #6d2320;
  border-radius: 2px;
  color: #ffe9e8;
  font-weight: 700;
  letter-spacing: 3px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-danger-solid:hover { filter: brightness(1.12); box-shadow: 0 4px 16px rgba(226, 85, 79, 0.4); }
.btn-main-sm {
  padding: 8px 26px;
  background: linear-gradient(180deg, #f2ad36, #b8781a);
  border: 1px solid #7a5416;
  border-radius: 2px;
  color: #100d07;
  font-weight: 700;
  letter-spacing: 3px;
  cursor: pointer;
  transition: all 0.15s;
  margin-left: 10px;
}
.btn-main-sm:hover { filter: brightness(1.1); box-shadow: 0 4px 16px rgba(240, 165, 42, 0.4); }

/* 用户详情抽屉 */
.dt-loading { padding: 60px 0; text-align: center; color: var(--text-mute); }
.dt { color: var(--text); }
.dt-head { display: flex; align-items: center; gap: 12px; padding-bottom: 14px; border-bottom: 1px solid var(--line); }
.dt-avatar { width: 48px; height: 48px; border-radius: 4px; border: 1px solid var(--line); object-fit: cover; background: #0b0e11; }
.dt-head-info { flex: 1; }
.dt-name { font-size: 16px; font-weight: 700; color: var(--gold-light); letter-spacing: 1px; }
.dt-sub { margin-top: 4px; font-size: 12px; color: var(--text-mute); }
.dt-close { padding: 4px 10px; font-size: 12px; background: transparent; border: 1px solid #3a424d; color: #c3cad3; cursor: pointer; }
.dt-close:hover { border-color: var(--gold); color: var(--gold-light); }
.dt-ban { margin-top: 12px; padding: 8px 10px; font-size: 12px; color: #e8a3a0; background: rgba(226, 85, 79, 0.12); border-left: 2px solid var(--blood); }
.dt-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 14px; }
.dt-item { padding: 8px 10px; background: rgba(20, 24, 29, 0.7); border: 1px solid var(--line-soft); }
.dt-item .k { display: block; font-size: 11px; color: var(--text-dim); letter-spacing: 1px; }
.dt-item .v { display: block; margin-top: 4px; font-size: 13px; color: var(--text); }
.dt-item .v.gold { color: var(--gold-light); font-weight: 700; }
.dt-sec { margin: 18px 0 8px; font-size: 13px; color: var(--gold-light); letter-spacing: 2px; }
.dt-sec-n { margin-left: 4px; font-size: 11px; color: var(--text-mute); }
.dt-empty { padding: 10px 0; font-size: 12px; color: var(--text-mute); text-align: center; }
.dt-row { display: flex; align-items: center; gap: 8px; padding: 8px 6px; font-size: 12px; border-bottom: 1px dashed var(--line-soft); }
.dt-row-main { flex: 1; color: var(--text); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.dt-row-sub { color: var(--text-mute); }
.dt-row-time { color: var(--text-mute); font-size: 11px; }
.dt-row-tag { font-weight: 700; }
.dt-row-tag.win { color: #7ed957; }
.dt-row-tag.lose { color: #e2554f; }
.dt-row-tag.draw { color: var(--text-dim); }
</style>
