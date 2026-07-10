-- 玩伴空间小程序 P0 初始化 SQL
-- MySQL 8

CREATE DATABASE IF NOT EXISTS playmate_space
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE playmate_space;

CREATE TABLE IF NOT EXISTS t_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  openid VARCHAR(128) DEFAULT NULL COMMENT '历史兼容字段：微信 openid，后续登录逻辑不依赖',
  unionid VARCHAR(128) DEFAULT NULL COMMENT '历史兼容字段：微信 unionid，后续登录逻辑不依赖',
  nickname VARCHAR(64) DEFAULT NULL COMMENT '用户昵称',
  avatar_url VARCHAR(512) DEFAULT NULL COMMENT '头像地址',
  phone VARCHAR(32) DEFAULT NULL COMMENT '手机号，P0.5 不校验真实性',
  email VARCHAR(128) DEFAULT NULL COMMENT '邮箱，P0.5 不校验真实性',
  password_hash VARCHAR(255) DEFAULT NULL COMMENT '密码哈希',
  password_set TINYINT NOT NULL DEFAULT 0 COMMENT '是否已设置密码：0 否，1 是',
  gender VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '性别：UNKNOWN/MALE/FEMALE/OTHER',
  address VARCHAR(255) DEFAULT NULL COMMENT '地址，用户自填',
  bio VARCHAR(512) DEFAULT NULL COMMENT '个人简介',
  profile_completed TINYINT NOT NULL DEFAULT 0 COMMENT '资料是否已完善',
  status VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT '用户状态：NORMAL/DISABLED',
  last_login_time DATETIME DEFAULT NULL COMMENT '最后登录时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  KEY idx_user_openid (openid),
  UNIQUE KEY uk_user_phone (phone),
  UNIQUE KEY uk_user_email (email),
  KEY idx_user_unionid (unionid),
  KEY idx_user_status_delete (status, delete_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

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

CREATE TABLE IF NOT EXISTS t_file (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  file_type VARCHAR(32) NOT NULL COMMENT '文件类型：ACTIVITY_COVER/USER_AVATAR/PHOTO/OTHER',
  bucket_name VARCHAR(128) NOT NULL COMMENT '对象存储 bucket',
  object_key VARCHAR(512) NOT NULL COMMENT '对象存储 key',
  url VARCHAR(1024) DEFAULT NULL COMMENT '文件访问地址',
  thumb_url VARCHAR(1024) DEFAULT NULL COMMENT '缩略图地址',
  original_name VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
  file_ext VARCHAR(32) DEFAULT NULL COMMENT '文件扩展名',
  size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小，单位 byte',
  content_type VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
  upload_user_id BIGINT NOT NULL COMMENT '上传用户 ID',
  related_type VARCHAR(32) DEFAULT NULL COMMENT '关联业务类型',
  related_id BIGINT DEFAULT NULL COMMENT '关联业务 ID',
  status VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT '文件状态：NORMAL/DELETED',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_object_key (object_key),
  KEY idx_file_upload_user (upload_user_id),
  KEY idx_file_related (related_type, related_id),
  KEY idx_file_type_delete (file_type, delete_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件元数据表';

CREATE TABLE IF NOT EXISTS t_activity (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  activity_name VARCHAR(128) NOT NULL COMMENT '活动名称',
  activity_type VARCHAR(32) NOT NULL COMMENT '活动类型：TRAVEL/MEAL/TEAM_BUILDING/BIRTHDAY/CAMPING/DRIVE/BOARD_GAME/OTHER',
  share_code VARCHAR(64) NOT NULL COMMENT '分享码，用于邀请链接',
  cover_file_id BIGINT DEFAULT NULL COMMENT '活动封面文件 ID',
  cover_url VARCHAR(1024) DEFAULT NULL COMMENT '活动封面 URL 快照',
  location_name VARCHAR(128) DEFAULT NULL COMMENT '地点名称',
  address VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
  start_date DATE DEFAULT NULL COMMENT '开始日期',
  end_date DATE DEFAULT NULL COMMENT '结束日期',
  description TEXT DEFAULT NULL COMMENT '活动描述',
  creator_user_id BIGINT NOT NULL COMMENT '创建人用户 ID',
  status VARCHAR(32) NOT NULL DEFAULT 'PLANNING' COMMENT '活动状态：PLANNING/ONGOING/ENDED/CANCELED',
  member_count INT NOT NULL DEFAULT 1 COMMENT '成员数量冗余',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_share_code (share_code),
  KEY idx_activity_creator (creator_user_id),
  KEY idx_activity_status_delete (status, delete_flag),
  KEY idx_activity_time (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动表';

CREATE TABLE IF NOT EXISTS t_activity_member (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  activity_id BIGINT NOT NULL COMMENT '活动 ID',
  user_id BIGINT NOT NULL COMMENT '用户 ID',
  role VARCHAR(32) NOT NULL DEFAULT 'MEMBER' COMMENT '角色：CREATOR/MEMBER',
  member_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '成员状态：ACTIVE/REMOVED',
  activity_nickname VARCHAR(64) DEFAULT NULL COMMENT '活动内昵称',
  join_source VARCHAR(32) NOT NULL DEFAULT 'CREATE' COMMENT '加入来源：CREATE/SHARE',
  join_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  removed_time DATETIME DEFAULT NULL COMMENT '移除时间',
  removed_by BIGINT DEFAULT NULL COMMENT '移除操作者用户 ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_member_activity_user (activity_id, user_id),
  KEY idx_activity_member_user (user_id),
  KEY idx_activity_member_role_status (activity_id, role, member_status),
  KEY idx_activity_member_status_delete (member_status, delete_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动成员表';
