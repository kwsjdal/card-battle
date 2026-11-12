<template>
  <!--
    统一卡面组件: 费用=法力水晶, 攻击=⚔, 生命=❤, 并显示嘲讽/圣盾/冲锋/吸血关键字
    size: sm(92x124) / md(120x160) / lg(160x214)
  -->
  <!-- 保留 card 类: 各页面已有的 .card.sel / .card.attacking / .card.hit 等动画选择器继续生效 -->
  <div class="cface card" :class="[frame, 'r' + (card.rarity || 1), size, { taunt: kw.taunt, sleeping: sleeping }]"
       :style="{ backgroundImage: `url(${art})` }"
       :title="tip">
    <!-- 法力水晶(费用) -->
    <span class="cf-mana">{{ card.cost }}</span>

    <!-- 关键字角标 -->
    <span v-if="kw.taunt" class="cf-kw taunt" title="嘲讽：敌方必须优先攻击它">🛡</span>
    <span v-if="kw.shield" class="cf-kw shield" title="圣盾：免疫下一次伤害">✨</span>
    <span v-if="kw.charge" class="cf-kw charge" title="冲锋：上场即可攻击">⚡</span>
    <span v-if="kw.rush" class="cf-kw rush" title="突袭：上场当回合即可攻击随从，但不能打脸">🏃</span>
    <span v-if="kw.windfury" class="cf-kw wind" title="风怒：每回合可以攻击两次">🌪</span>
    <span v-if="kw.poisonous" class="cf-kw poison" title="剧毒：对随从造成伤害即直接摧毁它">☠</span>

    <!-- 攻击 / 生命 -->
    <span class="cf-atk" title="攻击力">⚔{{ card.attack }}</span>
    <span class="cf-hp" title="生命值">❤{{ card.hp }}</span>

    <!-- 召唤失调: 本回合刚上场且无冲锋, 下回合才能攻击 -->
    <span v-if="sleeping" class="cf-sleep" title="召唤失调：下个回合才能攻击（拥有「冲锋」的随从上场即可攻击）">💤</span>

    <!-- 卡名 -->
    <div class="cf-inner">
      <span class="cf-name">{{ card.name }}</span>
      <span v-if="card.rarity >= 4" class="cf-star">★</span>
    </div>

    <!-- 同名张数(卡组编辑用) -->
    <span v-if="count" class="cf-count">{{ count }}</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { cardSkin, cardArt } from '../api/skin'

const props = defineProps({
  card: { type: Object, required: true },
  size: { type: String, default: 'md' },
  count: { type: [String, Number], default: '' },
  /** 召唤失调: 本回合刚上场且无冲锋, 不能攻击 */
  sleeping: { type: Boolean, default: false },
  /** 干员档案用: 强制指定立绘(专属时装) */
  artOverride: { type: String, default: '' },
  /** 干员档案用: 强制指定卡框 */
  frameOverride: { type: String, default: '' }
})

/** 解析 effect: "battlecry:draw:1;taunt:0:0" */
function parse(eff) {
  const r = { taunt: false, charge: false, shield: false, lifesteal: false,
              rush: false, windfury: false, poisonous: false, list: [] }
  if (!eff) return r
  for (const one of String(eff).split(';')) {
    const p = one.split(':').map(s => (s || '').trim())
    if (!p[0]) continue
    if (p[0] === 'taunt') r.taunt = true
    else if (p[0] === 'charge') r.charge = true
    else if (p[0] === 'shield') r.shield = true
    else if (p[0] === 'lifesteal') r.lifesteal = true
    else if (p[0] === 'rush') r.rush = true
    else if (p[0] === 'windfury') r.windfury = true
    else if (p[0] === 'poisonous') r.poisonous = true
    else r.list.push(p)
  }
  return r
}

const kw = computed(() => parse(props.card.effect))
const frame = computed(() => props.frameOverride || cardSkin(props.card.name))
const art = computed(() => props.artOverride || cardArt(props.card.name)
  || props.card.img || '/cards/' + (props.card.id || 1) + '.jpg')

const tip = computed(() => {
  const t = [props.card.name]
  if (kw.value.taunt) t.push('嘲讽')
  if (kw.value.shield) t.push('圣盾')
  if (kw.value.charge) t.push('冲锋')
  if (kw.value.lifesteal) t.push('吸血')
  if (kw.value.rush) t.push('突袭')
  if (kw.value.windfury) t.push('风怒')
  if (kw.value.poisonous) t.push('剧毒')
  return t.join(' · ')
})
</script>

