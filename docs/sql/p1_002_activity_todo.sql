-- 玩伴空间 P1 活动待办生命周期迁移。
-- 仅新增表；可重复执行，不删除或改写既有数据。

USE playmate_space;

CREATE TABLE IF NOT EXISTS t_activity_todo (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  activity_id BIGINT NOT NULL COMMENT '活动 ID（逻辑关联 t_activity.id）',
  todo_type VARCHAR(32) NOT NULL COMMENT 'POLL_VOTE/POLL_RESULT_CONFIRM/MANUAL_REMINDER/SETTLEMENT_PAYMENT',
  source_type VARCHAR(32) NOT NULL COMMENT 'POLL/ITINERARY/SETTLEMENT/MANUAL',
  source_id BIGINT DEFAULT NULL COMMENT '来源业务 ID',
  source_key VARCHAR(128) NOT NULL COMMENT '自动待办幂等键，如 POLL_VOTE:{pollId}',
  title VARCHAR(128) NOT NULL COMMENT '待办标题',
  content VARCHAR(512) DEFAULT NULL COMMENT '待办内容',
  action_type VARCHAR(32) NOT NULL COMMENT 'VIEW_POLL/CONFIRM_POLL_RESULT/ACK_REMINDER/VIEW_SETTLEMENT',
  due_time DATETIME DEFAULT NULL COMMENT '到期或提示时间',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/COMPLETED/CANCELED/EXPIRED',
  auto_generated TINYINT NOT NULL DEFAULT 1 COMMENT '是否自动生成：0 否，1 是',
  created_by BIGINT DEFAULT NULL COMMENT '创建人用户 ID',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_todo_activity_source_key (activity_id, source_key),
  KEY idx_activity_todo_activity_status_due (activity_id, status, due_time),
  KEY idx_activity_todo_source (source_type, source_id),
  KEY idx_activity_todo_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动待办生命周期表';

CREATE TABLE IF NOT EXISTS t_activity_todo_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  todo_id BIGINT NOT NULL COMMENT '待办 ID（逻辑关联 t_activity_todo.id）',
  user_id BIGINT NOT NULL COMMENT '用户 ID（逻辑关联 t_user.id）',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/COMPLETED/CANCELED',
  read_time DATETIME DEFAULT NULL COMMENT '首次阅读时间',
  completed_time DATETIME DEFAULT NULL COMMENT '完成时间',
  completion_reason VARCHAR(32) DEFAULT NULL COMMENT 'VOTED/RESULT_CONFIRMED/ACKNOWLEDGED/AUTO_CLOSED/ACTIVITY_CANCELED',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_todo_user_todo_user (todo_id, user_id),
  KEY idx_activity_todo_user_user_status (user_id, status),
  KEY idx_activity_todo_user_todo_status (todo_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动待办用户处理状态表';
