-- =====================================================================
-- 干员技能系统：66 位干员全部拥有战吼/亡语/关键字（2026-10-23）
-- 编码: 多条用 ';' 分隔, 单条 "时机:效果:数值"
--   主动: battlecry(战吼,打出时) / deathrattle(亡语,阵亡时)
--   被动: taunt(嘲讽) / charge(冲锋) / shield(圣盾) / lifesteal(吸血)
--   效果: damage_face 伤敌方英雄 / heal 治疗 / draw 抽牌 / aoe 敌方全场
--        damage_random 随机敌方随从 / buff_all 友方全体+1/+1 / summon 召唤1/1
-- 设计原则: 重装多嘲讽、先锋多抽牌/冲锋、医疗多治疗、术师多AOE、
--          狙击多直伤、近卫多站场、辅助多buff、特种多亡语。
-- 幂等：可重复执行。
-- =====================================================================
USE card_battle;

-- ---------- 1 费 ----------
UPDATE cards SET effect='charge:0:0'                                   WHERE id=1;   -- 玫兰莎 近卫: 冲锋
UPDATE cards SET effect='battlecry:draw:1'                             WHERE id=2;   -- 克洛丝 狙击: 战吼抽牌
UPDATE cards SET effect='taunt:0:0'                                    WHERE id=3;   -- 米格鲁 重装: 嘲讽
UPDATE cards SET effect='battlecry:damage_face:2'                      WHERE id=4;   -- 炎熔 术师: 战吼直伤2
UPDATE cards SET effect='battlecry:buff_all:1'                         WHERE id=5;   -- 猎蜂 近卫: 战吼友方全体+1/+1

-- ---------- 2 费 ----------
UPDATE cards SET effect='lifesteal:0:0'                                WHERE id=6;   -- 拉普兰德 近卫: 吸血
UPDATE cards SET effect='charge:0:0;battlecry:draw:1'                  WHERE id=7;   -- 夜刀 先锋: 冲锋+抽牌
UPDATE cards SET effect='battlecry:heal:4'                             WHERE id=8;   -- 白面鸮 医疗: 战吼治疗4
UPDATE cards SET effect='taunt:0:0;deathrattle:heal:3'                 WHERE id=9;   -- 星熊 重装: 嘲讽+亡语治疗
UPDATE cards SET effect='charge:0:0;lifesteal:0:0'                     WHERE id=10;  -- 斯卡蒂 近卫: 冲锋+吸血

-- ---------- 3 费以上 ----------
UPDATE cards SET effect='taunt:0:0;shield:0:0'                         WHERE id=11;  -- 年 重装: 嘲讽+圣盾
UPDATE cards SET effect='taunt:0:0;battlecry:heal:3'                   WHERE id=12;  -- 临光 重装: 嘲讽+治疗
UPDATE cards SET effect='battlecry:damage_random:2'                    WHERE id=13;  -- 霜叶 近卫: 战吼随机伤害2
UPDATE cards SET effect='battlecry:draw:2'                             WHERE id=14;  -- 安洁莉娜 辅助: 战吼抽2张
UPDATE cards SET effect='battlecry:aoe:2'                              WHERE id=15;  -- 惊蛰 术师: 战吼AOE 2
UPDATE cards SET effect='deathrattle:summon:1'                         WHERE id=16;  -- 赫默 医疗: 亡语召唤傀儡
UPDATE cards SET effect='battlecry:buff_all:1;taunt:0:0'               WHERE id=17;  -- 银灰 近卫: 战吼全体+1/+1+嘲讽
UPDATE cards SET effect='battlecry:aoe:3'                              WHERE id=18;  -- 艾雅法拉 术师: 战吼AOE 3
UPDATE cards SET effect='taunt:0:0;deathrattle:summon:1'               WHERE id=19;  -- 泥岩 重装: 嘲讽+亡语召唤
UPDATE cards SET effect='charge:0:0;battlecry:damage_face:2'           WHERE id=20;  -- 能天使 狙击: 冲锋+直伤2

