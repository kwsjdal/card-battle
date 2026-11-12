-- ============================================================================
--  卡牌对战系统 —— 数据库初始化脚本
--  毕业设计：基于 Spring Boot 的卡牌对战游戏匹配与战斗系统的设计与实现
--
--  环境要求：MySQL 8.0+（用到 JSON 字段与 utf8mb4_0900_ai_ci 排序规则）
--  生成方式：从真实运行库 card_battle 导出结构与基础数据后整理
--  生成时间：2026-10-23
--
--  【重要】本脚本会 DROP 并重建全部 9 张业务表，
--          重复执行会清空所有对局记录 / 卡组 / 充值流水等运行时数据，
--          仅用于全新安装或需要恢复初始演示数据时执行。
--
--  执行方式（命令行）：
--      mysql -u root -p < sql/init.sql
--  或 Navicat / DataGrip 中打开后全量执行。
-- ============================================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `card_battle`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `card_battle`;


-- ============================================================================
--  一、表结构（10 张表）
-- ============================================================================

DROP TABLE IF EXISTS `cards`;
DROP TABLE IF EXISTS `skins`;
DROP TABLE IF EXISTS `users`;
DROP TABLE IF EXISTS `user_cards`;
DROP TABLE IF EXISTS `user_decks`;
DROP TABLE IF EXISTS `user_skins`;
DROP TABLE IF EXISTS `match_records`;
DROP TABLE IF EXISTS `gacha_log`;
DROP TABLE IF EXISTS `recharge_log`;

