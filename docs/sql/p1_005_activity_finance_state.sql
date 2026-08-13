-- P2 费用一致性控制前向迁移。
-- 可重复执行：不删除历史账单，旧账单的 client_request_id 保持 NULL。

CREATE TABLE IF NOT EXISTS t_activity_finance_state (
  activity_id BIGINT NOT NULL COMMENT '活动 ID',
  finance_version BIGINT NOT NULL DEFAULT 0 COMMENT '费用事实数据版本',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动费用状态表';

DELIMITER $$

CREATE PROCEDURE p1_005_add_column_if_missing(
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

CREATE PROCEDURE p1_005_add_index_if_missing(
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

CALL p1_005_add_column_if_missing(
  't_activity_expense',
  'client_request_id',
  "VARCHAR(64) DEFAULT NULL COMMENT '新增账单幂等请求 ID' AFTER created_by"
) $$

CALL p1_005_add_index_if_missing(
  't_activity_expense',
  'uk_expense_create_request',
  'UNIQUE KEY uk_expense_create_request (activity_id, created_by, client_request_id)'
) $$

DROP PROCEDURE p1_005_add_index_if_missing $$
DROP PROCEDURE p1_005_add_column_if_missing $$

DELIMITER ;
