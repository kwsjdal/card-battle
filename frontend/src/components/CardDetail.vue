<template>
  <el-dialog v-model="visible" :width="dialogWidth" :title="`干员档案 · ${card.name || ''}`" center
             @closed="$emit('update:modelValue', false)">
    <div v-if="card.id" class="detail">
      <!-- 左: 大立绘 + 当前皮肤边框(专属时装会覆盖立绘) -->
      <div class="d-left">
        <CardFace :card="card" size="lg" :class="previewSkin" :art-override="previewArt" />
        <div class="d-rarity">{{ rarityName(card.rarity) }}</div>
        <div class="d-skin-hint" v-if="previewArt">♢ 正在穿着专属时装</div>
      </div>

      <!-- 右: 属性 + 效果 -->
      <div class="d-right">
        <div class="d-name">
          {{ card.name }}
          <span class="d-tag" v-if="profession">{{ profession }}</span>
        </div>
        <div class="d-line">费用 <b>{{ card.cost }}</b> · 攻击 <b>{{ card.attack }}</b> · 生命 <b>{{ card.hp }}</b></div>
        <div class="d-intro">
          <div class="eff-title">干 员 介 绍</div>
          <div class="d-desc">{{ card.description || '暂无档案介绍' }}</div>
        </div>

        <!-- 技能(炉石式: 战吼 / 亡语 / 被动关键字) -->
        <div class="d-eff" v-if="abilities.length || effects.length">
          <div class="eff-title">技 能</div>
          <div v-for="(e, i) in abilities" :key="'k'+i" class="eff-item">
            <span class="eff-ico" :class="e.type">{{ e.icon }}</span>
            <span class="eff-type" :class="e.type">{{ e.label }}</span>
            <span class="eff-text">{{ e.text }}</span>
          </div>
          <div v-for="(e, i) in effects" :key="'e'+i" class="eff-item">
            <span class="eff-ico" :class="e.type">{{ e.icon }}</span>
            <span class="eff-type" :class="e.type">{{ e.label }}</span>
            <span class="eff-text">{{ e.text }}</span>
          </div>
        </div>
        <div class="d-eff empty" v-else>
          <span class="eff-type">无特殊技能</span>
          <span class="eff-text">该干员仅有基础攻防属性</span>
        </div>
      </div>
    </div>

    <!-- 时装列表: 专属 + 通用(每干员独立装配, 互不影响) -->
    <div class="d-skins" v-if="!hideSkins">
      <div class="sk-title">
        可 用 时 装（{{ card.name }} 专属独立装配，不影响其他干员）
        <span class="sk-tip-sub">专属＝换成官方时装立绘　通用＝沿用这张卡面、只换卡框与光效</span>
      </div>
      <div class="sk-grid">
        <div v-for="s in skins" :key="s.id" class="sk-item"
             :class="{ on: isWearing(s), lock: !s.owned }">
          <div class="sk-preview">
            <!-- 预览 = 实际效果: 专属换官方时装立绘, 通用只在干员自己的卡面上换卡框 -->
            <div class="sk-card card" :class="s.frameClass"
                 :style="{ backgroundImage: `url(${s.exclusive ? skinImg(s.frameClass) : (card.img || '/cards/' + card.id + '.jpg')})` }">
              <span class="skc-name">{{ card.name }}</span>
            </div>
          </div>
          <div class="sk-info">
            <span class="sk-name">
              {{ s.name }}
              <em v-if="s.exclusive" class="sk-ex">专属 · 换立绘</em>
              <em v-else class="sk-gen">通用 · 换卡框</em>
            </span>
            <span class="sk-desc">{{ s.description }}</span>
          </div>
          <button v-if="isWearing(s)" class="sk-btn on" disabled>装 配 中</button>
          <button v-else-if="s.owned" class="sk-btn" @click="equip(s)">装 配</button>
          <button v-else class="sk-btn buy" @click="buy(s)">🪙 {{ s.price }} 购买</button>
        </div>
      </div>
    </div>

    <template #footer>
      <button v-if="!hideSkins" class="btn-plain" @click="unequip">卸 下 时 装</button>
      <button class="btn-main" @click="visible = false">关 闭</button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/request'
import { cardSkin, cardArt, setOperatorSkin, loadSkinMap, operatorSkinMap, skinImg } from '../api/skin'
import CardFace from './CardFace.vue'

const props = defineProps({
  modelValue: Boolean,
  card: { type: Object, default: () => ({}) },
  /** 战斗内速览模式: 隐藏时装装配区, 弹窗收窄, 只留属性/介绍/技能 */
  hideSkins: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])