-- ---------- 1. 卡牌库（共 56 张：id 1-20 为初始卡，21-56 为扩充干员）----------
CREATE TABLE `cards` (
  `id`          bigint      NOT NULL AUTO_INCREMENT,
  `name`        varchar(32) NOT NULL                COMMENT '卡牌名',
  `cost`        int         NOT NULL                COMMENT '法力消耗 1-10',
  `attack`      int         NOT NULL                COMMENT '攻击力',
  `hp`          int         NOT NULL                COMMENT '生命值',
  `effect`      varchar(255)         DEFAULT NULL   COMMENT '效果描述, 预留(嘲讽/战吼/亡语)',
  `img`         varchar(255)         DEFAULT NULL   COMMENT '卡面图片路径, 对应 frontend/public/cards/',
  `rarity`      tinyint     NOT NULL DEFAULT '1'    COMMENT '1普通 2稀有 3史诗 4传说',
  `description` varchar(255)         DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡牌库';

-- ---------- 2. 卡框皮肤 ----------
CREATE TABLE `skins` (
  `id`          int         NOT NULL AUTO_INCREMENT,
  `name`        varchar(40) NOT NULL,
  `price`       int         NOT NULL DEFAULT '0'    COMMENT '金币价格',
  `frame_class` varchar(30) NOT NULL                COMMENT '前端 CSS 类名',
  `description` varchar(100)         DEFAULT NULL,
  `operator`    varchar(32)          DEFAULT NULL   COMMENT '绑定干员名(专属时装), NULL=通用卡框',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡框皮肤';

-- ---------- 3. 用户表（16 字段）----------
CREATE TABLE `users` (
  `id`           bigint      NOT NULL AUTO_INCREMENT,
  `username`     varchar(32) NOT NULL                COMMENT '用户名',
  `password`     varchar(64) NOT NULL                COMMENT 'SHA-256(随机盐+全局盐+明文); 老账号为MD5(全局盐+明文), 首次登录自动升级',
  `salt`         varchar(32) DEFAULT NULL            COMMENT '每用户随机盐, NULL=老格式待升级',
  `pwd_cipher`   varchar(128) DEFAULT NULL           COMMENT '密码可逆密文(AES), 仅供管理员查看明文; 登录校验不使用',
  `nickname`     varchar(32) NOT NULL DEFAULT '新手召唤师',
  `rating`       int         NOT NULL DEFAULT '1000' COMMENT '天梯积分',
  `win_count`    int         NOT NULL DEFAULT '0',
  `lose_count`   int         NOT NULL DEFAULT '0',
  `create_time`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `role`         varchar(20) NOT NULL DEFAULT 'user' COMMENT 'user / admin',
  `balance`      int         NOT NULL DEFAULT '0'    COMMENT '金币(充值获得, 可买皮肤/抽卡)',
  `crystal`      int         NOT NULL DEFAULT '0'    COMMENT '水晶(每日签到/任务获得, 抽卡比金币贵100/抽)',
  `skin_id`      int         NOT NULL DEFAULT '0'    COMMENT '当前装备的皮肤 id, 0=未装备',
  `status`       tinyint     NOT NULL DEFAULT '0'    COMMENT '0正常 1封禁',
  `banned_until` datetime             DEFAULT NULL   COMMENT '封禁到期时间, NULL=永久',
  `ban_reason`   varchar(200)         DEFAULT NULL   COMMENT '封禁原因',
  `avatar`       varchar(200)         DEFAULT NULL   COMMENT '头像路径, 对应 frontend/public/avatars/',
  `phone`        varchar(20)          DEFAULT NULL   COMMENT '手机号, 短信验证码登录用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username`  (`username`),
  UNIQUE KEY `uk_phone`  (`phone`),
  KEY `idx_status` (`status`),
  KEY `idx_ct`     (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- ---------- 4. 用户已解锁卡牌 ----------
CREATE TABLE `user_cards` (
  `id`         bigint   NOT NULL AUTO_INCREMENT,
  `user_id`    bigint   NOT NULL,
  `card_id`    bigint   NOT NULL,
  `create_time` datetime         DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_card` (`user_id`, `card_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户卡牌';

-- ---------- 5. 用户卡组 ----------
CREATE TABLE `user_decks` (
  `id`         bigint      NOT NULL AUTO_INCREMENT,
  `user_id`    bigint      NOT NULL,
  `name`       varchar(32) NOT NULL DEFAULT '我的卡组',
  `card_ids`   json        NOT NULL                COMMENT '卡牌id列表, 共30张',
  `is_default` tinyint     NOT NULL DEFAULT 0       COMMENT '是否出战卡组: 1=匹配对局时使用(每人最多1套)',
  `create_time` datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_default` (`user_id`, `is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户卡组';

-- ---------- 6. 用户已购皮肤 / 每干员装配的皮肤 ----------
CREATE TABLE `user_skins` (
  `id`         bigint   NOT NULL AUTO_INCREMENT,
  `user_id`    bigint   NOT NULL,
  `skin_id`    int      NOT NULL,
  `create_time` datetime         DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_skin` (`user_id`, `skin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户皮肤';

CREATE TABLE `user_operator_skin` (
  `id`         bigint   NOT NULL AUTO_INCREMENT,
  `user_id`    bigint   NOT NULL,
  `operator`   varchar(32) NOT NULL COMMENT '干员名(即卡牌名)',
  `skin_id`    int      NOT NULL COMMENT '装配的皮肤',
  `create_time` datetime         DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_oper` (`user_id`, `operator`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每干员独立装配的皮肤';

-- ---------- 7. 对局记录 ----------
CREATE TABLE `match_records` (
  `id`          bigint      NOT NULL AUTO_INCREMENT,
  `room_id`     varchar(64) NOT NULL,
  `player1_id`  bigint      NOT NULL,
  `player2_id`  bigint      NOT NULL,
  `winner_id`   bigint      NOT NULL,
  `rounds`      int         NOT NULL DEFAULT '0'  COMMENT '总回合数',
  `detail`      json                DEFAULT NULL COMMENT '战斗过程记录, 预留',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_p1` (`player1_id`),
  KEY `idx_p2` (`player2_id`),
  KEY `idx_ct` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对局记录';

-- ---------- 8. 抽卡记录 ----------
CREATE TABLE `gacha_log` (
  `id`          bigint   NOT NULL AUTO_INCREMENT,
  `user_id`     bigint   NOT NULL,
  `cost`        int      NOT NULL                COMMENT '本次消耗点券',
  `results`     text                              COMMENT '抽到的卡牌 id 列表',
  `create_time` datetime          DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽卡记录';

-- ---------- 9. 充值流水 ----------
CREATE TABLE `recharge_log` (
  `id`            bigint      NOT NULL AUTO_INCREMENT,
  `user_id`       bigint      NOT NULL,
  `amount`        int         NOT NULL,
  `balance_after` int         NOT NULL,
  `remark`        varchar(100)         DEFAULT NULL,
  `create_time`   datetime            DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_ct`   (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='充值流水';


-- ============================================================================
--  二、基础数据
-- ============================================================================

-- ---------- 66 张卡牌 ----------
INSERT INTO `cards` (`id`, `name`, `cost`, `attack`, `hp`, `effect`, `img`, `rarity`, `description`) VALUES
  (1, '玫兰莎', 1, 2, 1, NULL, '/cards/1.jpg', 1, '罗德岛近卫干员，攻高血薄的速攻单位'),
  (2, '克洛丝', 1, 1, 2, NULL, '/cards/2.jpg', 1, NULL),
  (3, '米格鲁', 2, 1, 4, NULL, '/cards/3.jpg', 1, '重装干员，高生命值前排'),
  (4, '炎熔', 3, 3, 2, 'battlecry:damage_face:2', '/cards/4.jpg', 2, '战吼：对敌方英雄造成2点伤害'),
  (5, '猎蜂', 4, 4, 4, NULL, '/cards/5.jpg', 2, NULL),
  (6, '拉普兰德', 4, 5, 3, NULL, '/cards/6.jpg', 2, NULL),
  (7, '夜刀', 3, 4, 1, 'deathrattle:damage_face:2', '/cards/7.jpg', 2, '亡语：对敌方英雄造成2点伤害'),
  (8, '白面鸮', 5, 3, 5, 'battlecry:heal:4', '/cards/8.jpg', 3, '战吼：为己方英雄回复4点生命'),
  (9, '星熊', 6, 6, 6, NULL, '/cards/9.jpg', 3, NULL),
  (10, '斯卡蒂', 7, 7, 7, NULL, '/cards/10.jpg', 4, NULL),
  (11, '年', 8, 8, 8, 'battlecry:aoe:2', '/cards/11.jpg', 4, '战吼：对所有敌方随从造成2点伤害'),
  (12, '临光', 9, 9, 9, NULL, '/cards/12.jpg', 4, NULL),
  (13, '霜叶', 2, 3, 2, NULL, '/cards/13.jpg', 2, NULL),
  (14, '安洁莉娜', 3, 2, 4, 'battlecry:draw:1', '/cards/14.jpg', 2, '战吼：抽1张牌'),
  (15, '惊蛰', 5, 5, 4, 'battlecry:aoe:1', '/cards/15.jpg', 3, '战吼：对所有敌方随从造成1点伤害'),
  (16, '赫默', 4, 2, 6, 'battlecry:heal:3', '/cards/16.jpg', 2, '战吼：为己方英雄回复3点生命'),
  (17, '银灰', 5, 6, 3, NULL, '/cards/17.jpg', 3, NULL),
  (18, '艾雅法拉', 4, 4, 4, 'deathrattle:aoe:1', '/cards/18.jpg', 3, '亡语：对所有敌方随从造成1点伤害'),
  (19, '泥岩', 6, 4, 8, NULL, '/cards/19.jpg', 3, NULL),
  (20, '能天使', 8, 7, 7, 'battlecry:draw:2', '/cards/20.jpg', 4, '战吼：抽2张牌'),
  (21, '香草', 2, 2, 3, NULL, NULL, 1, '干练的先锋干员'),
  (22, '翎羽', 1, 2, 2, NULL, NULL, 1, '迅捷的杀回先锋'),
  (23, '月见夜', 3, 3, 2, NULL, NULL, 1, '近卫干员'),
  (24, '芬', 1, 1, 3, NULL, NULL, 1, '稳重的先锋'),
  (25, '12F', 3, 2, 3, NULL, NULL, 1, '术师干员'),
  (26, '红豆', 3, 4, 2, NULL, NULL, 2, '杀回先锋'),
  (27, '清道夫', 3, 3, 4, NULL, NULL, 2, '技能回费先锋'),
  (28, '缠丸', 4, 5, 3, NULL, NULL, 2, '近卫干员'),
  (29, '艾丝黛尔', 4, 3, 5, NULL, NULL, 2, '近卫干员'),
  (30, '白金', 3, 3, 3, NULL, NULL, 2, '狙击干员'),
  (31, '蓝毒', 3, 4, 2, NULL, NULL, 2, '狙击干员'),
  (32, '陨星', 5, 4, 3, NULL, NULL, 2, '狙击干员'),
  (33, '守林人', 4, 5, 3, NULL, NULL, 2, '狙击干员'),
  (34, '调香师', 3, 2, 5, NULL, NULL, 2, '医疗干员'),
  (35, '末药', 2, 1, 5, NULL, NULL, 2, '医疗干员'),
  (36, '嘉维尔', 3, 3, 4, NULL, NULL, 2, '医疗干员'),
  (37, '蛇屠箱', 4, 2, 7, NULL, NULL, 2, '重装干员'),
  (38, '古米', 3, 2, 5, NULL, NULL, 2, '重装干员'),
  (39, '德克萨斯', 3, 3, 5, NULL, NULL, 3, '技能回费先锋'),
  (40, '芙兰卡', 4, 5, 3, NULL, NULL, 3, '近卫干员'),
  (41, '普罗旺斯', 4, 6, 3, NULL, NULL, 3, '狙击干员'),
  (42, '华法琳', 4, 3, 4, NULL, NULL, 3, '医疗干员'),
  (43, '可颂', 5, 3, 7, NULL, NULL, 3, '重装干员'),
  (44, '雷蛇', 5, 4, 6, NULL, NULL, 3, '重装干员'),
  (45, '初雪', 4, 2, 5, NULL, NULL, 3, '辅助干员'),
  (46, '梅尔', 4, 3, 3, NULL, NULL, 3, '召唤辅助'),
  (47, '红', 3, 5, 3, NULL, NULL, 3, '特种干员'),
  (48, '食铁兽', 4, 4, 4, NULL, NULL, 3, '特种干员'),
  (49, '陈', 6, 6, 5, NULL, NULL, 4, '近卫干员'),
  (50, '推进之王', 5, 5, 6, NULL, NULL, 4, '技能回费先锋'),
  (51, '夜莺', 6, 3, 7, NULL, NULL, 4, '医疗干员'),
  (52, '闪灵', 5, 3, 6, NULL, NULL, 4, '医疗干员'),
  (53, '塞雷娅', 6, 5, 7, NULL, NULL, 4, '重装干员'),
  (54, '史尔特尔', 7, 8, 5, NULL, NULL, 4, '近卫干员'),
  (55, '风笛', 4, 4, 5, NULL, NULL, 4, '杀回先锋'),
  (56, '麦哲伦', 5, 3, 4, NULL, NULL, 4, '召唤辅助'),
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

-- ---------- 12 个卡框皮肤 ----------
INSERT INTO `skins` (`id`, `name`, `price`, `frame_class`, `operator`, `description`) VALUES
  (1, '罗德岛制式', 0, 'skin-default', NULL, '罗德岛标准制式卡框，蓝白配灰的作战涂装'),
  (2, '莱茵生命·低温实验室', 300, 'skin-ice', NULL, '莱茵生命科研涂装，青蓝冷凝光边'),
  (3, '企鹅物流·急件', 500, 'skin-flame', NULL, '企鹅物流加急货运涂装，橙红警示灯边'),
  (4, '萨卡兹·源石技艺', 800, 'skin-forest', NULL, '萨卡兹源石技艺涂装，荧绿源石辉光'),
  (5, '深海猎人·深渊', 1500, 'skin-holo', NULL, '深海猎人涂装，紫黑深海回响光边'),
  (6, '巴别塔·遗珍', 3000, 'skin-gold', NULL, '巴别塔遗留制式，鎏金蚀刻纹边'),
  (7, '新约·阿米娅', 600, 'skin-amiya', '阿米娅', '阿米娅升变主题，青紫双源石光边'),
  (8, '医师·凯尔希', 800, 'skin-kalts', '凯尔希', '凯尔希医疗主题，白绿医疗辐光环边'),
  (9, '爆破·W', 1000, 'skin-w', 'W', 'W的爆破美学，橙黑爆痕焦边'),
  (10, '雪境·银灰', 1000, 'skin-silverash', '银灰', '银灰雪境领主主题，霜银冷冽光边'),
  (11, '黄昏·史尔特尔', 1200, 'skin-surtr', '史尔特尔', '史尔特尔焚烬主题，赤金烈焰熔边'),
  (12, '荒野·拉普兰德', 800, 'skin-lappland', '拉普兰德', '拉普兰德荒野主题，冷月银紫狼边');

-- ---------- 3 个测试账号（密码统一为 123456）----------
-- 密码 = MD5(PWD_SALT + '123456')，盐定义在 JwtUtil.PWD_SALT
-- 注意：不要直接把明文写进 password 字段，否则登录校验不通过
INSERT INTO `users` (`id`, `username`, `password`, `nickname`, `rating`, `win_count`, `lose_count`, `create_time`, `role`, `balance`, `crystal`, `skin_id`, `status`, `banned_until`, `ban_reason`, `avatar`, `phone`) VALUES
  (1, 'ljh',   'f14c414c97bdba3a59e8b659b0b68792', '李家宏',   1000, 0, 0, '2026-10-23 00:00:00', 'admin', 1000, 300, 0, 0, NULL, NULL, '/avatars/1.png', NULL),
  (2, 'test2', 'f14c414c97bdba3a59e8b659b0b68792', '陪练小号', 1000, 0, 0, '2026-10-23 00:00:00', 'user',  1000, 300, 0, 0, NULL, NULL, '/avatars/1.png', NULL),
  (3, 'user',  'f14c414c97bdba3a59e8b659b0b68792', '测试玩家', 1000, 0, 0, '2026-10-23 00:00:00', 'user',  1000, 300, 0, 0, NULL, NULL, '/avatars/1.png', '17701581891');

-- ---------- 给测试账号发放新手卡牌（id 1-12，与注册时 STARTER_CARDS=12 一致）----------
INSERT INTO `user_cards` (`user_id`, `card_id`)
SELECT u.`id`, c.`id`
FROM `users` u
CROSS JOIN `cards` c
WHERE c.`id` <= 12;

-- ---------- 给测试账号预置一套默认卡组（30 张）----------
INSERT INTO `user_decks` (`user_id`, `name`, `card_ids`) VALUES
  (1, '新手推荐卡组', '[1,2,3,4,5,6,7,8,9,10,11,12,1,2,3,4,5,6,7,8,9,10,11,12,1,2,3,4,5,6]'),
  (2, '新手推荐卡组', '[1,2,3,4,5,6,7,8,9,10,11,12,1,2,3,4,5,6,7,8,9,10,11,12,1,2,3,4,5,6]'),
  (3, '新手推荐卡组', '[1,2,3,4,5,6,7,8,9,10,11,12,1,2,3,4,5,6,7,8,9,10,11,12,1,2,3,4,5,6]');

-- ---------- 运行时数据表（对局记录 / 抽卡 / 充值）保持为空，随使用生成 ----------


-- ============================================================================
--  三、待办：卡面图回填
-- ============================================================================
--  id 21-66 的卡面图尚未生成，img 字段为 NULL，
--  前端会显示默认样式（无卡面）。图片生成后把文件放到
--  frontend/public/cards/ 下，再执行下面这条 SQL 回填：
--
--  UPDATE `cards` SET `img` = CONCAT('/cards/', `id`, '.jpg') WHERE `id` BETWEEN 21 AND 66;
--
--  改完 cards 表后需等 CardCache 的 60s 缓存过期或重启后端才生效。
-- ============================================================================

-- ============================================================================
-- 干员档案介绍（2026-10-23 补全，66 位干员各一句明日方舟风格档案）
-- ============================================================================
-- =====================================================================
USE card_battle;

UPDATE cards SET description='罗德岛行动组A4队长，近卫干员。性格冷静认真，白刃战能力在同期干员中首屈一指。' WHERE id=1;
UPDATE cards SET description='罗德岛行动组A4成员，狙击干员。性格活泼爱开玩笑，射术稳定，能持续压制敌人掩护队友。' WHERE id=2;
UPDATE cards SET description='罗德岛行动组A4成员，重装干员。出身贫寒却乐观坚韧，用大型盾牌为小队挡下所有火力。' WHERE id=3;
UPDATE cards SET description='罗德岛行动组A4成员，术师干员。法术天赋出众但情绪易失控，中二气十足，痴迷研究源石技艺。' WHERE id=4;
UPDATE cards SET description='前地下拳击手，近卫干员。性格直率热情，拳法迅猛，相信自己的双拳胜过任何武器。' WHERE id=5;
UPDATE cards SET description='叙拉古沃尔西尼家族大小姐，近卫干员。随性不羁的狼之少女，源石剑术凌厉而难以捉摸。' WHERE id=6;
UPDATE cards SET description='罗德岛行动预备组A1队长，先锋干员。沉默寡言，执行力极强，永远第一个冲进战场。' WHERE id=7;
UPDATE cards SET description='莱茵生命出身的医疗干员。言语冷淡但医术高明，负责罗德岛战地急救与后方支援。' WHERE id=8;
UPDATE cards SET description='前龙门近卫局特别监察组组长，重装干员。鬼族武士，坚盾与忠义并重，人称「鬼姐」。' WHERE id=9;
UPDATE cards SET description='深海猎人成员，近卫干员。独来独往的 killer whale，持巨剑独行于大地，狩猎海嗣。' WHERE id=10;
UPDATE cards SET description='行踪不明的炎国工匠，重装干员。言谈古风，所铸重器可当千军，喜怒皆成谜。' WHERE id=11;
UPDATE cards SET description='卡兹戴尔骑士世家出身，重装干员。耀骑士玛恩纳之妹，坚盾之下亦有仁心。' WHERE id=12;
UPDATE cards SET description='罗德岛行动预备组A4成员，近卫干员。寡言的青年，挥舞巨型镰刀，沉默却可靠。' WHERE id=13;
UPDATE cards SET description='活泼的浮空信使少女，辅助干员。操纵反重力源石技艺，喜欢恶作剧却也认真可靠。' WHERE id=14;
UPDATE cards SET description='炎国司职吏员，术师干员。雷法精湛，一丝不苟，对纪律与规矩有着近乎执念的坚持。' WHERE id=15;
UPDATE cards SET description='莱茵生命研究员，医疗干员。冷静理性的医师，心中始终装着医学最初的理想。' WHERE id=16;
UPDATE cards SET description='谢拉格雪境之主，近卫干员。喀兰贸易董事长，沉稳睿智，为家族与雪山谋划一切。' WHERE id=17;
UPDATE cards SET description='火山学者，术师干员。听力不佳却感知敏锐，能操纵炽烈的火山源石技艺焚尽来敌。' WHERE id=18;
UPDATE cards SET description='从乌萨斯走出的流浪战士，重装干员。沉默坚毅，岩石般的意志护佑每一位同行者。' WHERE id=19;
UPDATE cards SET description='企鹅物流王牌，狙击干员。性格高调爱炫，双枪火力全开，有她在派对永远不会冷场。' WHERE id=20;
UPDATE cards SET description='来自荒野的兽群饲养员，先锋干员。性格温和腼腆，带着可爱的小兽伙伴一同作战。' WHERE id=21;
UPDATE cards SET description='前警戒警卫队队员，先锋干员。纪律严明，枪术利落，退役后加入罗德岛继续服役。' WHERE id=22;
UPDATE cards SET description='罗德岛行动预备组A4成员，近卫干员。自称「社交界的明星」，风流言谈下藏着可靠本色。' WHERE id=23;
UPDATE cards SET description='罗德岛行动预备组A1成员，先锋干员。认真稳重的模范生，擅长指挥队友与快速突击。' WHERE id=24;
UPDATE cards SET description='来历不明的神秘干员，术师干员。总是戴着防毒面具，操使着古老而危险的源石技艺。' WHERE id=25;
UPDATE cards SET description='元气满满的先锋干员。活泼好动，喜欢黏着前辈撒娇，战场上却意外地靠谱。' WHERE id=26;
UPDATE cards SET description='沉默的战场清道夫，先锋干员。行动迅速隐秘，擅长渗透清理，从不谈论自己的过去。' WHERE id=27;
UPDATE cards SET description='出身东国的剑士少女，近卫干员。温柔有礼，挥舞长刀时却锐不可当。' WHERE id=28;
UPDATE cards SET description='平时温和、失控时危险，近卫干员。巨刃与体术并行，一直在学习控制自己的力量。' WHERE id=29;
UPDATE cards SET description='卡西米尔无胄盟刺客，狙击干员。冷淡寡言，百步之外一击必中，「白金」之名令人胆寒。' WHERE id=30;
UPDATE cards SET description='爱好制作甜点的毒物专家，狙击干员。笑容甜美箭矢淬毒，狡黠又机智。' WHERE id=31;
UPDATE cards SET description='前佣兵狙击手，狙击干员。沉稳可靠的重炮手，她的一炮足以粉碎整面城墙。' WHERE id=32;
UPDATE cards SET description='出身荒野的猎手，狙击干员。孤身行走林间，枪声响起之处猎物应声而倒。' WHERE id=33;
UPDATE cards SET description='帕尔维斯调香师世家出身，医疗干员。以香气抚慰伤病，温柔优雅的战地医师。' WHERE id=34;
UPDATE cards SET description='来自炎国的药剂师，医疗干员。性格温和腼腆，默默守护队友，医者仁心。' WHERE id=35;
UPDATE cards SET description='阿卡胡拉出身的医生，医疗干员。医术野蛮但有效——先把人打晕再治，也算治好了。' WHERE id=36;
UPDATE cards SET description='天真的重装干员。抱着心爱的滑板冲锋陷阵，快乐至上，盾后是全队的笑声。' WHERE id=37;
UPDATE cards SET description='乌萨斯学生自治团成员，重装干员。背着「做饭用」的大盾，是队伍温暖的后勤担当。' WHERE id=38;
UPDATE cards SET description='企鹅物流成员，先锋干员。沉默寡言的狼族女性，剑术利落，任何委托永远准时送达。' WHERE id=39;
UPDATE cards SET description='前盾卫队成员，重装干员。豪爽的大姐头，巨盾横扫，谁也别想越过她的防线。' WHERE id=40;
UPDATE cards SET description='天灾信使，狙击干员。与伙伴狼「霜色」同行，风雨兼程，箭无虚发。' WHERE id=41;
UPDATE cards SET description='活了数百年的血魔医师，医疗干员。罗德岛元老，毒舌刻薄，医术却冠绝当世。' WHERE id=42;
UPDATE cards SET description='前秘装佣兵，重装干员。油滑爱财却极重情义，防爆盾一立，滴水不漏。' WHERE id=43;
UPDATE cards SET description='黑钢国际护卫组长，重装干员。严谨认真的雷神之子，盾与电光构筑起钢铁防线。' WHERE id=44;
UPDATE cards SET description='谢拉格圣山之女，辅助干员。尊贵的圣女，歌声可净化人心，亦蕴含雪山的伟力。' WHERE id=45;
UPDATE cards SET description='莱茵生命防御工程师，辅助干员。机械装置「咪波」的创造者，天才发明家兼吸猫狂人。' WHERE id=46;
UPDATE cards SET description='代号「斯芬克斯」，特种干员。沉默的狼之杀手，只遵从「外婆」的命令展开猎杀。' WHERE id=47;
UPDATE cards SET description='炎国武馆传人，特种干员。如今的影业明星，拳法刚猛，依旧行侠仗义。' WHERE id=48;
UPDATE cards SET description='龙门近卫局局长，近卫干员。「陈晖洁」，佩剑赤霄，雷厉风行的龙之女。' WHERE id=49;
UPDATE cards SET description='格拉斯哥帮之王，先锋干员。流亡的阿斯兰王族，重锤所向，为王先驱。' WHERE id=50;
UPDATE cards SET description='希尔战争孤儿，医疗干员。沉默的圣女「夜莺」，以吟唱疗愈之歌守护众人。' WHERE id=51;
UPDATE cards SET description='前萨卡兹赦罪师，医疗干员。背负巨剑的修女，医术与守护之光普照全队。' WHERE id=52;
UPDATE cards SET description='莱茵生命前防御科主任，重装干员。科学家与坚盾的结合体，理性与守护并存。' WHERE id=53;
UPDATE cards SET description='失忆的流浪者，近卫干员。操纵毁灭性的烈焰巨剑，燃烧一切胆敢阻挡之人。' WHERE id=54;
UPDATE cards SET description='维多利亚皇家近卫学校毕业生，先锋干员。性格爽朗的号手，冲锋号角永不停歇。' WHERE id=55;
UPDATE cards SET description='极北科考探险家，辅助干员。驾驶自造无人机队，永远向着未知疆域前进。' WHERE id=56;
UPDATE cards SET description='罗德岛的领袖，术师干员。少女之王阿米娅，为了受苦之人奔走于大地之间。' WHERE id=57;
UPDATE cards SET description='莱茵生命实验体，术师干员。体内蕴藏毁灭性炎能，任性妄为却渴望被爱。' WHERE id=58;
UPDATE cards SET description='乌萨斯帝国前将军，近卫干员。「指头」赫拉格，为保卫家族与尊严执剑而战。' WHERE id=59;
UPDATE cards SET description='萨卡兹佣兵，狙击干员。喜欢炸弹与戏谑的复仇者，笑里藏刀，无人猜得透她。' WHERE id=60;
UPDATE cards SET description='罗德岛医疗部门主管，医疗干员。博识而冷峻的 Mon3tr 主人，掌握罗德岛最深的秘密。' WHERE id=61;
UPDATE cards SET description='东国玉盛苏家千金，辅助干员。九尾狐少女铃兰，温柔的祈祷中蕴含缚敌之力。' WHERE id=62;
UPDATE cards SET description='罗德岛精锐近卫，近卫干员。链条巨刃火力持久，大姐姐气场十足，战斗永不停歇。' WHERE id=63;
UPDATE cards SET description='阿卡胡拉巨械工程师，重装干员。操纵自制巨型机甲，力大无穷，谁也拦不住。' WHERE id=64;
UPDATE cards SET description='莱茵生命科研成果，特种干员。机械体「温蒂」，以强劲气流把敌人推离阵地。' WHERE id=65;
UPDATE cards SET description='炎国画师，术师干员。「夕」，笔下丹青自成天地，一念山河，一念春秋。' WHERE id=66;

-- ============================================================================
-- 干员技能（2026-10-23）：66 位干员全部含战吼/亡语/嘲讽/冲锋/圣盾/吸血
-- 编码说明见 sql/update-effects.sql；引擎实现见 BattleRoom.resolveAbilities
-- ============================================================================
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
