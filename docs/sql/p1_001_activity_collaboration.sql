-- 玩伴空间小程序 P1 活动协作增强建表 SQL
-- MySQL 8；仅新增表，不修改或删除既有 P0 / P0.5 数据。

USE playmate_space;

-- 行程：最终落地的活动安排；进行中、已完成等展示状态由时间动态计算，不落表。
CREATE TABLE IF NOT EXISTS t_activity_itinerary (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  activity_id BIGINT NOT NULL COMMENT '活动 ID（逻辑关联 t_activity.id）',
  title VARCHAR(128) NOT NULL COMMENT '行程标题',
  itinerary_type VARCHAR(32) NOT NULL DEFAULT 'OTHER' COMMENT '行程类型：TRANSPORT/MEAL/LODGING/SIGHTSEEING/ACTIVITY/OTHER',
  itinerary_date DATE NOT NULL COMMENT '行程日期',
  start_time TIME DEFAULT NULL COMMENT '开始时间；全天行程可为空',
  end_time TIME DEFAULT NULL COMMENT '结束时间；全天行程可为空',
  all_day TINYINT NOT NULL DEFAULT 0 COMMENT '是否全天：0 否，1 是',
  location_name VARCHAR(128) DEFAULT NULL COMMENT '地点名称',
  address VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
  description TEXT DEFAULT NULL COMMENT '行程说明',
  planning_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '规划状态：DRAFT/PENDING_DECISION/CONFIRMED/CANCELED',
  origin_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL/POLL_RESULT',
  origin_poll_id BIGINT DEFAULT NULL COMMENT '来源投票 ID（逻辑关联 t_activity_poll.id）',
  created_by BIGINT NOT NULL COMMENT '创建人用户 ID（逻辑关联 t_user.id）',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，用于投票结果应用冲突检测',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  KEY idx_itinerary_activity_date_time (activity_id, itinerary_date, start_time),
  KEY idx_itinerary_activity_planning_status (activity_id, planning_status),
  KEY idx_itinerary_created_by (created_by),
  KEY idx_itinerary_origin_poll (origin_poll_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动行程表';

-- 投票：可独立使用，也可修改已有行程或生成新行程；结果应用由阶段 C 的业务逻辑控制。
CREATE TABLE IF NOT EXISTS t_activity_poll (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  activity_id BIGINT NOT NULL COMMENT '活动 ID（逻辑关联 t_activity.id）',
  purpose VARCHAR(32) NOT NULL DEFAULT 'GENERAL' COMMENT '用途：GENERAL/UPDATE_ITINERARY/CREATE_ITINERARY',
  decision_type VARCHAR(32) NOT NULL DEFAULT 'OTHER' COMMENT '决策类型：PLACE/TIME/TRANSPORT/CONTENT/RESTAURANT/OTHER',
  target_itinerary_id BIGINT DEFAULT NULL COMMENT '待修改的目标行程 ID（逻辑关联 t_activity_itinerary.id）',
  generated_itinerary_id BIGINT DEFAULT NULL COMMENT '由本投票生成的行程 ID（逻辑关联 t_activity_itinerary.id）',
  title VARCHAR(128) NOT NULL COMMENT '投票标题',
  description TEXT DEFAULT NULL COMMENT '投票说明',
  vote_type VARCHAR(32) NOT NULL DEFAULT 'SINGLE' COMMENT '投票类型：SINGLE/MULTIPLE',
  allow_modify TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许成员补充或修改候选内容：0 否，1 是',
  deadline DATETIME DEFAULT NULL COMMENT '截止时间',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/ACTIVE/CLOSED/CANCELED',
  result_apply_mode VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '结果应用方式：NONE/MANUAL/AUTO',
  result_apply_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '结果应用状态：NOT_REQUIRED/PENDING/APPLIED/REVIEW_REQUIRED/FAILED',
  winner_option_id BIGINT DEFAULT NULL COMMENT '最终确认的胜出选项 ID（逻辑关联 t_activity_poll_option.id）',
  target_itinerary_version INT DEFAULT NULL COMMENT '发起修改行程投票时记录的行程版本',
  itinerary_template JSON DEFAULT NULL COMMENT '生成新行程时已确定的日期、时间、标题等固定字段',
  created_by BIGINT NOT NULL COMMENT '创建人用户 ID（逻辑关联 t_user.id）',
  closed_at DATETIME DEFAULT NULL COMMENT '关闭时间',
  applied_at DATETIME DEFAULT NULL COMMENT '结果应用时间',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  KEY idx_poll_activity_status_deadline (activity_id, status, deadline),
  KEY idx_poll_target_itinerary (target_itinerary_id),
  KEY idx_poll_generated_itinerary (generated_itinerary_id),
  KEY idx_poll_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动投票表';

-- 投票选项：选项的结果负载可描述胜出后要写回或生成的行程字段。
CREATE TABLE IF NOT EXISTS t_activity_poll_option (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  poll_id BIGINT NOT NULL COMMENT '投票 ID（逻辑关联 t_activity_poll.id）',
  option_text VARCHAR(255) NOT NULL COMMENT '选项文本',
  option_description VARCHAR(512) DEFAULT NULL COMMENT '选项说明',
  result_payload JSON DEFAULT NULL COMMENT '胜出后用于更新或生成行程的字段 JSON',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  KEY idx_poll_option_poll (poll_id),
  KEY idx_poll_option_poll_sort (poll_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动投票选项表';

-- 投票记录：同一用户不能对同一选项重复投票；单选限制由阶段 C 的事务逻辑保证。
CREATE TABLE IF NOT EXISTS t_activity_poll_vote (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  poll_id BIGINT NOT NULL COMMENT '投票 ID（逻辑关联 t_activity_poll.id）',
  option_id BIGINT NOT NULL COMMENT '投票选项 ID（逻辑关联 t_activity_poll_option.id）',
  user_id BIGINT NOT NULL COMMENT '投票用户 ID（逻辑关联 t_user.id）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_poll_vote_poll_option_user (poll_id, option_id, user_id),
  KEY idx_poll_vote_poll_user (poll_id, user_id),
  KEY idx_poll_vote_option (option_id),
  KEY idx_poll_vote_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动投票记录表';

-- 预算项：预算总额由预算项汇总，不在活动表中冗余保存。
CREATE TABLE IF NOT EXISTS t_activity_budget_item (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  activity_id BIGINT NOT NULL COMMENT '活动 ID（逻辑关联 t_activity.id）',
  name VARCHAR(128) NOT NULL COMMENT '预算名称',
  category VARCHAR(32) NOT NULL DEFAULT 'OTHER' COMMENT '分类：TRANSPORT/LODGING/TICKET/FOOD/ENTERTAINMENT/SHOPPING/OTHER',
  calculation_mode VARCHAR(32) NOT NULL DEFAULT 'DIRECT_AMOUNT' COMMENT '计算方式：UNIT_X_QUANTITY/DIRECT_AMOUNT',
  unit_price DECIMAL(12,2) DEFAULT NULL COMMENT '单价',
  quantity DECIMAL(12,2) DEFAULT NULL COMMENT '数量',
  estimated_amount DECIMAL(12,2) NOT NULL COMMENT '最终预算金额',
  planned_date DATE DEFAULT NULL COMMENT '预计发生日期',
  related_itinerary_id BIGINT DEFAULT NULL COMMENT '关联行程 ID（逻辑关联 t_activity_itinerary.id）',
  description VARCHAR(512) DEFAULT NULL COMMENT '备注说明',
  created_by BIGINT NOT NULL COMMENT '创建人用户 ID（逻辑关联 t_user.id）',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/VOID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  KEY idx_budget_activity_category (activity_id, category),
  KEY idx_budget_activity_status (activity_id, status),
  KEY idx_budget_created_by (created_by),
  KEY idx_budget_related_itinerary (related_itinerary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动预算项表';

-- 账单：已参与结算的账单通过 VOID 作废，不做物理删除。
CREATE TABLE IF NOT EXISTS t_activity_expense (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  activity_id BIGINT NOT NULL COMMENT '活动 ID（逻辑关联 t_activity.id）',
  title VARCHAR(128) NOT NULL COMMENT '消费标题',
  category VARCHAR(32) NOT NULL DEFAULT 'OTHER' COMMENT '分类：TRANSPORT/LODGING/TICKET/FOOD/ENTERTAINMENT/SHOPPING/OTHER',
  amount DECIMAL(12,2) NOT NULL COMMENT '消费金额',
  payer_user_id BIGINT NOT NULL COMMENT '付款人用户 ID（逻辑关联 t_user.id）',
  expense_time DATETIME NOT NULL COMMENT '消费时间',
  receipt_file_id BIGINT DEFAULT NULL COMMENT '主要付款凭证文件 ID（逻辑关联 t_file.id）',
  description VARCHAR(512) DEFAULT NULL COMMENT '备注说明',
  created_by BIGINT NOT NULL COMMENT '创建人用户 ID（逻辑关联 t_user.id）',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/VOID',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  KEY idx_expense_activity_time (activity_id, expense_time),
  KEY idx_expense_activity_status (activity_id, status),
  KEY idx_expense_payer (payer_user_id),
  KEY idx_expense_created_by (created_by),
  KEY idx_expense_receipt_file (receipt_file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动账单表';

-- 账单分摊：保存每位参与成员的最终承担金额，支持后续部分成员或自定义金额分摊。
CREATE TABLE IF NOT EXISTS t_activity_expense_share (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  expense_id BIGINT NOT NULL COMMENT '账单 ID（逻辑关联 t_activity_expense.id）',
  user_id BIGINT NOT NULL COMMENT '分摊用户 ID（逻辑关联 t_user.id）',
  share_amount DECIMAL(12,2) NOT NULL COMMENT '最终承担金额',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_expense_share_expense_user (expense_id, user_id),
  KEY idx_expense_share_expense (expense_id),
  KEY idx_expense_share_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动账单分摊表';

-- 结算转账记录：结算建议可由账单动态计算，实际转账状态需要留存。
CREATE TABLE IF NOT EXISTS t_activity_settlement (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  activity_id BIGINT NOT NULL COMMENT '活动 ID（逻辑关联 t_activity.id）',
  from_user_id BIGINT NOT NULL COMMENT '付款用户 ID（逻辑关联 t_user.id）',
  to_user_id BIGINT NOT NULL COMMENT '收款用户 ID（逻辑关联 t_user.id）',
  amount DECIMAL(12,2) NOT NULL COMMENT '转账金额',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/COMPLETED/CANCELED',
  completed_at DATETIME DEFAULT NULL COMMENT '完成转账时间',
  operated_by BIGINT DEFAULT NULL COMMENT '最后操作人用户 ID（逻辑关联 t_user.id）',
  remark VARCHAR(512) DEFAULT NULL COMMENT '备注',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  KEY idx_settlement_activity_status (activity_id, status),
  KEY idx_settlement_from_user (from_user_id),
  KEY idx_settlement_to_user (to_user_id),
  KEY idx_settlement_activity_from_to (activity_id, from_user_id, to_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动结算转账表';

-- 照片墙：复用 t_file，仅维护活动与文件的业务关联关系。
CREATE TABLE IF NOT EXISTS t_activity_photo (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  activity_id BIGINT NOT NULL COMMENT '活动 ID（逻辑关联 t_activity.id）',
  file_id BIGINT NOT NULL COMMENT '文件 ID（逻辑关联 t_file.id）',
  uploaded_by BIGINT NOT NULL COMMENT '上传人用户 ID（逻辑关联 t_user.id）',
  taken_at DATETIME DEFAULT NULL COMMENT '拍摄时间',
  description VARCHAR(512) DEFAULT NULL COMMENT '照片说明',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '展示排序号，越小越靠前',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DELETED',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_photo_activity_file (activity_id, file_id),
  KEY idx_photo_activity_created (activity_id, create_time),
  KEY idx_photo_uploaded_by (uploaded_by),
  KEY idx_photo_file (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动照片墙表';
