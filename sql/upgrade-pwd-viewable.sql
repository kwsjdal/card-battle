-- ============================================================================
--  管理员「查看玩家密码」支持：users 增加可逆密文列 pwd_cipher
--  说明：登录校验仍然使用 password(SHA-256 + 随机盐)，不可逆；
--        pwd_cipher 只是额外保存的一份 AES 密文，供管理后台查看明文。
--  执行：mysql -uroot -p card_battle < sql/upgrade-pwd-viewable.sql
-- ============================================================================
USE `card_battle`;

ALTER TABLE `users`
  ADD COLUMN `pwd_cipher` varchar(128) DEFAULT NULL
      COMMENT '密码可逆密文(AES), 仅供管理员查看; 登录校验不使用' AFTER `salt`;

-- 存量账号没有密文：管理员查看时会提示「重置一次密码后即可查看」；
-- 玩家下次登录时系统会自动用登录明文补齐该列。
