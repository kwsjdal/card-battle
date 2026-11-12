// 用系统 Edge 无头复现"点人机对战进对局黑屏", 抓 console 错误与页面快照
import { chromium } from 'playwright-core'

const BASE = 'http://localhost:5173'

const browser = await chromium.launch({ channel: 'msedge', headless: true })
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } })

const logs = []
page.on('console', (m) => logs.push(`[console.${m.type()}] ${m.text()}`))
page.on('pageerror', (e) => logs.push(`[PAGEERROR] ${e.message}\n${e.stack || ''}`))
page.on('requestfailed', (r) => logs.push(`[REQFAIL] ${r.method()} ${r.url()} :: ${r.failure()?.errorText}`))
page.on('response', async (r) => {
  if (r.status() >= 400) {
    let body = ''
    try { body = (await r.text()).slice(0, 300) } catch {}
    logs.push(`[HTTP ${r.status()}] ${r.request().method()} ${r.url()} :: ${body}`)
  }
})

// 1. 登录(在页面上下文里调真实接口, 拿到 token/user 存进 localStorage —— 与 Login.vue 行为一致)
await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
const loginRes = await page.evaluate(async () => {
  const r = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'testbot', password: '123456' })
  })
  const j = await r.json()
  return j
})
const data = loginRes?.data || loginRes
if (!data?.token || !data?.user) {
  console.log('登录失败:', JSON.stringify(loginRes).slice(0, 400))
  await browser.close(); process.exit(1)
}
await page.evaluate((d) => {
  localStorage.setItem('token', d.token)
  localStorage.setItem('user', JSON.stringify(d.user))
}, data)
console.log('== 登录成功 userId=', data.user.id)

// 2. 进大厅, 点人机对战(或直接调接口建房再跳 /battle —— 与 joinBot 相同路径)
await page.goto(BASE + '/lobby', { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(1500)
const roomId = await page.evaluate(async () => {
  const token = localStorage.getItem('token')
  const r = await fetch('/api/match/bot?level=normal', { method: 'POST', headers: { Authorization: 'Bearer ' + token } })
  const j = await r.json()
  return j.data || j   // axios 拦截器在项目里做的是 data 解包, 原生 fetch 要手动做
})
console.log('== 建房 roomId=', roomId)
await page.evaluate((rid) => localStorage.setItem('roomId', rid), roomId)

// 3. 跳转 /battle, 观察是否黑屏
await page.goto(BASE + '/battle', { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(3500)

const info = await page.evaluate(() => ({
  url: location.href,
  hasBattle: !!document.querySelector('.battle'),
  hasLoading: !!document.querySelector('.battle-loading'),
  loadingText: document.querySelector('.battle-loading')?.innerText || '',
  battleChildren: document.querySelector('.battle') ? document.querySelector('.battle').children.length : 0,
  appHtmlLen: document.getElementById('app')?.innerHTML?.length || 0,
  bodyBg: getComputedStyle(document.body).background.slice(0, 120)
}))
console.log('== 页面状态:', JSON.stringify(info, null, 2))
console.log('== console/错误收集 ==')
for (const l of logs) console.log(l)
await page.screenshot({ path: 'K:/card-battle/tools/repro-battle.png' })
await browser.close()
