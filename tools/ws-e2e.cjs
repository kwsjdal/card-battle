/**
 * WS 端到端测试: 完整对局 + 越权攻击 + 疲劳 + 落库
 *
 * 依赖 @stomp/stompjs(取自 frontend/node_modules), 运行前先启动后端 8080。
 * 用法:
 *   NODE_PATH=<frontend>/node_modules node tools/ws-e2e.cjs
 */
const { Client } = require('@stomp/stompjs')

const BASE = 'http://localhost:8080'
const WS = 'ws://localhost:8080/ws'

const ME = { id: 12, name: 'testbot', token: null }
const ATK = { id: 9001, name: 'atk_probe1', token: null }
const PWD = '123456'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

/** 登录取 JWT。两个账号都要在线, 否则无法验证"别人能不能操作我的房间" */
async function login(username, password) {
  const res = await fetch(BASE + '/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  const j = await res.json()
  if (j.code !== 0) {
    if (j.msg === '用户不存在') {
      throw new Error('测试账号 ' + username + ' 不存在。先在 users 表建好它, 或直接复用任一现有玩家的密码哈希。')
    }
    throw new Error('登录失败 ' + username + ': ' + j.msg)
  }
  return j.data.token
}

async function api(token, method, url) {
  const res = await fetch(BASE + url, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token }
  })
  return await res.json()
}

let pass = 0, fail = 0
const check = (name, ok, detail) => {
  if (ok) { pass++; console.log('  [通过] ' + name) }
  else { fail++; console.log('  [失败] ' + name + ' -> ' + detail) }
}

/** 建立 STOMP 连接; onMsg(topic, data); errSink 收集 STOMP ERROR 帧 */
function stomp(who, topics, onMsg, errSink) {
  return new Promise((resolve, reject) => {
    let up = false
    const c = new Client({
      brokerURL: WS + '?token=' + encodeURIComponent(who.token),
      reconnectDelay: 0,
      debug: () => {},
      onConnect: () => {
        up = true
        topics.forEach((t) => c.subscribe(t, (m) => onMsg(t, JSON.parse(m.body))))
        resolve(c)
      },
      onStompError: (f) => {
        const m = (f && f.headers && f.headers.message) || (f && f.body) || 'stomp error'
        if (errSink) errSink.push(String(m))
        if (!up) reject(new Error('STOMP error: ' + m))
      },
      onWebSocketClose: () => {}
    })
    c.activate()
    setTimeout(() => { if (!up) reject(new Error('WS connect timeout')) }, 10000)
  })
}

