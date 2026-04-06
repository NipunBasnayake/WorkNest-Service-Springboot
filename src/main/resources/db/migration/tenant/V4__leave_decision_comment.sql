SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE leave_requests ADD COLUMN decision_comment TEXT',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'leave_requests'
      AND column_name = 'decision_comment'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

