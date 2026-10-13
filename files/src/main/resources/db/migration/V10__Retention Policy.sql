USE `files`;

-- Retention by path, decided per client: "files under <appCode>/<PATH> older than N days are removed".
--
-- V8 gave a single file a lifetime, chosen by whoever uploaded it. That only works for callers that
-- can pass one (the internal upload endpoint), so public uploads never expired and the number of days
-- was hard-coded wherever it was needed. This table moves the decision to the folder instead.
--
-- Rows come in two levels:
--   APP     the app owner's client registers a prefix and its default days. Only a registered prefix
--           is ever swept, so the blast radius is limited to folders an app declared as disposable.
--   CLIENT  a client tunes the days of a registered prefix for itself (APPLIES_TO = SELF) or for the
--           clients it manages (APPLIES_TO = MANAGED). It cannot invent a prefix of its own.
--
-- For a file of client C the client's own SELF row wins, then the nearest manager's MANAGED row, then
-- the APP row. RETENTION_DAYS NULL means keep forever, which is how a client switches retention off.
--
-- A file with EXPIRES_AFTER_MINUTES keeps its own lifetime; this policy only applies to files without.
CREATE TABLE IF NOT EXISTS `files`.`files_retention_policy` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `APP_CODE` VARCHAR(64) NOT NULL COMMENT 'App that owns the prefix, the first folder of the path',
    `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'App owner client for APP rows, the overriding client for CLIENT rows',
    `RESOURCE_TYPE` ENUM('STATIC', 'SECURED') NOT NULL COMMENT 'Static or Secured files',
    `PATH` VARCHAR(1024) NOT NULL COMMENT 'Prefix after <appCode>/, no leading or trailing slash, empty for the whole app folder',
    `RETENTION_DAYS` INT UNSIGNED NULL DEFAULT NULL COMMENT 'Days after the last write a file may be removed. NULL means keep forever.',
    `APPLIES_TO` ENUM('SELF', 'MANAGED') NOT NULL DEFAULT 'SELF' COMMENT 'SELF: the client itself, MANAGED: the clients it manages',
    `LEVEL` ENUM('APP', 'CLIENT') NOT NULL COMMENT 'APP: default registered by the app owner, CLIENT: a client override',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    -- PATH is indexed on its first 255 characters: the full 1024 in utf8mb4 is past InnoDB's 3072 byte
    -- key limit, so two prefixes that share their first 255 characters cannot both be registered.
    -- LEVEL is part of the key because the app owner's client holds the APP row and may also want a
    -- CLIENT override for itself; without it the two would collide on (owner, path, SELF).
    UNIQUE KEY `UK_FILES_RETENTION_POLICY` (`APP_CODE`, `CLIENT_CODE`, `RESOURCE_TYPE`, `PATH`(255), `APPLIES_TO`, `LEVEL`),
    KEY `KEY_FILES_RETENTION_POLICY_APP_TYPE` (`APP_CODE`, `RESOURCE_TYPE`)
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