const visible = ref(props.modelValue)
const skins = ref([])
const previewSkin = ref('skin-default')
const previewArt = ref('')
const dialogWidth = computed(() => (props.hideSkins ? 'min(600px, 94vw)' : '820px'))

watch(() => props.modelValue, (v) => {
  visible.value = v
  if (v) {
    loadSkinMap().then(() => {
      previewSkin.value = cardSkin(props.card.name)
      previewArt.value = cardArt(props.card.name)
    })
    if (!props.hideSkins) loadSkins()
  }
})
watch(visible, (v) => { if (!v) emit('update:modelValue', false) })

const rarityName = (r) => ({ 1: '普通', 2: '稀有', 3: '史诗', 4: '传说' }[r] || '普通')

/** 从 description 里提取职业(如"医疗干员" -> 医疗) */
const profession = computed(() => {
  const d = props.card.description || ''
  const m = d.match(/(先锋|近卫|重装|狙击|术师|医疗|辅助|特种|召唤)干员/)
  return m ? m[1] : (d.includes('术师') ? '术师' : (d.includes('狙击') ? '狙击' : ''))
})

/** 被动关键字: taunt / charge / shield / lifesteal / rush / windfury / poisonous */
const KEYWORD_INFO = {
  taunt:      { icon: '🛡', label: '嘲讽', text: '敌方随从必须优先攻击它，无法直接打脸' },
  charge:     { icon: '⚡', label: '冲锋', text: '上场即可攻击，不受召唤失调限制' },
  shield:     { icon: '✨', label: '圣盾', text: '免疫下一次受到的伤害，随后破盾' },
  lifesteal:  { icon: '🩸', label: '吸血', text: '造成伤害时，为己方英雄回复等量生命' },
  rush:       { icon: '🏃', label: '突袭', text: '上场当回合即可攻击随从，但还不能攻击英雄' },
  windfury:   { icon: '🌪', label: '风怒', text: '每回合可以攻击两次' },
  poisonous:  { icon: '☠', label: '剧毒', text: '对随从造成伤害即直接摧毁它，与伤害数值无关' }
}

/** 被动关键字列表(单独一行展示, 与主动技能区分) */
const abilities = computed(() => {
  const raw = props.card.effect
  if (!raw) return []
  const out = []
  for (const one of String(raw).split(';')) {
    const k = (one.split(':')[0] || '').trim()
    if (KEYWORD_INFO[k]) out.push({ type: k, ...KEYWORD_INFO[k] })
  }
  return out
})

/** 主动技能: 战吼 / 亡语 —— 解析 "battlecry:damage_face:2" */
const effects = computed(() => {
  const raw = props.card.effect
  if (!raw) return []
  return String(raw).split(';').map(parseOne).filter(Boolean)
})

function parseOne(code) {
  const p = code.split(':').map(s => (s || '').trim())
  const [timing, kind, val] = p
  if (timing !== 'battlecry' && timing !== 'deathrattle') return null   // 被动关键字走 abilities
  const isBattle = timing === 'battlecry'
  const label = isBattle ? '战吼' : '亡语'
  const icon = isBattle ? '⚡' : '💀'
  const who = isBattle ? '打出时' : '阵亡时'
  const map = {
    damage_face: `对敌方英雄造成 ${val} 点伤害`,
    aoe: `对所有敌方随从造成 ${val} 点伤害`,
    heal: `为己方英雄回复 ${val} 点生命`,
    draw: `抽 ${val} 张牌`,
    damage_random: `对一个随机敌方随从造成 ${val} 点伤害`,
    buff_all: `使己方其他随从获得 +${val}/+${val}`,
    summon: `召唤 ${val} 个 1/1 战术傀儡`
  }
  return { type: timing, icon, label, text: `${who}：${map[kind] || kind + ' ' + (val || '')}` }
}

/** 该干员当前是否穿着这款皮肤(罗德岛制式=无装配记录时显示) */
function isWearing(s) {
  const cur = operatorSkinMap.value[props.card.name]
  if (s.id === 1) return !cur || cur === 1
  return cur === s.id
}

async function loadSkins() {
  try {
    const r = await http.get('/skins/operator', { params: { name: props.card.name || '' } })
    skins.value = r.skins
  } catch { skins.value = [] }
}

