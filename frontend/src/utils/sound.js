/**
 * 音效模块: 全部用 Web Audio 现场合成, 不引入任何音频文件
 * (避免外部素材版权问题, 也不会有"AI 生成音"的违和感)
 * 开关状态存 localStorage: sfxOn = 1 / 0
 */
let ctx = null
let master = null

function ensure() {
  if (ctx) return ctx
  const AC = window.AudioContext || window.webkitAudioContext
  if (!AC) return null
  ctx = new AC()
  master = ctx.createGain()
  master.gain.value = 0.5
  master.connect(ctx.destination)
  return ctx
}

/** 浏览器要求用户交互后才能出声, 首次点击时调用 */
export function unlock() {
  const c = ensure()
  if (c && c.state === 'suspended') c.resume()
}

export function isOn() {
  return localStorage.getItem('sfxOn') !== '0'
}

export function setOn(on) {
  localStorage.setItem('sfxOn', on ? '1' : '0')
  if (on) unlock()
}

export function toggle() {
  const next = !isOn()
  setOn(next)
  return next
}

/**
 * 单音: 频率从 f0 滑到 f1, dur 秒, type 波形
 */
function tone({ f0, f1, dur = 0.12, type = 'square', vol = 0.22, delay = 0 }) {
  const c = ensure()
  if (!c) return
  const t0 = c.currentTime + delay
  const osc = c.createOscillator()
  const g = c.createGain()
  osc.type = type
  osc.frequency.setValueAtTime(f0, t0)
  if (f1 && f1 !== f0) osc.frequency.exponentialRampToValueAtTime(f1, t0 + dur)
  // 打击感包络: 瞬间起音 + 指数衰减
  g.gain.setValueAtTime(0.0001, t0)
  g.gain.exponentialRampToValueAtTime(vol, t0 + 0.012)
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur)
  osc.connect(g)
  g.connect(master)
  osc.start(t0)
  osc.stop(t0 + dur + 0.02)
}

/** 噪声(用于"受击/翻牌"的沙沙声) */
function noise({ dur = 0.18, vol = 0.18, delay = 0, hp = 1200 }) {
  const c = ensure()
  if (!c) return
  const t0 = c.currentTime + delay
  const len = Math.floor(c.sampleRate * dur)
  const buf = c.createBuffer(1, len, c.sampleRate)
  const data = buf.getChannelData(0)
  for (let i = 0; i < len; i++) data[i] = (Math.random() * 2 - 1) * (1 - i / len)
  const src = c.createBufferSource()
  src.buffer = buf
  const filter = c.createBiquadFilter()
  filter.type = 'lowpass'
  filter.frequency.value = hp
  const g = c.createGain()
  g.gain.setValueAtTime(vol, t0)
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur)
  src.connect(filter); filter.connect(g); g.connect(master)
  src.start(t0)
}

const PRESETS = {
  // UI: 短促的"咔"
  click: () => tone({ f0: 880, f1: 660, dur: 0.07, type: 'square', vol: 0.13 }),
  // 卡牌: 翻牌沙沙 + 轻响
  card:  () => { noise({ dur: 0.12, vol: 0.12, hp: 2600 }); tone({ f0: 520, f1: 780, dur: 0.1, type: 'triangle', vol: 0.13, delay: 0.03 }) },
  // 抽卡: 上行三音
  pull:  () => { tone({ f0: 523, dur: 0.1, type: 'triangle', vol: 0.16 }); tone({ f0: 659, dur: 0.1, type: 'triangle', vol: 0.16, delay: 0.09 }); tone({ f0: 784, dur: 0.16, type: 'triangle', vol: 0.16, delay: 0.18 }) },
  // 出牌: 落桌
  play:  () => { tone({ f0: 300, f1: 160, dur: 0.14, type: 'sawtooth', vol: 0.16 }); noise({ dur: 0.1, vol: 0.1, hp: 900 }) },
  // 攻击: 金属撞击
  attack: () => { noise({ dur: 0.16, vol: 0.2, hp: 3200 }); tone({ f0: 180, f1: 90, dur: 0.16, type: 'square', vol: 0.16 }) },
  // 英雄技能: 能量扫频
  hero: () => tone({ f0: 220, f1: 1200, dur: 0.28, type: 'sawtooth', vol: 0.13 }),
  // 受伤
  hurt: () => { tone({ f0: 420, f1: 120, dur: 0.18, type: 'sawtooth', vol: 0.16 }); noise({ dur: 0.14, vol: 0.12, hp: 800 }) },
  // 匹配到对手: 三连上升
  match: () => { tone({ f0: 392, dur: 0.12, type: 'square', vol: 0.14 }); tone({ f0: 523, dur: 0.12, type: 'square', vol: 0.14, delay: 0.13 }); tone({ f0: 659, dur: 0.2, type: 'square', vol: 0.14, delay: 0.26 }) },
  // 胜利: 大三和弦琶音
  win: () => { [523, 659, 784, 1047].forEach((f, i) => tone({ f0: f, dur: 0.34, type: 'triangle', vol: 0.16, delay: i * 0.11 })) },
  // 失败: 下行小三度
  lose: () => { [523, 440, 349, 262].forEach((f, i) => tone({ f0: f, dur: 0.34, type: 'triangle', vol: 0.15, delay: i * 0.14 })) },
  // 提示/升级
  ding: () => { tone({ f0: 1047, dur: 0.12, type: 'sine', vol: 0.16 }); tone({ f0: 1568, dur: 0.16, type: 'sine', vol: 0.12, delay: 0.08 }) },
  // 抽到高稀有度
  rare: () => { [659, 880, 1319].forEach((f, i) => tone({ f0: f, dur: 0.3, type: 'sine', vol: 0.16, delay: i * 0.1 })) },
  // 错误
  error: () => tone({ f0: 200, f1: 140, dur: 0.22, type: 'square', vol: 0.14 })
}

