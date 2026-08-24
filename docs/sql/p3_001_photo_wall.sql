-- P3 照片墙 Round 1 前向迁移。
-- MySQL 8；可重复执行，不删除既有活动、文件或照片数据。
-- 旧照片保持未审核状态：PENDING + HIDDEN，绝不自动放行。

USE playmate_space;

DELIMITER $$

CREATE PROCEDURE p3_001_add_column_if_missing(
  IN p_table_name VARCHAR(64),
  IN p_column_name VARCHAR(64),
  IN p_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = p_table_name AND column_name = p_column_name
  ) THEN
    SET @sql = CONCAT('ALTER TABLE ', p_table_name, ' ADD COLUMN ', p_column_name, ' ', p_definition);
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$

CREATE PROCEDURE p3_001_add_index_if_missing(
  IN p_table_name VARCHAR(64),
  IN p_index_name VARCHAR(64),
  IN p_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = p_table_name AND index_name = p_index_name
  ) THEN
    SET @sql = CONCAT('ALTER TABLE ', p_table_name, ' ADD ', p_definition);
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$

-- t_file 保留既有公开 URL 字段，新增字段只为未来私有 PHOTO 和文件生命周期建模。
CALL p3_001_add_column_if_missing('t_file', 'access_level', "VARCHAR(32) NOT NULL DEFAULT 'PUBLIC' COMMENT '访问级别：PUBLIC/PRIVATE'") $$
CALL p3_001_add_column_if_missing('t_file', 'lifecycle_status', "VARCHAR(32) NOT NULL DEFAULT 'BOUND' COMMENT '文件生命周期：TEMP/BOUND/DELETING/DELETED'") $$
CALL p3_001_add_column_if_missing('t_file', 'storage_provider', "VARCHAR(32) NOT NULL DEFAULT 'MINIO' COMMENT '存储提供方：MINIO/COS'") $$
CALL p3_001_add_column_if_missing('t_file', 'width', "INT DEFAULT NULL COMMENT '图片实际宽度'") $$
CALL p3_001_add_column_if_missing('t_file', 'height', "INT DEFAULT NULL COMMENT '图片实际高度'") $$
CALL p3_001_add_column_if_missing('t_file', 'thumb_object_key', "VARCHAR(512) DEFAULT NULL COMMENT '照片墙缩略图对象 key'") $$
CALL p3_001_add_column_if_missing('t_file', 'preview_object_key', "VARCHAR(512) DEFAULT NULL COMMENT '快速预览图对象 key'") $$
CALL p3_001_add_column_if_missing('t_file', 'bound_at', "DATETIME DEFAULT NULL COMMENT '绑定业务时间'") $$
CALL p3_001_add_column_if_missing('t_file', 'expire_at', "DATETIME DEFAULT NULL COMMENT 'TEMP 文件过期清理时间'") $$

-- 三个维度独立：业务记录、内容审核结论、普通成员可见性。
CALL p3_001_add_column_if_missing('t_activity_photo', 'audit_status', "VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '内容审核状态：PENDING/APPROVED/REJECTED'") $$
CALL p3_001_add_column_if_missing('t_activity_photo', 'visibility_status', "VARCHAR(32) NOT NULL DEFAULT 'HIDDEN' COMMENT '照片可见状态：NORMAL/REVIEWING/HIDDEN'") $$
CALL p3_001_add_column_if_missing('t_activity_photo', 'latest_audit_task_id', "BIGINT DEFAULT NULL COMMENT '最近一次内容审核任务 ID'") $$
CALL p3_001_add_column_if_missing('t_activity_photo', 'like_count', "INT NOT NULL DEFAULT 0 COMMENT '有效点赞数冗余计数'") $$
CALL p3_001_add_column_if_missing('t_activity_photo', 'deleted_by', "BIGINT DEFAULT NULL COMMENT '删除操作者用户 ID'") $$
CALL p3_001_add_column_if_missing('t_activity_photo', 'deleted_at', "DATETIME DEFAULT NULL COMMENT '业务删除时间'") $$
CALL p3_001_add_column_if_missing('t_activity_photo', 'version', "INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本'") $$

CALL p3_001_add_index_if_missing('t_activity_photo', 'idx_photo_activity_visible_time',
  'KEY idx_photo_activity_visible_time (activity_id, status, audit_status, visibility_status, create_time, id)') $$
