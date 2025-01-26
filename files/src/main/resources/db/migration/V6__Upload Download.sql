use files;

DROP TABLE IF EXISTS file_upload_download;
DROP TABLE IF EXISTS files_upload_download;

CREATE TABLE files_upload_download (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `TYPE` ENUM('UPLOAD', 'DOWNLOAD') NOT NULL COMMENT 'Type of the ZIP',
    `RESOURCE_TYPE` ENUM('STATIC', 'SECURED') NOT NULL COMMENT 'Resource type',
    `CLIENT_CODE` char(8) NOT NULL COMMENT 'Client Code to whom the folder belongs to',
    `PATH` VARCHAR(1024) NOT NULL COMMENT 'Path of the folder',
    `CDN_URL` VARCHAR(1024) NOT NULL COMMENT 'URL in the CDN',	
    `STATUS` ENUM('PENDING', 'DONE', 'ERROR') NOT NULL DEFAULT 'PENDING' COMMENT 'Status of the process',
    `EXCEPTION` text DEFAULT NULL COMMENT 'Exception message if any',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    
    PRIMARY KEY (ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;