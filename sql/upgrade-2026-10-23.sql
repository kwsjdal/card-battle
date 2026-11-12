-- ============================================================================
--  2026-10-23 增量：双货币(金币/水晶) + 明日方舟新干员 + 方舟主题皮肤
--  在 card_battle 库执行；新装用户直接用最新 init.sql，无需本文件
-- ============================================================================

USE card_battle;

-- 1. 用户表新增水晶字段（签到/任务获得，可抽卡，比金币抽卡贵100/抽）
ALTER TABLE `users`
  ADD COLUMN `crystal` int NOT NULL DEFAULT 0 COMMENT '水晶(签到/任务获得)' AFTER `balance`;

-- 存量用户发一点初始水晶便于体验新功能
UPDATE `users` SET `crystal` = 300;

-- 2. 新增 10 位明日方舟干员（id 57-66，卡面图待生成，img 为 NULL）
INSERT INTO `cards` (`id`, `name`, `cost`, `attack`, `hp`, `effect`, `img`, `rarity`, `description`) VALUES
  (57, '阿米娅', 3, 3, 4, NULL, NULL, 3, '罗德岛行动总指挥'),
  (58, '伊芙利特', 6, 6, 5, NULL, NULL, 4, '高热术师干员'),
  (59, '赫拉格', 5, 5, 5, NULL, NULL, 3, '孤胆近卫干员'),
  (60, 'W', 5, 4, 5, NULL, NULL, 4, '爆破狙击干员'),
  (61, '凯尔希', 5, 3, 7, NULL, NULL, 4, '罗德岛医疗主管'),
  (62, '铃兰', 4, 2, 6, NULL, NULL, 3, '辅助干员'),
  (63, '煌', 6, 5, 6, NULL, NULL, 4, '链锯近卫干员'),
  (64, '森蚺', 6, 4, 8, NULL, NULL, 3, '机甲重装干员'),
  (65, '温蒂', 5, 5, 4, NULL, NULL, 3, '推击手特种'),
  (66, '夕', 6, 4, 7, NULL, NULL, 4, '绘梦术师干员');

-- 3. 新增 6 款明日方舟干员主题皮肤（参考开源方舟角色皮肤设计，金币购买）
INSERT INTO `skins` (`id`, `name`, `price`, `frame_class`, `description`) VALUES
  (7,  '新约·阿米娅', 600,  'skin-amiya',    '阿米娅升变主题，青紫双源石光边'),
  (8,  '医师·凯尔希', 800,  'skin-kalts',    '凯尔希医疗主题，白绿医疗辐光环边'),
  (9,  '爆破·W',      1000, 'skin-w',        'W的爆破美学，橙黑爆痕焦边'),
  (10, '雪境·银灰',   1000, 'skin-silverash','银灰雪境领主主题，霜银冷冽光边'),
  (11, '黄昏·史尔特尔', 1200, 'skin-surtr',  '史尔特尔焚烬主题，赤金烈焰熔边'),
  (12, '荒野·拉普兰德', 800, 'skin-lappland', '拉普兰德荒野主题，冷月银紫狼边');


-- 4. 干员档案页: 皮肤绑定干员(专属时装) + 通用卡框
ALTER TABLE `skins`
  ADD COLUMN `operator` varchar(32) NULL COMMENT '绑定干员名, NULL=通用卡框' AFTER `description`;
UPDATE `skins` SET `operator` = '阿米娅'   WHERE `id` = 7;
UPDATE `skins` SET `operator` = '凯尔希'   WHERE `id` = 8;
UPDATE `skins` SET `operator` = 'W'        WHERE `id` = 9;
UPDATE `skins` SET `operator` = '银灰'     WHERE `id` = 10;
UPDATE `skins` SET `operator` = '史尔特尔' WHERE `id` = 11;
UPDATE `skins` SET `operator` = '拉普兰德' WHERE `id` = 12;

-- 5. 每干员独立装配皮肤(明日方舟模式): 一个干员同一时间只能穿一款, 互不影响
CREATE TABLE IF NOT EXISTS `user_operator_skin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `operator` varchar(32) NOT NULL COMMENT '干员名(即卡牌名)',
  `skin_id` int NOT NULL COMMENT '装配的皮肤',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_oper` (`user_id`, `operator`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每干员独立装配的皮肤';

-- 回填说明：卡面图生成后执行
-- UPDATE `cards` SET `img` = CONCAT('/cards/', `id`, '.jpg') WHERE `id` BETWEEN 57 AND 66;