/** 播放音效: play('attack') */
export function play(name) {
  if (!isOn()) return
  try {
    const fn = PRESETS[name]
    if (fn) fn()
  } catch (e) { /* 浏览器不支持音频时静默忽略 */ }
}

/** 点击类交互的统一入口(按钮点下去就响一下) */
export function click() { play('click') }

/* ==========================================================================
 * 背景音乐(BGM) —— 同样是纯 Web Audio 现场合成, 零音频文件
 * 空灵偏史诗的氛围铺底: 低频 pad + 低音贝斯 + 缓慢五声琶音 + 生成式混响
 * 调式取 A 小调五声(A C D E G), 和声走向 Am - F - C - G, 每小节 8 秒循环
 * 开关状态存 localStorage: bgmOn = 1 / 0 (默认开)
 * ========================================================================== */
const BGM_KEY = 'bgmOn'
const BGM_LEVEL = 0.7                 // BGM 淡入后的最终音量(刻意压低, 不盖过打击音效)
const BGM_BAR = 8                     // 每小节秒数
let bgmGain = null                    // BGM 总音量(独立于音效, 便于淡入淡出)
let reverbSend = null                 // 混响送出
let bgmTimer = null
let bgmRunning = false
let bgmNextBar = 0                    // 下一个小节的绝对开始时间
let bgmBarIdx = 0

/** 4 个和弦: bass 低音 / pad 铺底三音 / arp 琶音音阶(高八度) */
const BGM_CHORDS = [
  { bass: 110.00, pad: [220.00, 261.63, 329.63], arp: [440.00, 523.25, 659.25, 880.00] }, // Am
  { bass:  87.31, pad: [174.61, 220.00, 261.63], arp: [349.23, 440.00, 523.25, 698.46] }, // F
  { bass: 130.81, pad: [196.00, 261.63, 329.63], arp: [392.00, 523.25, 659.25, 783.99] }, // C
  { bass:  98.00, pad: [196.00, 246.94, 293.66], arp: [392.00, 493.88, 587.33, 783.99] }  // G
]
// 16 个八分音符的拨弦节奏(1=拨), 疏密交替, 留白多才不吵
const ARP_STEPS = [1, 0, 1, 0, 1, 1, 0, 1, 1, 0, 1, 0, 1, 0, 1, 1]

/** 生成一段噪声衰减作为混响脉冲响应(不依赖任何音频文件) */
function makeReverb(c, sec = 2.6, decay = 2.4) {
  const len = Math.floor(c.sampleRate * sec)
  const buf = c.createBuffer(2, len, c.sampleRate)
  for (let ch = 0; ch < 2; ch++) {
    const d = buf.getChannelData(ch)
    for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, decay)
  }
  const cv = c.createConvolver()
  cv.buffer = buf
  return cv
}

/** 铺底音: 三个微失谐正弦叠加, 慢起慢落 */
function padNote(freq, t0, dur, vol) {
  const c = ctx
  const g = c.createGain()
  const f = c.createBiquadFilter()
  f.type = 'lowpass'
  f.frequency.value = 1100
  f.Q.value = 0.5
  g.gain.setValueAtTime(0.0001, t0)
  g.gain.linearRampToValueAtTime(vol, t0 + 1.8)
  g.gain.setValueAtTime(vol, t0 + dur - 1.8)
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur)
  for (const det of [-7, 0, 6]) {
    const o = c.createOscillator()
    o.type = 'sine'
    o.frequency.value = freq
    o.detune.value = det
    o.connect(g)
    o.start(t0)
    o.stop(t0 + dur + 0.05)
  }
  g.connect(f)
  f.connect(bgmGain)
  if (reverbSend) f.connect(reverbSend)
}

/** 低音: 正弦 + 一点三角泛音, 稳住底盘 */
function bassNote(freq, t0, dur, vol) {
  const c = ctx
  const g = c.createGain()
  const o = c.createOscillator()
  o.type = 'sine'
  o.frequency.value = freq
  const o2 = c.createOscillator()
  o2.type = 'triangle'
  o2.frequency.value = freq * 2
  const g2 = c.createGain()
  g2.gain.value = 0.1
  g.gain.setValueAtTime(0.0001, t0)
  g.gain.linearRampToValueAtTime(vol, t0 + 0.9)
  g.gain.setValueAtTime(vol, t0 + dur - 1.6)
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur)
  o.connect(g)
  o2.connect(g2)
  g2.connect(g)
  g.connect(bgmGain)
  o.start(t0);  o.stop(t0 + dur + 0.05)
  o2.start(t0); o2.stop(t0 + dur + 0.05)
}

