SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE leave_requests ADD COLUMN last_reminder_sent_for_date DATE',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'leave_requests'
      AND column_name = 'last_reminder_sent_for_date'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE tasks ADD COLUMN last_due_reminder_sent_for_date DATE',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'tasks'
      AND column_name = 'last_due_reminder_sent_for_date'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE announcements ADD COLUMN team_id BIGINT',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'announcements'
      AND column_name = 'team_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE announcements ADD INDEX idx_announcements_team (team_id)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'announcements'
      AND index_name = 'idx_announcements_team'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE announcements ADD CONSTRAINT fk_announcements_team FOREIGN KEY (team_id) REFERENCES teams(id)',
              'SELECT 1')
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'announcements'
      AND constraint_name = 'fk_announcements_team'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
