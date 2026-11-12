/**
 * 天梯段位映射
 *
 * 初始分 1000，每局 ±25。区段按"净胜多少局能上去"来划：
 * 1000 起步是青铜，打到传说约需净胜 36 局，既够得着也不至于泛滥。
 */

const TIERS = [
  { key: 'bronze',   name: '青铜', min: 0,    color: '#c1854a', glow: 'rgba(193,133,74,0.55)' },
  { key: 'silver',   name: '白银', min: 1100, color: '#c3ccd6', glow: 'rgba(195,204,214,0.55)' },
  { key: 'gold',     name: '黄金', min: 1200, color: '#e6b422', glow: 'rgba(230,180,34,0.6)' },
  { key: 'platinum', name: '铂金', min: 1350, color: '#7de3e8', glow: 'rgba(125,227,232,0.6)' },
  { key: 'diamond',  name: '钻石', min: 1500, color: '#8fc4ff', glow: 'rgba(143,196,255,0.65)' },
  { key: 'master',   name: '大师', min: 1700, color: '#c08cff', glow: 'rgba(192,140,255,0.65)' },
  { key: 'legend',   name: '传说', min: 1900, color: '#ff7a5c', glow: 'rgba(255,122,92,0.7)' }
]

/** 天梯分 -> 段位（找不到时兜底为青铜，避免 undefined 传播） */
export function rankTier(rating) {
  const r = Number(rating) || 0
  let hit = TIERS[0]
  for (const t of TIERS) {
    if (r >= t.min) hit = t
  }
  const idx = TIERS.indexOf(hit)
  const next = TIERS[idx + 1]
  return {
    ...hit,
    next: next ? next.name : null,
    // 距离下一段还差多少分（已是最高段位时返回 0）
    toNext: next ? Math.max(0, next.min - r) : 0
  }
}

/** 排行榜分组着色用：取整条榜单里段位的最高档，避免一屏七种颜色 */
export function tierKey(rating) {
  return rankTier(rating).key
}

export { TIERS }