/** 给该干员装配皮肤(每干员独立, 覆盖旧的) */
async function equip(s) {
  await http.post('/skins/equip-operator', { operator: props.card.name, skinId: s.id })
  setOperatorSkin(props.card.name, s.id)
  previewSkin.value = cardSkin(props.card.name)
  previewArt.value = cardArt(props.card.name)
  ElMessage.success(`「${props.card.name}」已换上「${s.name}」`)
  await loadSkins()
}

async function buy(s) {
  try {
    await http.post('/skins/buy', { skinId: s.id })
    ElMessage.success(`已购买「${s.name}」, 可直接装配`)
    await loadSkins()
  } catch { /* request.js 已弹错误 */ }
}

/** 卸下该干员的时装(恢复罗德岛制式) */
async function unequip() {
  await http.post('/skins/unequip-operator', { operator: props.card.name })
  setOperatorSkin(props.card.name, 0)
  previewSkin.value = 'skin-default'
  previewArt.value = ''
  ElMessage.success(`「${props.card.name}」已恢复罗德岛制式`)
  await loadSkins()
}
</script>

<style scoped>
.detail { display: flex; gap: 22px; }

/* === 左侧立绘 === */
.d-left { width: 200px; flex-shrink: 0; text-align: center; }
.d-portrait {
  position: relative;
  width: 180px; height: 250px;
  border-radius: 10px;
  background-color: #22334d;
  background-size: cover;
  background-position: center top;
  margin: 0 auto;
  overflow: visible;
}
/* 与卡池小卡一致的压暗渐变 + 底部姓名条，保证各处卡面观感统一 */
.d-portrait::after {
  content: '';
  position: absolute; inset: 0;
  border-radius: 10px;
  background: linear-gradient(160deg, rgba(15, 25, 45, 0.05) 55%, rgba(15, 25, 45, 0.88));
}
.d-nameplate {
  position: absolute; bottom: 16px; left: 0; right: 0; z-index: 2;
  text-align: center; font-size: 16px; letter-spacing: 3px; color: #e8dcc4;
  text-shadow: 0 1px 4px #000;
}
.d-rarity-plate {
  position: absolute; bottom: 3px; left: 0; right: 0; z-index: 2;
  text-align: center; font-size: 10px; letter-spacing: 2px; color: #b09c6e;
}
.d-cost {
  position: absolute; top: -6px; left: -6px; z-index: 3;
  width: 30px; height: 30px; border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #b4dcff, #4a90d9 50%, #1e4682);
  border: 2px solid #051530;
  color: #fff; font-weight: 800; font-size: 15px;
  display: flex; align-items: center; justify-content: center;
}
.d-atk, .d-hp {
  position: absolute; bottom: -8px; z-index: 3;
  width: 32px; height: 32px; border-radius: 50%;
  color: #fff; font-weight: 800; font-size: 15px;
  display: flex; align-items: center; justify-content: center;
  border: 2px solid #1a1005;
}
.d-atk { right: 6px; background: linear-gradient(135deg, #e6b94f, #a5842f); color: #1a1005; }
.d-hp { left: 6px; background: linear-gradient(135deg, #3cc24a, #1a6b24); }
.d-rarity { margin-top: 16px; font-size: 13px; color: #f0cc6d; letter-spacing: 3px; }
.d-skin-hint { margin-top: 8px; font-size: 11px; color: #8fd7ff; letter-spacing: 1px; }

/* === 右侧属性 === */
.d-right { flex: 1; min-width: 0; }
.d-name { font-size: 22px; font-weight: 700; color: #f0cc6d; letter-spacing: 3px; }
.d-tag {
  display: inline-block; margin-left: 10px; padding: 2px 10px;
  font-size: 11px; font-weight: 400; letter-spacing: 1px;
  border: 1px solid rgba(240, 204, 109, 0.45);
  border-radius: 10px; color: #d8c9a8;
}
.d-line { margin-top: 8px; font-size: 13px; color: #b8a884; }
.d-line b { color: #e8dcc4; font-size: 15px; }
.d-intro {
  margin-top: 12px; padding: 12px 14px;
  background: rgba(240, 204, 109, 0.06);
  border: 1px solid rgba(240, 204, 109, 0.22);
  border-radius: 8px;
}
.d-intro .eff-title { margin-bottom: 6px; }
.d-desc { font-size: 13px; color: #c8b892; line-height: 1.9; }

.d-eff { margin-top: 14px; padding: 12px 14px; background: rgba(0, 0, 0, 0.35); border-radius: 8px; }
.d-eff.empty { padding: 10px 14px; }
.eff-title { font-size: 12px; color: #f0cc6d; letter-spacing: 3px; margin-bottom: 8px; }
.eff-item { display: flex; align-items: center; gap: 10px; padding: 4px 0; }
.eff-ico { font-size: 15px; filter: drop-shadow(0 0 5px rgba(255, 196, 92, 0.8)); }
.eff-type {
  padding: 2px 10px; border-radius: 3px; font-size: 11px; font-weight: 700;
  background: rgba(240, 204, 109, 0.18); color: #f0cc6d;
  border: 1px solid rgba(240, 204, 109, 0.4);
}
.eff-ico.deathrattle ~ .eff-type { background: rgba(176, 74, 217, 0.18); color: #d8a8f0; border-color: rgba(176, 74, 217, 0.45); }
.eff-text { font-size: 12px; color: #c4b28a; }

/* === 时装列表 === */
.d-skins { margin-top: 18px; border-top: 1px solid rgba(212, 168, 71, 0.25); padding-top: 14px; }
.sk-title { font-size: 12px; color: #f0cc6d; letter-spacing: 3px; margin-bottom: 12px; }
.sk-tip-sub { display: block; margin-top: 4px; font-size: 10px; letter-spacing: 1px; color: #8a7b62; }
.sk-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(148px, 1fr)); gap: 12px; max-height: 240px; overflow-y: auto; }
.sk-item {
  padding: 10px;
  background: rgba(20, 16, 12, 0.7);
  border: 1px solid rgba(120, 90, 50, 0.45);
  border-radius: 8px;
  display: flex; flex-direction: column; gap: 8px; align-items: center;
}
.sk-item.on { border-color: #f0cc6d; box-shadow: 0 0 12px rgba(240, 204, 109, 0.35); }
.sk-item.lock { opacity: 0.75; }
.sk-preview { display: flex; justify-content: center; }
.sk-card {
  position: relative;
  width: 68px; height: 94px;
  border-radius: 6px;
  background-color: #22334d;
  background-size: cover;
  background-position: center top;
}
.skc-name {
  position: absolute; bottom: 3px; left: 0; right: 0;
  text-align: center; font-size: 9px; color: #fff;
  text-shadow: 0 1px 2px #000; background: rgba(0, 0, 0, 0.45);
}
.sk-info { text-align: center; display: flex; flex-direction: column; gap: 2px; }
.sk-name { font-size: 12px; color: #f0cc6d; font-weight: 600; }
.sk-ex, .sk-gen {
  font-style: normal; font-size: 9px; margin-left: 4px;
  padding: 1px 5px; border-radius: 8px;
}
.sk-ex { background: rgba(230, 162, 60, 0.2); color: #f0cc6d; }
.sk-gen { background: rgba(143, 215, 255, 0.15); color: #8fd7ff; }
.sk-desc { font-size: 10px; color: #8a7b62; }
.sk-btn {
  width: 100%;
  padding: 5px 0;
  border-radius: 4px;
  font-size: 11px; letter-spacing: 1px; cursor: pointer;
  background: transparent;
  border: 1px solid var(--gold-dark);
  color: #d8c9a8;
}
.sk-btn:hover:not(:disabled) { background: rgba(212, 168, 71, 0.15); color: #f0cc6d; }
.sk-btn.buy { background: linear-gradient(180deg, #e6b94f, #a5842f); color: #1a1005; font-weight: 700; border-color: #7a5f1e; }
.sk-btn.on { border-color: #f0cc6d; color: #f0cc6d; }
.sk-btn:disabled { cursor: default; }

.btn-main, .btn-plain {
  padding: 8px 24px;
  border-radius: 4px;
  font-weight: 700; letter-spacing: 3px; cursor: pointer;
  font-size: 13px;
}
.btn-main { background: linear-gradient(180deg, #e6b94f, #a5842f); border: 1px solid #7a5f1e; color: #1a1005; }
.btn-plain { background: transparent; border: 1px solid var(--gold-dark); color: #d8c9a8; }
.btn-plain:hover { background: rgba(212, 168, 71, 0.15); color: #f0cc6d; }

/* 手机端(含战斗内速览): 立绘与属性改为上下排列, 避免横向溢出 */
@media (max-width: 640px) {
  .detail { flex-direction: column; gap: 14px; }
  .d-left { width: 100%; }
  .d-right { width: 100%; }
  .d-name { font-size: 18px; letter-spacing: 2px; }
  .d-intro { padding: 10px 12px; }
  .d-desc { font-size: 12px; line-height: 1.8; }
  .eff-item { flex-wrap: wrap; gap: 6px; }
}
</style>
