/**
 * BGM 引擎离线自检
 * 用一套"会挑错"的 Web Audio 桩(Stub)跑真实的 sound.js,
 * 目的是在没有浏览器的情况下抓住:
 *   - 非法参数(exponentialRamp 目标 <= 0 / NaN 频率 / 未知波形)
 *   - 未启动就 stop、重复 start 之类的状态机错误
 *   - 音符频率是否落在合理的人耳区间
 * 运行: node tools/bgm-selftest.mjs
 */
const errors = []
const stats = {
  osc: 0, gain: 0, filter: 0, convolver: 0, bufferSource: 0,
  freqs: [], types: new Set(), ramps: 0
}

function bad(msg) { errors.push(msg) }

function param(name, initial) {
  const p = {
    value: initial,
    setValueAtTime(v, t) {
      if (!Number.isFinite(v)) bad(`${name}.setValueAtTime 非法值 ${v}`)
      if (!Number.isFinite(t) || t < 0) bad(`${name}.setValueAtTime 非法时间 ${t}`)
      p.value = v
      return p
    },
    linearRampToValueAtTime(v, t) {
      if (!Number.isFinite(v)) bad(`${name}.linearRamp 非法值 ${v}`)
      if (!Number.isFinite(t) || t < 0) bad(`${name}.linearRamp 非法时间 ${t}`)
      p.value = v
      return p
    },
    exponentialRampToValueAtTime(v, t) {
      // Web Audio 规范: exponentialRamp 的目标值必须 > 0, 否则抛 InvalidStateError
      if (!Number.isFinite(v) || v <= 0) bad(`${name}.exponentialRamp 目标必须 > 0, 实际 ${v}`)
      if (!Number.isFinite(t) || t < 0) bad(`${name}.exponentialRamp 非法时间 ${t}`)
      p.value = v
      stats.ramps++
      return p
    },
    cancelScheduledValues() { return p }
  }
  return p
}

const TYPES = new Set(['sine', 'square', 'sawtooth', 'triangle', 'custom'])

class StubContext {
  constructor() {
    this.sampleRate = 48000
    this.currentTime = 0
    this.state = 'running'
    this.destination = { connect() {} }
    globalThis.__ctx = this
  }
  resume() { this.state = 'running'; return Promise.resolve() }
  createGain() {
    stats.gain++
    return { gain: param('gain', 1), connect() {}, disconnect() {} }
  }
  createOscillator() {
    stats.osc++
    const o = {
      _type: 'sine',
      frequency: param('frequency', 440),
      detune: param('detune', 0),
      connect() {}, disconnect() {},
      start(t) {
        if (o._started) bad('oscillator 重复 start')
        if (!Number.isFinite(t) || t < 0) bad(`oscillator.start 非法时间 ${t}`)
        o._started = true
        const f = o.frequency.value
        if (!(f > 20 && f < 5000)) bad(`振荡器频率超出人耳合理区间: ${f}`)
        stats.freqs.push(f)
      },
      stop(t) {
        if (!o._started) bad('oscillator 未 start 就 stop')
        if (!Number.isFinite(t) || t < 0) bad(`oscillator.stop 非法时间 ${t}`)
      }
    }
    Object.defineProperty(o, 'type', {
      get() { return o._type },
      set(v) {
        if (!TYPES.has(v)) bad(`未知振荡器波形 ${v}`)
        o._type = v
        stats.types.add(v)
      }
    })
    return o
  }
  createBiquadFilter() {
    stats.filter++
    const f = {
      _type: 'lowpass',
      frequency: param('filter.frequency', 350),
      Q: param('filter.Q', 1),
      connect() {},
      disconnect() {}
    }
    Object.defineProperty(f, 'type', {
      get() { return f._type },
      set(v) {
        if (!['lowpass', 'highpass', 'bandpass', 'notch'].includes(v)) bad(`未知滤波器类型 ${v}`)
        f._type = v
      }
    })
    return f
  }
  createConvolver() {
    stats.convolver++
    return { buffer: null, connect() {}, disconnect() {} }
  }
  createBuffer(ch, len, rate) {
    if (!(len > 0)) bad(`createBuffer 长度非法 ${len}`)
    return {
      numberOfChannels: ch,
      length: len,
      sampleRate: rate,
      getChannelData: () => new Float32Array(len)
    }
  }
  createBufferSource() {
    stats.bufferSource++
    return { buffer: null, connect() {}, start() {}, stop() {} }
  }
}

const AC = StubContext
globalThis.window = { AudioContext: AC, webkitAudioContext: AC }
const store = new Map()
globalThis.localStorage = {
  getItem: k => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v))
}

const sfx = await import('../frontend/src/utils/sound.js')

// ---- 用例 1: 默认开关 ----
if (!sfx.isBgmOn()) bad('bgmOn 默认应为开')
sfx.setBgmOn(false)
if (sfx.isBgmOn()) bad('setBgmOn(false) 后仍为开')
sfx.setBgmOn(true)

// ---- 用例 2: 启动并渲染 2 个小节 ----
const t0 = Date.now()
sfx.startBgm()
if (!sfx.isBgmRunning()) bad('startBgm 后 isBgmRunning 应为 true')

// 手动推进时间: 每 300ms 把 currentTime 往前推 1 秒, 让 8 秒的小节依次排进来
for (let i = 0; i < 20; i++) {
  await new Promise(r => setTimeout(r, 300))
  globalThis.__ctx.currentTime += 1
}

// ---- 用例 3: 重复 start 不应重复建图 ----
const gainBefore = stats.gain
sfx.startBgm()
if (stats.gain !== gainBefore) bad('重复 startBgm 又新建了音频节点(应幂等)')

// ---- 用例 4: 关闭 ----
sfx.stopBgm()
if (sfx.isBgmRunning()) bad('stopBgm 后 isBgmRunning 应为 false')
sfx.stopBgm()   // 幂等
sfx.startBgm()  // 可再次打开
if (!sfx.isBgmRunning()) bad('再次 startBgm 失败')
sfx.stopBgm()

// ---- 用例 5: 音效仍可用 ----
sfx.play('attack')
sfx.play('win')

// ---- 汇总 ----
const uniq = [...new Set(stats.freqs.map(f => Math.round(f * 100) / 100))].sort((a, b) => a - b)
console.log('振荡器:', stats.osc, ' 增益节点:', stats.gain, ' 滤波器:', stats.filter,
  ' 卷积混响:', stats.convolver, ' 指数包络段:', stats.ramps)
console.log('波形种类:', [...stats.types].join(', '))
console.log('出现过的频率(Hz), 共', uniq.length, '种:')
console.log('  ' + uniq.slice(0, 30).join(' '))
const elapsed = Date.now() - t0
if (!stats.osc) bad('没有任何振荡器被创建, BGM 实际没响')
if (stats.convolver !== 1) bad(`混响应只建 1 个, 实际 ${stats.convolver}`)
if (elapsed < 2000) bad('调度循环没有持续运行')

if (errors.length) {
  console.log('\n✗ 失败 ' + errors.length + ' 项:')
  for (const e of [...new Set(errors)].slice(0, 20)) console.log('  - ' + e)
  process.exit(1)
}
console.log('\n✓ BGM 引擎自检全部通过 (参数合法 / 节点未重复创建 / 启停幂等)')
