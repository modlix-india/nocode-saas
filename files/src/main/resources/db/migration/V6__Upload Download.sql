use files;

DROP TABLE IF EXISTS file_upload_download;

CREATE TABLE file_upload_download (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `TYPE` ENUM('UPLOAD', 'DOWNLOAD') NOT NULL COMMENT 'Type of the ZIP',
    `USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'ID of the user who uploaded or downloaded the ZIP',
    `PATH` VARCHAR(1024) NOT NULL COMMENT 'Path of the folder',
    `CDN_URL` VARCHAR(1024) DEFAULT NULL COMMENT 'CDN URL of the ZIP',
    `IS_DONE` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Flag to indicate if the ZIP is done',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    
    PRIMARY KEY (ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;