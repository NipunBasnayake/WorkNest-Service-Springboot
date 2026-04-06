-- Add hash-at-rest support for refresh tokens.
-- Backfill should be completed by application dual-read migration flow.

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE refresh_tokens ADD COLUMN token_hash VARCHAR(64)',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'refresh_tokens'
      AND column_name = 'token_hash'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE refresh_tokens ADD UNIQUE INDEX uk_refresh_tokens_token_hash (token_hash)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'refresh_tokens'
      AND index_name = 'uk_refresh_tokens_token_hash'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

