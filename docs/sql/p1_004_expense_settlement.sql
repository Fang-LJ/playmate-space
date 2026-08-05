-- P2 费用与 AA 结算前向迁移。
-- 可重复执行：保留既有账单、分摊和实际转账历史，不写入动态结算建议。

DELIMITER $$

CREATE PROCEDURE p1_add_column_if_missing(
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

CALL p1_add_column_if_missing('t_activity_expense', 'split_mode', "VARCHAR(32) NOT NULL DEFAULT 'EQUAL' COMMENT '分摊方式：EQUAL/CUSTOM' AFTER payer_user_id") $$
CALL p1_add_column_if_missing('t_activity_expense', 'voided_by', "BIGINT DEFAULT NULL COMMENT '作废操作人用户 ID' AFTER status") $$
CALL p1_add_column_if_missing('t_activity_expense', 'voided_at', "DATETIME DEFAULT NULL COMMENT '作废时间' AFTER voided_by") $$
CALL p1_add_column_if_missing('t_activity_expense', 'void_reason', "VARCHAR(512) DEFAULT NULL COMMENT '作废原因' AFTER voided_at") $$

CALL p1_add_column_if_missing('t_activity_settlement', 'canceled_at', "DATETIME DEFAULT NULL COMMENT '撤销时间' AFTER completed_at") $$
CALL p1_add_column_if_missing('t_activity_settlement', 'canceled_by', "BIGINT DEFAULT NULL COMMENT '撤销操作人用户 ID' AFTER canceled_at") $$
CALL p1_add_column_if_missing('t_activity_settlement', 'cancel_reason', "VARCHAR(512) DEFAULT NULL COMMENT '撤销原因' AFTER canceled_by") $$

DROP PROCEDURE p1_add_column_if_missing $$

DELIMITER ;

