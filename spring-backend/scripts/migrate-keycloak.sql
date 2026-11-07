-- Run against librarymanagement after taking a backup. No users/loans are deleted.
SET @statement = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
   AND table_name='users' AND column_name='keycloak_id')=0,
  'ALTER TABLE users ADD COLUMN keycloak_id VARCHAR(255) COLLATE utf8mb4_bin NULL',
  'SELECT 1');
PREPARE migration FROM @statement;
EXECUTE migration;
DEALLOCATE PREPARE migration;

SET @statement = IF(
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
   AND table_name='users' AND column_name='keycloak_id' AND non_unique=0)=0,
  'ALTER TABLE users ADD CONSTRAINT uk_users_keycloak_id UNIQUE (keycloak_id)',
  'SELECT 1');
PREPARE migration FROM @statement;
EXECUTE migration;
DEALLOCATE PREPARE migration;

-- Username is display data, not identity. Legacy and new users may have the same name.
SET @old_username_index = (
  SELECT index_name FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='users' AND column_name='username'
    AND non_unique=0 AND index_name<>'PRIMARY' LIMIT 1);
SET @statement = IF(@old_username_index IS NULL, 'SELECT 1',
  CONCAT('ALTER TABLE users DROP INDEX `', REPLACE(@old_username_index, '`', '``'), '`'));
PREPARE migration FROM @statement;
EXECUTE migration;
DEALLOCATE PREPARE migration;

-- Retain legacy password hashes as historical data; new Keycloak profiles store no password.
SET @statement = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
   AND table_name='users' AND column_name='password')=1,
  'ALTER TABLE users MODIFY COLUMN password VARCHAR(255) NULL', 'SELECT 1');
PREPARE migration FROM @statement;
EXECUTE migration;
DEALLOCATE PREPARE migration;
