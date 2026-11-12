<template>
  <!-- 段位徽章: 菱形宝石造型, 靠配色区分段位; 形状统一以免抢了卡面立绘的视觉 -->
  <span class="tier" :class="'t-' + tier.key" :title="tip">
    <svg class="tier-icon" viewBox="0 0 24 24" aria-hidden="true">
      <path class="gem" d="M12 2 L21 8 L12 22 L3 8 Z" />
      <path class="facet" d="M3 8 H21 M12 2 V22" />
    </svg>
    <span v-if="showName" class="tier-name">{{ tier.name }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { rankTier } from '../utils/tier'

const props = defineProps({
  rating: { type: [Number, String], default: 0 },
  /** 是否显示段位文字（榜单里空间紧时可只显示图标） */
  showName: { type: Boolean, default: true }
})

const tier = computed(() => rankTier(props.rating))

const tip = computed(() => {
  const t = tier.value
  return t.next
    ? `${t.name} · ${props.rating} 分（距${t.next}还差 ${t.toNext} 分）`
    : `${t.name} · ${props.rating} 分（已达最高段位）`
})
</script>

<style>
.tier {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  vertical-align: middle;
}
.tier-icon {
  width: 15px;
  height: 15px;
  fill: none;
  stroke-width: 1.5;
  stroke-linejoin: round;
  stroke-linecap: round;
}
.tier-icon .gem { stroke-width: 1.6; }
.tier-icon .facet { opacity: 0.45; }
.tier-name {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 1px;
  white-space: nowrap;
}

/* 各段位配色: 描边 + 内部淡填充 + 外发光, 越高档光越亮 */
.t-bronze   .tier-icon { stroke: #c1854a; } .t-bronze   .tier-icon .gem { fill: rgba(193,133,74,0.22); }
.t-silver   .tier-icon { stroke: #c3ccd6; } .t-silver   .tier-icon .gem { fill: rgba(195,204,214,0.22); }
.t-gold     .tier-icon { stroke: #e6b422; } .t-gold     .tier-icon .gem { fill: rgba(230,180,34,0.25); }
.t-platinum .tier-icon { stroke: #7de3e8; } .t-platinum .tier-icon .gem { fill: rgba(125,227,232,0.25); }
.t-diamond  .tier-icon { stroke: #8fc4ff; } .t-diamond  .tier-icon .gem { fill: rgba(143,196,255,0.28); }
.t-master   .tier-icon { stroke: #c08cff; } .t-master   .tier-icon .gem { fill: rgba(192,140,255,0.3); }
.t-legend   .tier-icon { stroke: #ff7a5c; } .t-legend   .tier-icon .gem { fill: rgba(255,122,92,0.32); }

.t-bronze   .tier-name { color: #c1854a; }
.t-silver   .tier-name { color: #c3ccd6; }
.t-gold     .tier-name { color: #e6b422; }
.t-platinum .tier-name { color: #7de3e8; }
.t-diamond  .tier-name { color: #8fc4ff; }
.t-master   .tier-name { color: #c08cff; }
.t-legend   .tier-name { color: #ff7a5c; text-shadow: 0 0 8px rgba(255,122,92,0.6); }

/* 传说额外加一圈呼吸光 */
.t-legend .tier-icon { animation: legendPulse 2.2s ease-in-out infinite; }
@keyframes legendPulse {
  0%, 100% { filter: drop-shadow(0 0 1px rgba(255,122,92,0.4)); }
  50% { filter: drop-shadow(0 0 6px rgba(255,122,92,0.95)); }
}
</style>