CALL p3_001_add_index_if_missing('t_activity_photo', 'idx_photo_activity_uploader_time',
  'KEY idx_photo_activity_uploader_time (activity_id, uploaded_by, status, create_time, id)') $$
CALL p3_001_add_index_if_missing('t_activity_photo', 'idx_photo_activity_visible_like',
  'KEY idx_photo_activity_visible_like (activity_id, status, audit_status, visibility_status, like_count, id)') $$
CALL p3_001_add_index_if_missing('t_activity_photo', 'idx_photo_latest_audit_task',
  'KEY idx_photo_latest_audit_task (latest_audit_task_id)') $$

CREATE TABLE IF NOT EXISTS t_activity_photo_like (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  activity_id BIGINT NOT NULL COMMENT '活动 ID（逻辑关联 t_activity.id）',
  photo_id BIGINT NOT NULL COMMENT '照片 ID（逻辑关联 t_activity_photo.id）',
  user_id BIGINT NOT NULL COMMENT '点赞用户 ID（逻辑关联 t_user.id）',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '点赞状态：ACTIVE/CANCELED',
  liked_at DATETIME DEFAULT NULL COMMENT '最近点赞时间',
  canceled_at DATETIME DEFAULT NULL COMMENT '最近取消点赞时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_photo_like_photo_user (photo_id, user_id),
  KEY idx_photo_like_photo_status (photo_id, status),
  KEY idx_photo_like_activity_user_status (activity_id, user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动照片点赞关系表';

CREATE TABLE IF NOT EXISTS t_activity_photo_report (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  activity_id BIGINT NOT NULL COMMENT '活动 ID（逻辑关联 t_activity.id）',
  photo_id BIGINT NOT NULL COMMENT '照片 ID（逻辑关联 t_activity_photo.id）',
  reporter_user_id BIGINT NOT NULL COMMENT '举报用户 ID（逻辑关联 t_user.id）',
  reason_code VARCHAR(32) NOT NULL COMMENT '举报原因：SEXUAL/VIOLENCE/ILLEGAL/PRIVACY/OTHER',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '举报处理状态：PENDING/CONFIRMED/DISMISSED',
  audit_task_id BIGINT DEFAULT NULL COMMENT '二次审核任务 ID',
  handled_at DATETIME DEFAULT NULL COMMENT '处理完成时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_photo_report_photo_reporter (photo_id, reporter_user_id),
  KEY idx_photo_report_activity_status_time (activity_id, status, create_time),
  KEY idx_photo_report_photo_status (photo_id, status),
  KEY idx_photo_reporter_time (reporter_user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动照片举报表';

CREATE TABLE IF NOT EXISTS t_activity_photo_audit_task (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  activity_id BIGINT NOT NULL COMMENT '活动 ID（逻辑关联 t_activity.id）',
  photo_id BIGINT NOT NULL COMMENT '照片 ID（逻辑关联 t_activity_photo.id）',
  report_id BIGINT DEFAULT NULL COMMENT '来源举报 ID（逻辑关联 t_activity_photo_report.id）',
  scene VARCHAR(32) NOT NULL COMMENT '审核场景：INITIAL/REPORT_RECHECK',
  provider VARCHAR(32) NOT NULL COMMENT '审核提供方：MOCK/WECHAT',
  provider_trace_id VARCHAR(128) DEFAULT NULL COMMENT '外部审核服务追踪 ID',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING/SUBMITTED/APPROVED/REJECTED/RETRY_WAIT/CANCELED',
  retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
  next_retry_time DATETIME DEFAULT NULL COMMENT '下次重试时间',
  last_error VARCHAR(512) DEFAULT NULL COMMENT '最近一次提交或回调错误',
  result_code VARCHAR(64) DEFAULT NULL COMMENT '审核结果代码',
  result_detail JSON DEFAULT NULL COMMENT '审核结果详情 JSON',
  submitted_at DATETIME DEFAULT NULL COMMENT '提交外部审核服务时间',
  completed_at DATETIME DEFAULT NULL COMMENT '审核完成时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_photo_audit_provider_trace (provider, provider_trace_id),
  KEY idx_photo_audit_photo_time (photo_id, create_time),
  KEY idx_photo_audit_status_retry (status, next_retry_time),
  KEY idx_photo_audit_report (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动照片内容审核持久任务表';

DROP PROCEDURE p3_001_add_index_if_missing $$
DROP PROCEDURE p3_001_add_column_if_missing $$

DELIMITER ;