<style>
/* 全局样式(不加 scoped): 各页面共用同一套卡面观感 */
.cface {
  position: relative;
  border-radius: 8px;
  background-color: #22334d;
  background-size: cover;
  background-position: center top;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.6);
  user-select: none;
}
.cface::after {
  content: '';
  position: absolute; inset: 0;
  background: linear-gradient(160deg, rgba(15, 25, 45, 0.05) 50%, rgba(15, 25, 45, 0.9));
  pointer-events: none;
}
.cface.xs { width: 50px; height: 70px; border-radius: 4px; }
.cface.sm { width: 92px; height: 124px; }
.cface.md { width: 120px; height: 160px; }
.cface.lg { width: 160px; height: 214px; }
/* xs(卡组槽位): 只留费用 + 攻血, 不显示卡名 */
.cface.xs .cf-inner { display: none; }
.cface.xs .cf-mana { min-width: 16px; height: 16px; font-size: 10px; top: 2px; left: 2px; }
.cface.xs .cf-atk, .cface.xs .cf-hp {
  min-width: 22px; height: 15px; padding: 0 3px;
  font-size: 9px; border-radius: 8px; bottom: 2px;
}
.cface.xs .cf-atk { left: 2px; }
.cface.xs .cf-hp { right: 2px; }
.cface.xs .cf-kw { width: 13px; height: 13px; font-size: 8px; top: 2px; }
.cface.xs .cf-kw.taunt { right: 2px; }
.cface.xs .cf-kw.shield { right: 16px; }
.cface.xs .cf-kw.charge { right: 30px; }
.cface.xs .cf-kw.rush { right: 44px; }
.cface.xs .cf-kw.wind { right: 58px; }
.cface.xs .cf-kw.poison { right: 72px; }

/* 召唤失调角标 💤 */
.cf-sleep {
  position: absolute; bottom: 3px; left: 50%; transform: translateX(-50%); z-index: 4;
  padding: 0 5px; border-radius: 8px;
  background: rgba(8, 14, 26, 0.8);
  border: 1px solid rgba(160, 200, 255, 0.5);
  font-size: 10px; line-height: 15px;
  box-shadow: 0 0 6px rgba(0, 0, 0, 0.6);
}
.cface.lg .cf-sleep { font-size: 13px; line-height: 20px; bottom: 5px; }
.cface.xs .cf-sleep { bottom: 18px; font-size: 8px; padding: 0 3px; }

/* 嘲讽: 砖墙般的灰白外框 + 顶部斜纹警示条, 一眼看出"必须先打它" */
.cface.taunt {
  border: 2px solid #b9b2a4;
  box-shadow: 0 0 0 1px #6d6659 inset, 0 2px 10px rgba(0, 0, 0, 0.7);
}
.cface.taunt::before {
  content: '';
  position: absolute; inset: 0; z-index: 2;
  background: repeating-linear-gradient(45deg,
    rgba(210, 205, 195, 0.22) 0 6px, transparent 6px 12px);
  pointer-events: none;
}
/* 召唤失调: 灰暗不可动 */
.cface.sleeping { filter: grayscale(0.55) brightness(0.8); }

/* 传说(r4): 斜向流光扫过, 与普通/稀有/史诗拉开档次 */
.cface.r4::before {
  content: '';
  position: absolute; inset: -40%;
  z-index: 2;
  background: linear-gradient(115deg,
    transparent 40%, rgba(255, 226, 160, 0.42) 50%, transparent 60%);
  animation: cfShine 2.8s linear infinite;
  pointer-events: none;
}
@keyframes cfShine {
  0% { transform: translateX(-60%); }
  100% { transform: translateX(60%); }
}

