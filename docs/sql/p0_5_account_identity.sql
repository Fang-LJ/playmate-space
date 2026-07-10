-- 玩伴空间小程序 P0.5 账号体系迁移 SQL
-- 目标：t_user 作为平台用户账号，t_user_identity 保存微信等第三方授权身份。
-- MySQL 8

USE playmate_space;

ALTER TABLE t_user
  MODIFY COLUMN openid VARCHAR(128) DEFAULT NULL COMMENT '历史兼容字段：微信 openid，后续登录逻辑不依赖',
  MODIFY COLUMN unionid VARCHAR(128) DEFAULT NULL COMMENT '历史兼容字段：微信 unionid，后续登录逻辑不依赖';

ALTER TABLE t_user
  ADD COLUMN email VARCHAR(128) DEFAULT NULL COMMENT '邮箱，P0.5 不校验真实性' AFTER phone,
  ADD COLUMN password_hash VARCHAR(255) DEFAULT NULL COMMENT '密码哈希' AFTER email,
  ADD COLUMN password_set TINYINT NOT NULL DEFAULT 0 COMMENT '是否已设置密码：0 否，1 是' AFTER password_hash,
  ADD COLUMN gender VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '性别：UNKNOWN/MALE/FEMALE/OTHER' AFTER password_set,
  ADD COLUMN address VARCHAR(255) DEFAULT NULL COMMENT '地址，用户自填' AFTER gender,
  ADD COLUMN bio VARCHAR(512) DEFAULT NULL COMMENT '个人简介' AFTER address,
  ADD COLUMN profile_completed TINYINT NOT NULL DEFAULT 0 COMMENT '资料是否已完善' AFTER bio;

ALTER TABLE t_user
  ADD UNIQUE KEY uk_user_phone (phone),
  ADD UNIQUE KEY uk_user_email (email);

CREATE TABLE IF NOT EXISTS t_user_identity (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  user_id BIGINT NOT NULL COMMENT '平台用户 ID',
  identity_type VARCHAR(64) NOT NULL COMMENT '身份类型：WECHAT_MINIPROGRAM 等',
  identifier VARCHAR(128) NOT NULL COMMENT '身份唯一标识，例如 openid',
  unionid VARCHAR(128) DEFAULT NULL COMMENT '微信 unionid',
  appid VARCHAR(128) DEFAULT NULL COMMENT '微信 appid',
  auth_nickname VARCHAR(64) DEFAULT NULL COMMENT '授权昵称',
  auth_avatar_url VARCHAR(512) DEFAULT NULL COMMENT '授权头像',
  raw_profile_json TEXT DEFAULT NULL COMMENT '授权原始资料 JSON',
  last_login_time DATETIME DEFAULT NULL COMMENT '该身份最后登录时间',
  bind_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_identity_type_identifier (identity_type, identifier),
  KEY idx_identity_user (user_id),
  KEY idx_identity_unionid (unionid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户第三方身份表';

INSERT IGNORE INTO t_user_identity (
  user_id,
  identity_type,
  identifier,
  unionid,
  auth_nickname,
  auth_avatar_url,
  last_login_time,
  bind_time,
  create_time,
  update_time,
  delete_flag
)
SELECT
  id,
  'WECHAT_MINIPROGRAM',
  openid,
  unionid,
  nickname,
  avatar_url,
  last_login_time,
  COALESCE(create_time, CURRENT_TIMESTAMP),
  COALESCE(create_time, CURRENT_TIMESTAMP),
  COALESCE(update_time, CURRENT_TIMESTAMP),
  delete_flag
FROM t_user
WHERE openid IS NOT NULL
  AND openid <> ''
  AND delete_flag = 0;

UPDATE t_user
SET profile_completed = CASE
  WHEN nickname IS NOT NULL AND nickname <> ''
    AND avatar_url IS NOT NULL AND avatar_url <> ''
    AND (phone IS NOT NULL OR email IS NOT NULL)
    AND password_set = 1
  THEN 1
  ELSE 0
END;
