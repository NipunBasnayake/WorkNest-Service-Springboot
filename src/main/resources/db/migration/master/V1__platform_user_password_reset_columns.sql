-- Manual master-schema migration script for platform_users.
-- Master DB currently uses spring.jpa.hibernate.ddl-auto=update in this project,
-- so this script is provided for controlled/manual migration environments.

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE platform_users ADD COLUMN password_reset_token_hash VARCHAR(255)',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'platform_users'
      AND column_name = 'password_reset_token_hash'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE platform_users ADD COLUMN password_reset_token_expires_at DATETIME',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'platform_users'
      AND column_name = 'password_reset_token_expires_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE platform_users ADD COLUMN password_change_required BIT(1) NOT NULL DEFAULT b''0''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'platform_users'
      AND column_name = 'password_change_required'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE platform_users ADD INDEX idx_platform_users_password_reset_token_hash (password_reset_token_hash)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'platform_users'
      AND index_name = 'idx_platform_users_password_reset_token_hash'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