/** 琶音: 拨弦质感, 低通从亮到暗 */
function arpNote(freq, t0, vol) {
  const c = ctx
  const o = c.createOscillator()
  o.type = 'triangle'
  o.frequency.value = freq
  const o2 = c.createOscillator()
  o2.type = 'sine'
  o2.frequency.value = freq * 2.004
  const g2 = c.createGain()
  g2.gain.value = 0.22
  const g = c.createGain()
  const f = c.createBiquadFilter()
  f.type = 'lowpass'
  f.frequency.setValueAtTime(3400, t0)
  f.frequency.exponentialRampToValueAtTime(700, t0 + 1.3)
  g.gain.setValueAtTime(0.0001, t0)
  g.gain.exponentialRampToValueAtTime(vol, t0 + 0.02)
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + 1.7)
  o.connect(g)
  o2.connect(g2)
  g2.connect(g)
  g.connect(f)
  f.connect(bgmGain)
  if (reverbSend) f.connect(reverbSend)
  o.start(t0);  o.stop(t0 + 1.8)
  o2.start(t0); o2.stop(t0 + 1.8)
}

/** 排一个小节的所有音符 */
function bgmScheduleBar(barIdx, t0) {
  const ch = BGM_CHORDS[barIdx % BGM_CHORDS.length]
  bassNote(ch.bass, t0, BGM_BAR + 0.6, 0.085)
  ch.pad.forEach((f, i) => padNote(f, t0 + i * 0.16, BGM_BAR + 1.0, 0.05))
  const stepDur = BGM_BAR / ARP_STEPS.length
  ARP_STEPS.forEach((on, i) => {
    if (!on) return
    const note = ch.arp[(i + barIdx) % ch.arp.length]
    arpNote(note, t0 + i * stepDur, 0.055)
  })
}

/** 调度循环: 提前 1.2 秒把下一个小节排进去 */
function bgmLoop() {
  if (!bgmRunning || !ctx) return
  // 浏览器还没解锁音频时不要排期, 否则 resume 瞬间会一次性爆音
  if (ctx.state !== 'running') {
    bgmNextBar = ctx.currentTime + 0.3
    return
  }
  const horizon = ctx.currentTime + 1.2
  while (bgmNextBar < horizon) {
    bgmScheduleBar(bgmBarIdx, bgmNextBar)
    bgmBarIdx++
    bgmNextBar += BGM_BAR
  }
}

export function isBgmOn() {
  return localStorage.getItem(BGM_KEY) !== '0'
}

/** 记录开关偏好; 真正的启停由调用方(进入/离开对战页)决定 */
export function setBgmOn(on) {
  localStorage.setItem(BGM_KEY, on ? '1' : '0')
}

export function startBgm() {
  // 音频只是氛围, 任何情况下都不许把页面逻辑带崩(创建 AudioContext 也可能抛异常)
  try {
    const c = ensure()
    if (!c) return
    if (c.state === 'suspended') c.resume()
    if (bgmRunning) return
    if (!bgmGain) {
      bgmGain = c.createGain()
      bgmGain.gain.value = 0
      bgmGain.connect(master)
      reverbSend = c.createGain()
      reverbSend.gain.value = 0.55
      const cv = makeReverb(c)
      reverbSend.connect(cv)
      cv.connect(bgmGain)
    }
    bgmRunning = true
    bgmBarIdx = 0
    bgmNextBar = c.currentTime + 0.3
    // 2.5 秒淡入, 避免一进对局突然"砸"一声
    bgmGain.gain.cancelScheduledValues(c.currentTime)
    bgmGain.gain.setValueAtTime(bgmGain.gain.value, c.currentTime)
    bgmGain.gain.linearRampToValueAtTime(BGM_LEVEL, c.currentTime + 2.5)
    bgmLoop()
    if (bgmTimer) clearInterval(bgmTimer)
    bgmTimer = setInterval(bgmLoop, 400)
  } catch (e) {
    bgmRunning = false
    console.warn('[bgm] 启动失败, 已跳过背景音乐:', e && e.message)
  }
}

export function stopBgm() {
  try {
    if (!bgmRunning) return
    bgmRunning = false
    if (bgmTimer) { clearInterval(bgmTimer); bgmTimer = null }
    if (bgmGain && ctx) {
      const t = ctx.currentTime
      bgmGain.gain.cancelScheduledValues(t)
      bgmGain.gain.setValueAtTime(bgmGain.gain.value, t)
      bgmGain.gain.linearRampToValueAtTime(0, t + 1.2)   // 1.2 秒淡出
    }
  } catch (e) {
    bgmRunning = false
    if (bgmTimer) { clearInterval(bgmTimer); bgmTimer = null }
  }
}

export function isBgmRunning() { return bgmRunning }

export default {
  play, click, isOn, setOn, toggle, unlock,
  startBgm, stopBgm, isBgmOn, setBgmOn, isBgmRunning
}
