-- Standalone books use stable member IDs; no activity or fake user records are created.
CREATE TABLE IF NOT EXISTS t_expense_book (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(80) NOT NULL,
 owner_user_id BIGINT NOT NULL,
 share_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
 version INT NOT NULL DEFAULT 1,
 client_request_id VARCHAR(64) NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_book_code (share_code),
 UNIQUE KEY uk_book_request (owner_user_id, client_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS t_book_member (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 book_id BIGINT NOT NULL,
 user_id BIGINT NULL,
 nickname VARCHAR(40) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 UNIQUE KEY uk_book_user (book_id, user_id),
 KEY idx_book_member (book_id),
 CONSTRAINT fk_book_member_book FOREIGN KEY (book_id) REFERENCES t_expense_book(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS t_book_expense (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 book_id BIGINT NOT NULL,
 title VARCHAR(128) NOT NULL,
 category VARCHAR(32) NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 payer_member_id BIGINT NOT NULL,
 split_mode VARCHAR(32) NOT NULL,
 expense_time DATETIME NOT NULL,
 description VARCHAR(512) NULL,
 receipt_file_id BIGINT NULL,
 created_by BIGINT NOT NULL,
 client_request_id VARCHAR(64) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 version INT NOT NULL DEFAULT 1,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_book_expense_request (book_id, created_by, client_request_id),
 KEY idx_book_expenses (book_id, status, expense_time, id),
 CONSTRAINT fk_book_expense_book FOREIGN KEY (book_id) REFERENCES t_expense_book(id),
 CONSTRAINT fk_book_expense_payer FOREIGN KEY (payer_member_id) REFERENCES t_book_member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS t_book_expense_share (
 expense_id BIGINT NOT NULL,
 member_id BIGINT NOT NULL,
 share_amount DECIMAL(12,2) NOT NULL,
 split_ratio DECIMAL(16,4) NOT NULL DEFAULT 1,
 PRIMARY KEY (expense_id, member_id),
 CONSTRAINT fk_book_share_expense FOREIGN KEY (expense_id) REFERENCES t_book_expense(id),
 CONSTRAINT fk_book_share_member FOREIGN KEY (member_id) REFERENCES t_book_member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS t_book_claim (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 book_id BIGINT NOT NULL,
 member_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_book_claim_user (book_id, user_id),
 CONSTRAINT fk_book_claim_book FOREIGN KEY (book_id) REFERENCES t_expense_book(id),
 CONSTRAINT fk_book_claim_member FOREIGN KEY (member_id) REFERENCES t_book_member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
