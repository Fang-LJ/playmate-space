-- P3 Round 2.5: uploads that reached object storage but failed DB persistence.
CREATE TABLE IF NOT EXISTS t_file_orphan_cleanup_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  bucket_name VARCHAR(128) NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  reason VARCHAR(64) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_time DATETIME NULL,
  last_error VARCHAR(512) NULL,
  completed_at DATETIME NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  delete_flag TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_orphan_cleanup_object (bucket_name, object_key),
  KEY idx_file_orphan_cleanup_due (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对象存储孤儿文件清理任务';