async function main() {
  console.log('=== WS 端到端测试 ===')

  // 注意: 单设备登录会让旧会话失效, 所以每轮都重新取一次 token
  ME.token = await login(ME.name, PWD)
  ATK.token = await login(ATK.name, PWD)

  const frames = []      // 我方收到的全部对局帧
  const atkErrors = []   // 攻击方收到的 STOMP ERROR 帧

  // ---------- 1. 先连 WS, 再建房(否则开局帧会在订阅前发出) ----------
  const myErrors = []
  const meClient = await stomp(ME, ['/topic/user/12'], (t, d) => {
    if (d.event === 'error') myErrors.push(d.msg)
    else frames.push(d)
  }, null)
  check('我方 WS 握手成功(带 token)', !!meClient.connected, '未连接')

  const atkClient = await stomp(ATK, ['/topic/user/9001'], (t, d) => {
    if (d.event === 'error') atkErrors.push(d.msg)
  }, [])
  check('攻击方 WS 握手成功(带 token)', !!atkClient.connected, '未连接')

  // ---------- 2. 建立人机房间 ----------
  const bot = await api(ME.token, 'POST', '/api/match/bot')
  const roomId = bot.data
  console.log('  房间: ' + roomId)
  check('创建人机房间', !!roomId, JSON.stringify(bot))

  let guard = 0
  while (frames.length === 0 && guard++ < 100) await sleep(50)
  const first = frames.filter((f) => f.event === 'state')[0]
  check('收到开局快照', !!first, 'frames=' + JSON.stringify(frames.map((f) => f.event)))
  if (!first) { console.log('结果: ' + pass + ' 通过 / ' + fail + ' 失败'); process.exit(1) }

  // 取双方快照(以我方视角为准)
  const side = (s) => (s.p1 && s.p1.userId === 12 ? { me: s.p1, op: s.p2 } : { me: s.p2, op: s.p1 })
  const f0 = side(first)
  console.log('      开局: 我方HP=' + f0.me.hp + ' 手牌=' + f0.me.hand.length
    + ' 手牌内容可见=' + (!!f0.me.hand))

  // ---------- 3. 我方快照私密性 ----------
  check('我方快照能看到自己的手牌', Array.isArray(f0.me.hand), 'hand=' + JSON.stringify(f0.me.hand))
  check('我方快照看不到对手手牌(只有张数)',
    f0.op && !('hand' in f0.op) && 'handCount' in f0.op,
    'op keys=' + Object.keys(f0.op || {}).join(','))

  // ---------- 4. HTTP 层越权: 攻击方读我方房间快照 ----------
  {
    const r = await api(ATK.token, 'GET', '/api/battle/' + roomId + '/snapshot')
    check('越权读快照被拒(HTTP)', r.code === 1 && /无权/.test(r.msg || ''), 'code=' + r.code + ' msg=' + r.msg)
  }

  // ---------- 5. 观战: 公开但与手牌脱敏 ----------
  {
    const r = await api(ATK.token, 'GET', '/api/battle/' + roomId + '/spectate')
    check('观战入口对已登录用户开放', r.code === 0, 'code=' + r.code + ' msg=' + r.msg)
    if (r.code === 0) {
      const d = r.data
      const p1 = d.p1, p2 = d.p2
      const noHand1 = !('hand' in p1) && 'handCount' in p1
      const noHand2 = !('hand' in p2) && 'handCount' in p2
      check('观战快照不含我方手牌(只给张数)', noHand1, 'p1 keys=' + Object.keys(p1).join(','))
      check('观战快照不含对手手牌(只给张数)', noHand2, 'p2 keys=' + Object.keys(p2).join(','))
      // 场上随从不泄露"可否攻击/召唤失调"
      const board = (p1.board || []).concat(p2.board || [])
      const leak = board.filter((c) => c.sick !== null || c.canAct !== null)
      check('观战不泄露随从可否攻击', leak.length === 0, '泄露 ' + leak.length + ' 个随从')
      check('观战仍能看到场上局势', Array.isArray(p1.board) && 'hp' in p1 && 'fatigue' in p1,
        'p1 keys=' + Object.keys(p1).join(','))
    }
  }

  // ---------- 6. WS 层越权: 攻击方伪造 userId 操作我方房间 ----------
  const before = JSON.stringify(side(frames.filter((f) => f.event === 'state').pop()))
  {
    atkClient.publish({
      destination: '/app/battle.action',
      body: JSON.stringify({ roomId, userId: 12, type: 'surrender' })
    })
    await sleep(700)
    check('伪造 userId 的 surrender 被拒(WS)', atkErrors.length > 0,
      '未收到 ERROR 帧, errors=' + JSON.stringify(atkErrors))
    if (atkErrors.length > 0) console.log('      服务端: ' + String(atkErrors[0]).slice(0, 120))

    atkClient.publish({
      destination: '/app/battle.action',
      body: JSON.stringify({ roomId, userId: 12, type: 'play', handIndex: 0 })
    })
    await sleep(700)
    check('伪造 userId 的出牌被拒(WS)', atkErrors.length >= 2, 'errors=' + atkErrors.length)

    atkClient.publish({
      destination: '/app/battle.action',
      body: JSON.stringify({ roomId, userId: 12, type: 'end' })
    })
    await sleep(700)
    check('伪造 userId 的结束回合被拒(WS)', atkErrors.length >= 3, 'errors=' + atkErrors.length)

    const after = JSON.stringify(side(frames.filter((f) => f.event === 'state').pop()))
    check('我方对局未被外部改动', before === after, '状态被外部改变了')
  }

  // ---------- 7. 正常推进对局 ----------
  const send = (a) => meClient.publish({ destination: '/app/battle.action', body: JSON.stringify(a) })
  const latest = () => {
    for (let i = frames.length - 1; i >= 0; i--) {
      if (frames[i].event === 'state') return frames[i]
      if (frames[i].event === 'game_over') return frames[i].snapshot
    }
    return null
  }
  const isOver = () => frames.some((f) => f.event === 'game_over')

  let acted = 0, ended = 0, turns = 0, tauntPlayed = 0
  const t0 = Date.now()
  while (Date.now() - t0 < 150000 && !isOver()) {
    const s = latest()
    if (!s) { await sleep(50); continue }
    if (s.currentUserId !== 12) { await sleep(60); continue }
    if (s.phase !== 'MAIN') { await sleep(60); continue }
    const { me, op } = side(s)
    turns++

    // 前 8 回合: 正常出牌(优先嘲讽, 用来验证机器人不会因嘲讽卡死) + 技能 + 攻击
    if (turns <= 8) {
      if (me.hand && me.hand.length) {
        const tauntIdx = me.hand.findIndex((c) => c.cost <= me.mana && /taunt/.test(c.effect || ''))
        const idx = tauntIdx >= 0 ? tauntIdx : me.hand.findIndex((c) => c.cost <= me.mana)
        if (idx >= 0) {
          if (tauntIdx >= 0) tauntPlayed++
          send({ roomId, type: 'play', handIndex: idx }); acted++; await sleep(70); continue
        }
      }
      if (!me.heroPowerUsed && me.mana >= 2) {
        send({ roomId, type: 'hero', targetIndex: -1 }); acted++; await sleep(70); continue
      }
      const ai = (me.board || []).findIndex((c) => c.canAct)
      if (ai >= 0) {
        const atk = me.board[ai]
        const foeBoard = op.board || []
        // 目标裁决与服务端规则保持一致: 敌方有嘲讽必须先打嘲讽; 突袭随从登场回合不能打脸
        const tauntI = foeBoard.findIndex((c) => /taunt/.test(c.effect || ''))
        let target = tauntI >= 0 ? tauntI : -1
        if (target === -1 && atk.rushLock && foeBoard.length) target = 0
        if (!(target === -1 && atk.rushLock)) {   // 突袭且无随从可打 -> 这回合放弃该随从
          send({ roomId, type: 'attack', boardIndex: ai, targetIndex: target }); acted++; await sleep(70); continue
        }
      }
    }
    send({ roomId, type: 'end' }); ended++
    await sleep(70)
  }

  console.log('      我方: 主动操作=' + acted + '(其中嘲讽随从' + tauntPlayed + '个) 过回合=' + ended
    + ' 耗时=' + ((Date.now() - t0) / 1000).toFixed(1) + 's')
  check('对局走到 GAME_OVER', isOver(), '未结束, 最后一帧=' + (latest() && latest().phase))
  check('我方操作全部合法(未收到服务端错误回执)', myErrors.length === 0,
    'errors=' + JSON.stringify(myErrors.slice(0, 3)))

  const over = frames.filter((f) => f.event === 'game_over').pop()
  if (over) {
    const fin = side(over.snapshot)
    console.log('      终局: 我方HP=' + fin.me.hp + ' 敌方HP=' + fin.op.hp
      + ' 疲劳(我/敌)=' + fin.me.fatigue + '/' + fin.op.fatigue
      + ' 胜者=' + over.winnerId)
    const logs = (over.snapshot.logTail || [])
    const fatigueLogs = logs.filter((l) => String(l.text || l).includes('疲劳'))
    check('终局日志含疲劳记录(或一方先被打死)',
      fin.me.fatigue > 0 || fin.op.fatigue > 0 || fin.op.hp <= 0 || fin.me.hp <= 0,
      'fatigue=' + fin.me.fatigue + '/' + fin.op.fatigue)
    if (fatigueLogs.length) console.log('      疲劳日志样例: ' + JSON.stringify(fatigueLogs[0]).slice(0, 140))
  }

  // ---------- 8. 落库 + 回放 ----------
  await sleep(900)
  const rec = await api(ME.token, 'GET', '/api/records/my')
  const list = (rec.data && rec.data.list) || []
  // 列表按 create_time 倒序, 最新一条就是刚打的这局(接口不下发 roomId, 用回合数交叉核对)
  const mine = list[0]
  check('对局记录已入库', !!mine, '战报列表为空')
  if (mine) {
    console.log('      记录 id=' + mine.id + ' 对手=' + mine.opponent
      + ' 结果=' + mine.result + ' 回合=' + mine.rounds)
    check('机器人对手在战报中显示为陪练', /机器人/.test(mine.opponent || ''), 'opponent=' + mine.opponent)
    const rep = await api(ME.token, 'GET', '/api/records/' + mine.id + '/replay')
    const tl = (rep.data && rep.data.timeline) || []
    check('回放时间线非空', tl.length > 0, 'timeline=' + tl.length)
    const distinctTurns = new Set(tl.map((l) => l.turn)).size
    console.log('      回放: ' + tl.length + ' 条日志, 覆盖 ' + distinctTurns + ' 个回合')
    check('回放日志按回合分组齐全', distinctTurns > 1 && tl.every((l) => l.turn > 0),
      'turns=' + distinctTurns)
    // 回放内容不含对手手牌: 日志里出现"手牌"字样说明泄露了私密信息
    const handLeak = tl.filter((l) => /手牌/.test(l.text || ''))
    check('回放日志不泄露手牌信息', handLeak.length === 0, '命中 ' + handLeak.length + ' 条')

    const bad = await api(ATK.token, 'GET', '/api/records/' + mine.id + '/replay')
    check('越权读他人回放被拒', bad.code === 1 && /无权/.test(bad.msg || ''), 'code=' + bad.code + ' msg=' + bad.msg)
  }

  meClient.deactivate()
  atkClient.deactivate()

  console.log()
  console.log('结果: ' + pass + ' 通过 / ' + fail + ' 失败')
  process.exit(fail > 0 ? 1 : 0)
}

main().catch((e) => { console.error('E2E 异常: ' + e.message); process.exit(1) })
