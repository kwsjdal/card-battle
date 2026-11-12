-- ============================================================================
--  用户卡组增加「出战卡组」标记（匹配对局时使用指定卡组，而不是只用最新一套）
--  执行：mysql -uroot -p card_battle < sql/upgrade-deck-default.sql
--  说明：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，重复执行若报 duplicate column
--        说明已升级过，忽略该错误即可（后面的 UPDATE 仍可单独执行）。
-- ============================================================================
USE `card_battle`;

ALTER TABLE `user_decks`
  ADD COLUMN `is_default` tinyint NOT NULL DEFAULT 0
      COMMENT '是否出战卡组: 1=匹配对局时使用(每人最多1套)' AFTER `card_ids`;

ALTER TABLE `user_decks`
  ADD INDEX `idx_user_default` (`user_id`, `is_default`);

-- 已有玩家: 把各自最新一套卡组设为出战, 保证升级后行为不变(原来就是取最新一套)
UPDATE `user_decks` d
  JOIN (
    SELECT user_id, MAX(id) AS max_id FROM `user_decks` GROUP BY user_id
  ) t ON d.id = t.max_id
SET d.is_default = 1
WHERE IFNULL(d.is_default, 0) = 0;
