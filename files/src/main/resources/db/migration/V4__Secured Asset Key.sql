DROP TABLE IF EXISTS files.`files_secured_access_key`;

CREATE TABLE files.`files_secured_access_keys`(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `PATH` VARCHAR(1024) COLLATE `utf8mb4_unicode_ci` NOT NULL COMMENT 'Path which needs to be secured.',
    `ACCESS_KEY` CHAR(15) COLLATE `utf8mb4_unicode_ci` NOT NULL COMMENT 'Key used for securing the file.',
    `ACCESS_TILL` TIMESTAMP NOT NULL COMMENT 'Time which the path can be accessed',
    `ACCESS_LIMIT` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Maximum times in which the file can be accessed',
    `ACCESSED_COUNT` BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT 'Tracks count of file accessed',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_ACCESS_KEY` (`ACCESS_KEY`)
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
