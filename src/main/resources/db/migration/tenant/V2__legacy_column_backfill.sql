-- MySQL versions prior to 8.0.29 do not support
-- "ALTER TABLE ... ADD COLUMN IF NOT EXISTS".
-- Use INFORMATION_SCHEMA + dynamic SQL so this migration is idempotent
-- and compatible across MySQL 8.x versions.

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE employees ADD COLUMN password_hash VARCHAR(255)',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'employees'
      AND column_name = 'password_hash'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE employees ADD COLUMN platform_user_id BIGINT',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'employees'
      AND column_name = 'platform_user_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE employees ADD COLUMN role VARCHAR(30)',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'employees'
      AND column_name = 'role'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE employees ADD COLUMN designation VARCHAR(120)',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'employees'
      AND column_name = 'designation'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE employees ADD COLUMN department VARCHAR(120)',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'employees'
      AND column_name = 'department'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE employees ADD COLUMN phone VARCHAR(30)',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'employees'
      AND column_name = 'phone'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE employees ADD COLUMN salary DECIMAL(12,2)',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'employees'
      AND column_name = 'salary'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE employees ADD COLUMN joined_date DATE',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'employees'
      AND column_name = 'joined_date'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE employees ADD COLUMN created_at DATETIME',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'employees'
      AND column_name = 'created_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE employees ADD COLUMN updated_at DATETIME',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'employees'
      AND column_name = 'updated_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE employees ADD COLUMN status VARCHAR(20)',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'employees'
      AND column_name = 'status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE employees SET role = 'EMPLOYEE' WHERE role IS NULL;
UPDATE employees SET status = 'ACTIVE' WHERE status IS NULL;
UPDATE employees SET joined_date = CURDATE() WHERE joined_date IS NULL;
UPDATE employees SET created_at = NOW() WHERE created_at IS NULL;
UPDATE employees SET updated_at = NOW() WHERE updated_at IS NULL;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE team_members ADD COLUMN functional_role VARCHAR(40) NOT NULL DEFAULT ''MEMBER''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'team_members'
      AND column_name = 'functional_role'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
UPDATE team_members SET functional_role = 'MEMBER' WHERE functional_role IS NULL;