/* 法力水晶 */
.cf-mana {
  position: absolute; top: 3px; left: 3px; z-index: 3;
  min-width: 22px; height: 22px; padding: 0 3px;
  display: flex; align-items: center; justify-content: center;
  background: radial-gradient(circle at 32% 28%, #9fd8ff, #3d7fd0 55%, #16386e);
  border: 1px solid #071a35;
  border-radius: 50% 50% 50% 50% / 60% 60% 40% 40%;
  box-shadow: 0 0 8px rgba(90, 170, 255, 0.7), inset 0 1px 2px rgba(255, 255, 255, 0.5);
  color: #fff; font-size: 13px; font-weight: 800;
  text-shadow: 0 1px 2px #04122b;
}
.cface.lg .cf-mana { min-width: 30px; height: 30px; font-size: 16px; }

/* 关键字角标 */
.cf-kw {
  position: absolute; top: 3px; z-index: 3;
  width: 18px; height: 18px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 10px;
  background: rgba(0, 0, 0, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.25);
}
.cface.lg .cf-kw { width: 24px; height: 24px; font-size: 13px; }
.cf-kw.taunt { right: 3px; box-shadow: 0 0 6px rgba(200, 200, 200, 0.6); }
.cf-kw.shield { right: 23px; box-shadow: 0 0 8px rgba(255, 214, 120, 0.9); }
.cface.lg .cf-kw.shield { right: 29px; }
.cf-kw.charge { right: 43px; box-shadow: 0 0 8px rgba(255, 196, 92, 0.8); }
.cface.lg .cf-kw.charge { right: 57px; }
/* 新增异能角标: 突袭(青) / 风怒(紫) / 剧毒(绿), 沿同一水平线继续左移 */
.cf-kw.rush { right: 63px; border-color: rgba(120, 220, 255, 0.55); box-shadow: 0 0 8px rgba(90, 200, 255, 0.75); }
.cface.lg .cf-kw.rush { right: 85px; }
.cf-kw.wind { right: 83px; border-color: rgba(190, 150, 255, 0.55); box-shadow: 0 0 8px rgba(170, 120, 255, 0.75); }
.cface.lg .cf-kw.wind { right: 113px; }
.cf-kw.poison { right: 103px; border-color: rgba(130, 235, 150, 0.55); box-shadow: 0 0 8px rgba(90, 220, 120, 0.75); }
.cface.lg .cf-kw.poison { right: 141px; }

/* 攻击 / 生命: 加大字号与描边, 小尺寸下也要一眼读清 */
.cf-atk, .cf-hp {
  position: absolute; bottom: 3px; z-index: 5;
  min-width: 32px; height: 24px; padding: 0 6px;
  display: flex; align-items: center; justify-content: center; gap: 2px;
  border-radius: 12px;
  font-size: 14px; font-weight: 900;
  font-variant-numeric: tabular-nums;
  color: #fff;
  -webkit-text-stroke: 0.6px rgba(0, 0, 0, 0.55);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.85), inset 0 1px 0 rgba(255, 255, 255, 0.3);
}
.cface.lg .cf-atk, .cface.lg .cf-hp { min-width: 40px; height: 28px; font-size: 15px; }
.cf-atk {
  left: 3px;
  background: linear-gradient(180deg, #f0c04a, #a1741d);
  border: 1px solid #5e420e;
  color: #2a1c04;
  text-shadow: 0 1px 0 rgba(255, 255, 255, 0.35);
}
.cf-hp {
  right: 3px;
  background: linear-gradient(180deg, #d6473c, #8c1f18);
  border: 1px solid #4d0f0b;
  text-shadow: 0 1px 2px #3a0a07;
}

/* 卡名: 加一条半透明衬底, 立绘再花也读得清 */
.cf-inner {
  position: absolute; bottom: 29px; left: 0; right: 0; z-index: 4;
  text-align: center;
  padding: 3px 4px;
  background: linear-gradient(180deg, transparent, rgba(6, 9, 14, 0.78) 45%);
}
.cface.lg .cf-inner { bottom: 36px; padding: 5px 6px; }
.cf-name {
  display: inline-block;
  font-size: 12px; font-weight: 700;
  color: #f7eeda; letter-spacing: 1px;
  text-shadow: 0 1px 3px #000, 0 0 8px #000, 0 0 2px #000;
}
.cface.lg .cf-name { font-size: 15px; letter-spacing: 2px; }
.cf-star { color: #f0cc6d; font-size: 10px; margin-left: 2px; }

/* 同名张数 */
.cf-count {
  position: absolute; bottom: 3px; left: 50%; transform: translateX(-50%); z-index: 3;
  padding: 0 6px; border-radius: 8px;
  background: rgba(0, 0, 0, 0.7); border: 1px solid rgba(240, 204, 109, 0.5);
  font-size: 10px; color: #f0cc6d;
}
</style>
