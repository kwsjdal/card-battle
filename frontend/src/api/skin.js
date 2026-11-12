import { ref } from 'vue'
import http from './request'

/** skinId -> 边框样式类 */
const MAP = {
  0: 'skin-default',
  1: 'skin-default',
  2: 'skin-ice',
  3: 'skin-flame',
  4: 'skin-forest',
  5: 'skin-holo',
  6: 'skin-gold',
  7: 'skin-amiya',
  8: 'skin-kalts',
  9: 'skin-w',
  10: 'skin-silverash',
  11: 'skin-surtr',
  12: 'skin-lappland'
}

/** 专属时装的官方立绘(穿上后卡面直接显示时装) */
const SKIN_ART = {
  'skin-amiya': '/skins/skin-amiya.jpg',
  'skin-kalts': '/skins/skin-kalts.jpg',
  'skin-w': '/skins/skin-w.jpg',
  'skin-silverash': '/skins/skin-silverash.jpg',
  'skin-surtr': '/skins/skin-surtr.jpg',
  'skin-lappland': '/skins/skin-lappland.jpg'
}

/** 皮肤预览立绘: 专属用官方时装, 通用用主题干员立绘 */
const SKIN_IMG = {
  'skin-default': '/cards/1.jpg',      // 玫兰莎
  'skin-ice': '/cards/16.jpg',         // 赫默(莱茵生命)
  'skin-flame': '/cards/39.jpg',       // 德克萨斯(企鹅物流)
  'skin-forest': '/cards/32.jpg',      // 陨星(萨卡兹)
  'skin-holo': '/cards/10.jpg',        // 斯卡蒂(深海猎人)
  'skin-gold': '/cards/57.jpg',        // 阿米娅(巴别塔)
  'skin-amiya': '/skins/skin-amiya.jpg',
  'skin-kalts': '/skins/skin-kalts.jpg',
  'skin-w': '/skins/skin-w.jpg',
  'skin-silverash': '/skins/skin-silverash.jpg',
  'skin-surtr': '/skins/skin-surtr.jpg',
  'skin-lappland': '/skins/skin-lappland.jpg'
}
const SKIN_OPER = {
  'skin-default': '玫兰莎', 'skin-ice': '赫默', 'skin-flame': '德克萨斯',
  'skin-forest': '陨星', 'skin-holo': '斯卡蒂', 'skin-gold': '阿米娅',
  'skin-amiya': '阿米娅', 'skin-kalts': '凯尔希', 'skin-w': 'W',
  'skin-silverash': '银灰', 'skin-surtr': '史尔特尔', 'skin-lappland': '拉普兰德'
}

/**
 * 每干员独立装配表(明日方舟模式): {干员名: skinId}
 * 无记录 = 罗德岛制式
 */
export const operatorSkinMap = ref({})

/** 登录后调用一次, 拉取装配表 */
export async function loadSkinMap() {
  try {
    operatorSkinMap.value = await http.get('/skins/map') || {}
  } catch { operatorSkinMap.value = {} }
}

/** 某干员当前穿的皮肤边框类 */
export function cardSkin(name) {
  const id = operatorSkinMap.value[name]
  return MAP[id] || 'skin-default'
}

/** 某干员是否穿了专属时装(卡面立绘会被时装覆盖) */
export function cardArt(name) {
  const id = operatorSkinMap.value[name]
  const cls = MAP[id]
  return (cls && SKIN_ART[cls]) || ''
}

/** 装配后更新本地映射(免刷新) */
export function setOperatorSkin(name, skinId) {
  const map = { ...operatorSkinMap.value }
  if (!skinId || skinId === 1) delete map[name]
  else map[name] = skinId
  operatorSkinMap.value = map
}

export const skinImg = (cls) => SKIN_IMG[cls] || '/cards/1.jpg'
export const skinOper = (cls) => SKIN_OPER[cls] || ''

/** 兼容旧引用: 全局卡框已改为每干员独立, 此值不再使用 */
export const currentSkin = ref('skin-default')
export function setSkin() { /* deprecated: 请用 setOperatorSkin */ }