-- ---------- 21~40 ----------
UPDATE cards SET effect='battlecry:draw:1;taunt:0:0'                   WHERE id=21;  -- 香草 先锋
UPDATE cards SET effect='charge:0:0'                                   WHERE id=22;  -- 翎羽 先锋: 冲锋
UPDATE cards SET effect='lifesteal:0:0;battlecry:heal:2'               WHERE id=23;  -- 月见夜 近卫
UPDATE cards SET effect='battlecry:draw:1'                             WHERE id=24;  -- 芬 先锋
UPDATE cards SET effect='battlecry:damage_random:1;battlecry:draw:1'   WHERE id=25;  -- 12F 术师
UPDATE cards SET effect='charge:0:0;deathrattle:draw:1'                WHERE id=26;  -- 红豆 先锋
UPDATE cards SET effect='battlecry:draw:1;deathrattle:draw:1'          WHERE id=27;  -- 清道夫 先锋
UPDATE cards SET effect='lifesteal:0:0;taunt:0:0'                      WHERE id=28;  -- 缠丸 近卫
UPDATE cards SET effect='battlecry:buff_all:1;deathrattle:summon:1'    WHERE id=29;  -- 艾丝黛尔 近卫
UPDATE cards SET effect='battlecry:damage_random:2'                    WHERE id=30;  -- 白金 狙击
UPDATE cards SET effect='battlecry:damage_face:2;deathrattle:damage_face:1' WHERE id=31; -- 蓝毒 狙击
UPDATE cards SET effect='battlecry:aoe:2;taunt:0:0'                    WHERE id=32;  -- 陨星 狙击
UPDATE cards SET effect='battlecry:damage_random:3'                    WHERE id=33;  -- 守林人 狙击
UPDATE cards SET effect='battlecry:heal:4;deathrattle:heal:2'          WHERE id=34;  -- 调香师 医疗
UPDATE cards SET effect='battlecry:heal:3;shield:0:0'                  WHERE id=35;  -- 末药 医疗
UPDATE cards SET effect='battlecry:heal:5'                             WHERE id=36;  -- 嘉维尔 医疗
UPDATE cards SET effect='taunt:0:0;battlecry:heal:2'                   WHERE id=37;  -- 蛇屠箱 重装
UPDATE cards SET effect='taunt:0:0;deathrattle:heal:3'                 WHERE id=38;  -- 古米 重装
UPDATE cards SET effect='charge:0:0;battlecry:draw:1'                  WHERE id=39;  -- 德克萨斯 先锋
UPDATE cards SET effect='taunt:0:0;shield:0:0'                         WHERE id=40;  -- 芙兰卡 重装

-- ---------- 41~66 ----------
UPDATE cards SET effect='battlecry:damage_random:2;charge:0:0'         WHERE id=41;  -- 普罗旺斯 狙击
UPDATE cards SET effect='lifesteal:0:0;battlecry:heal:3'               WHERE id=42;  -- 华法琳 医疗
UPDATE cards SET effect='taunt:0:0;deathrattle:draw:1'                 WHERE id=43;  -- 可颂 重装
UPDATE cards SET effect='taunt:0:0;battlecry:aoe:1'                    WHERE id=44;  -- 雷蛇 重装
UPDATE cards SET effect='battlecry:buff_all:1;deathrattle:heal:3'      WHERE id=45;  -- 初雪 辅助
UPDATE cards SET effect='deathrattle:summon:2'                         WHERE id=46;  -- 梅尔 辅助: 亡语召唤2个
UPDATE cards SET effect='charge:0:0;battlecry:damage_random:2'         WHERE id=47;  -- 红 特种
UPDATE cards SET effect='charge:0:0;lifesteal:0:0'                     WHERE id=48;  -- 食铁兽 特种
UPDATE cards SET effect='battlecry:aoe:2;charge:0:0'                   WHERE id=49;  -- 陈 近卫
UPDATE cards SET effect='battlecry:buff_all:2'                         WHERE id=50;  -- 推进之王 先锋
UPDATE cards SET effect='battlecry:heal:6;shield:0:0'                  WHERE id=51;  -- 夜莺 医疗
UPDATE cards SET effect='shield:0:0;battlecry:heal:4'                  WHERE id=52;  -- 闪灵 医疗
UPDATE cards SET effect='taunt:0:0;battlecry:buff_all:1'               WHERE id=53;  -- 塞雷娅 重装
UPDATE cards SET effect='charge:0:0;battlecry:aoe:2'                   WHERE id=54;  -- 史尔特尔 近卫
UPDATE cards SET effect='charge:0:0;battlecry:buff_all:1'              WHERE id=55;  -- 风笛 先锋
UPDATE cards SET effect='deathrattle:summon:2;battlecry:draw:1'        WHERE id=56;  -- 麦哲伦 辅助
UPDATE cards SET effect='battlecry:aoe:2;deathrattle:damage_face:2'    WHERE id=57;  -- 阿米娅 术师
UPDATE cards SET effect='battlecry:aoe:3;deathrattle:aoe:1'            WHERE id=58;  -- 伊芙利特 术师
UPDATE cards SET effect='lifesteal:0:0;taunt:0:0'                      WHERE id=59;  -- 赫拉格 近卫
UPDATE cards SET effect='battlecry:damage_random:3;deathrattle:aoe:2'  WHERE id=60;  -- W 狙击
UPDATE cards SET effect='battlecry:heal:5;deathrattle:summon:1'        WHERE id=61;  -- 凯尔希 医疗
UPDATE cards SET effect='battlecry:buff_all:2;deathrattle:heal:3'      WHERE id=62;  -- 铃兰 辅助
UPDATE cards SET effect='taunt:0:0;lifesteal:0:0;battlecry:aoe:1'      WHERE id=63;  -- 煌 近卫
UPDATE cards SET effect='taunt:0:0;shield:0:0;deathrattle:summon:1'    WHERE id=64;  -- 森蚺 重装
UPDATE cards SET effect='battlecry:aoe:2;deathrattle:draw:1'           WHERE id=65;  -- 温蒂 特种
UPDATE cards SET effect='battlecry:aoe:3;battlecry:draw:1'             WHERE id=66;  -- 夕 术师
