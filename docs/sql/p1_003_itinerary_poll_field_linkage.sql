-- 玩伴空间 P1 行程 × 投票字段联动增量迁移
-- MySQL 8；可重复执行，不要求删除或重建已有数据。

USE playmate_space;

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_missing$$
CREATE PROCEDURE add_column_if_missing(
  IN table_name_value VARCHAR(64),
  IN column_name_value VARCHAR(64),
  IN column_definition_value TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND column_name = column_name_value
  ) THEN
    SET @ddl = CONCAT(
      'ALTER TABLE `', table_name_value, '` ADD COLUMN `',
      column_name_value, '` ', column_definition_value
    );
    PREPARE statement_to_run FROM @ddl;
    EXECUTE statement_to_run;
    DEALLOCATE PREPARE statement_to_run;
  END IF;
END$$

CALL add_column_if_missing('t_activity_itinerary', 'transport_mode',
  'VARCHAR(64) DEFAULT NULL COMMENT ''交通方式'' AFTER `all_day`')$$
CALL add_column_if_missing('t_activity_itinerary', 'departure_name',
  'VARCHAR(128) DEFAULT NULL COMMENT ''出发地'' AFTER `transport_mode`')$$
CALL add_column_if_missing('t_activity_itinerary', 'destination_name',
  'VARCHAR(128) DEFAULT NULL COMMENT ''目的地'' AFTER `departure_name`')$$
CALL add_column_if_missing('t_activity_itinerary', 'route_detail',
  'VARCHAR(512) DEFAULT NULL COMMENT ''路线说明'' AFTER `destination_name`')$$
CALL add_column_if_missing('t_activity_itinerary', 'meal_type',
  'VARCHAR(64) DEFAULT NULL COMMENT ''用餐类型'' AFTER `route_detail`')$$
CALL add_column_if_missing('t_activity_itinerary', 'restaurant_name',
  'VARCHAR(128) DEFAULT NULL COMMENT ''具体餐厅'' AFTER `meal_type`')$$
CALL add_column_if_missing('t_activity_itinerary', 'activity_content',
  'VARCHAR(128) DEFAULT NULL COMMENT ''活动内容'' AFTER `restaurant_name`')$$
CALL add_column_if_missing('t_activity_poll', 'decision_scope',
  'JSON DEFAULT NULL COMMENT ''本次投票允许修改的行程字段白名单'' AFTER `itinerary_template`')$$

DROP PROCEDURE IF EXISTS add_column_if_missing$$

DELIMITER ;

CREATE TABLE IF NOT EXISTS t_activity_poll_application (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  activity_id BIGINT NOT NULL COMMENT '活动 ID（逻辑关联 t_activity.id）',
  poll_id BIGINT NOT NULL COMMENT '投票 ID（逻辑关联 t_activity_poll.id）',
  target_itinerary_id BIGINT NOT NULL COMMENT '实际应用的行程 ID（逻辑关联 t_activity_itinerary.id）',
  winner_option_id BIGINT NOT NULL COMMENT '胜出选项 ID（逻辑关联 t_activity_poll_option.id）',
  before_snapshot JSON DEFAULT NULL COMMENT '应用前行程字段快照',
  after_snapshot JSON NOT NULL COMMENT '应用后行程字段快照',
  changed_fields JSON NOT NULL COMMENT '实际修改字段及前后值',
  unchanged_fields JSON NOT NULL COMMENT '本次保持不变字段及值',
  applied_by BIGINT NOT NULL COMMENT '应用操作人用户 ID（逻辑关联 t_user.id）',
  applied_at DATETIME NOT NULL COMMENT '应用时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  delete_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_poll_application_poll (poll_id),
  KEY idx_poll_application_activity (activity_id, applied_at),
  KEY idx_poll_application_itinerary (target_itinerary_id, applied_at),
  KEY idx_poll_application_operator (applied_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投票结果应用记录表';

