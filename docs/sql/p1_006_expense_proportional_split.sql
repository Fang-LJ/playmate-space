-- P2 按比例分摊前向迁移。
-- 可重复执行：历史账单默认使用 1.0000，不改变已计算出的最终分摊金额。

DELIMITER $$

CREATE PROCEDURE p1_006_add_column_if_missing(
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

CALL p1_006_add_column_if_missing(
  't_activity_expense_share',
  'split_ratio',
  "DECIMAL(16,4) NOT NULL DEFAULT 1.0000 COMMENT '按比例分摊时的比例值' AFTER share_amount"
) $$

DROP PROCEDURE p1_006_add_column_if_missing $$

DELIMITER ;
