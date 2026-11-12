/**
 * 人机对战 / 匹配对战 链路实测
 * 口令不写死在这里, 从 tools/ws-e2e.cjs 的常量里读, 避免命令行出现明文。
 * 运行: node tools/battle-flow-check.mjs
 */
import fs from 'fs'

const BASE = 'http://localhost:8080'
const PWD = fs.readFileSync('tools/ws-e2e.cjs', 'utf8').match(/const PWD = '([^']+)'/)[1]

const j = async (r) => ({ code: r.status, body: await r.json().catch(() => null) })
const post = (p, b, t) => fetch(BASE + p, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: 'Bearer ' + t } : {}) },
  body: b ? JSON.stringify(b) : undefined
}).then(j)
const get = (p, t) => fetch(BASE + p, { headers: t ? { Authorization: 'Bearer ' + t } : {} }).then(j)

const ok = (label, cond, extra = '') => {
  console.log((cond ? '  [通过] ' : '  [失败] ') + label + (extra ? '  ' + extra : ''))
  return cond
}
let failed = 0
const check = (l, c, e) => { if (!ok(l, c, e)) failed++ }

console.log('=== 1. 登录 ===')
const login = await post('/api/auth/login', { username: 'testbot', password: PWD })
const token = login.body?.data?.token
check('测试号登录成功', !!token, login.body?.msg || '')
if (!token) process.exit(1)

console.log('=== 2. 人机对战 ===')
const bot = await post('/api/match/bot?level=normal', null, token)
const roomId = bot.body?.data
check('创建人机房间返回房间号', typeof roomId === 'string' && roomId.length > 0, 'roomId=' + roomId)
if (!roomId) process.exit(1)

const snap = await get(`/api/battle/${roomId}/snapshot`, token)
const s = snap.body?.data
const meP = s?.p1?.userId === 12 ? s.p1 : s?.p2
const foeP = s?.p1?.userId === 12 ? s.p2 : s?.p1
check('快照可读', snap.code === 200 && !!s, s ? `phase=${s.phase} 回合=${s.turnNo}` : '')
check('我方有手牌', (meP?.hand?.length || 0) > 0, `手牌 ${meP?.hand?.length} 张`)
check('我方有牌库', (meP?.deckCount || 0) > 0, `牌库 ${meP?.deckCount} 张`)
check('对手是机器人', (foeP?.userId || 0) < 0, `对手=${foeP?.nickname}`)
check('手牌含卡牌详情字段', !!meP?.hand?.[0]?.name && !!meP?.hand?.[0]?.cost, `样例: ${meP?.hand?.[0]?.name}`)
check('初始血量为 30', meP?.hp === 30 && foeP?.hp === 30, `${meP?.hp} vs ${foeP?.hp}`)

console.log('=== 3. 快照可重复读取(前端进对局会拉两次) ===')
const snap2 = await get(`/api/battle/${roomId}/snapshot`, token)
check('二次拉取仍成功', snap2.code === 200 && !!snap2.body?.data)

console.log('=== 4. 观战列表能看到这局 ===')
const live = await get('/api/battle/live', token)
const mine = (live.body?.data || []).find(x => x.roomId === roomId)
check('人机房出现在观战列表', !!mine, mine ? `${mine.p1} vs ${mine.p2}` : '')

console.log('=== 5. 匹配队列(匹配对战入口) ===')
const join = await post('/api/match/join', null, token)
check('加入匹配队列成功', join.code === 200 && join.body?.code === 0, JSON.stringify(join.body?.data ?? join.body?.msg))
const st = await get('/api/match/status', token)
check('队列状态接口可读', st.code === 200, JSON.stringify(st.body?.data))
const stData = st.body?.data || {}
check('状态显示在队列中', stData.inQueue === true, `inQueue=${stData.inQueue} waited=${stData.waited}s`)
const leave = await post('/api/match/leave', null, token)
check('退出队列成功', leave.code === 200)
const st2 = await get('/api/match/status', token)
check('退出后不在队列', (st2.body?.data || {}).inQueue === false)

console.log('\n结果: ' + (failed ? '失败 ' + failed + ' 项' : '全部通过'))
process.exit(failed ? 1 : 0)
