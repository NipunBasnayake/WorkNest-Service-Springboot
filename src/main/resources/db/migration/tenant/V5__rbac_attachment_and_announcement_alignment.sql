SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE announcements ADD COLUMN created_by_role VARCHAR(30)',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'announcements'
      AND column_name = 'created_by_role'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE announcements a
LEFT JOIN employees e ON e.id = a.created_by_id
SET a.created_by_role = CASE
    WHEN e.role = 'HR' THEN 'HR'
    ELSE 'ADMIN'
END
WHERE a.created_by_role IS NULL;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE attachments ADD COLUMN file_url VARCHAR(1000)',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'attachments'
      AND column_name = 'file_url'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE attachments ADD COLUMN file_type VARCHAR(120)',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'attachments'
      AND column_name = 'file_type'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE attachments
SET file_url = storage_path
WHERE file_url IS NULL
  AND storage_path IS NOT NULL;

UPDATE attachments
SET file_type = mime_type
WHERE file_type IS NULL
  AND mime_type IS NOT NULL;

ALTER TABLE attachments
    MODIFY COLUMN mime_type VARCHAR(120) NULL,
    MODIFY COLUMN storage_path VARCHAR(500) NULL;
