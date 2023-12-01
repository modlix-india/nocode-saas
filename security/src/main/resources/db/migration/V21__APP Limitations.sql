USE security;

DROP TABLE IF EXISTS `security_app_limitations` ;

CREATE TABLE `security_app_limitations` (
    `ID` BIGINT unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `APP_ID` BIGINT unsigned NOT NULL COMMENT 'App id to which this user belongs to.',  
    `CLIENT_ID` BIGINT unsigned NOT NULL COMMENT 'Client id to which this user belongs to.',
    `NAME` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Name of the object which need to be limited.',
    `LIMIT` BIGINT unsigned NOT NULL COMMENT 'Number of times to perform this task for selected app and client.',
    `CREATED_BY` BIGINT unsigned DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
     PRIMARY KEY (`ID`),
     UNIQUE INDEX `UK1_LIMIT_APP_CLIENT` (`APP_ID`,`CLIENT_ID`, `LIMIT`) VISIBLE,
      CONSTRAINT `FK1_LIMIT_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
      CONSTRAINT `FK2_LIMIT_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `security_app_owner_limitations`;

CREATE TABLE `security_app_owner_limitations` (
    `ID` BIGINT unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `APP_ID` BIGINT unsigned NOT NULL COMMENT 'App id to which this user belongs to.',  
    `CLIENT_ID` BIGINT unsigned NOT NULL COMMENT 'Client id to which this user belongs to.',
    `NAME` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Name of the object which need to be limited.',
    `LIMIT` BIGINT unsigned NOT NULL COMMENT 'Number of times to perform this task for selected app and client.',
    `CREATED_BY` BIGINT unsigned DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
     PRIMARY KEY (`ID`),
     UNIQUE INDEX `UK1_OWNER_LIMIT_APP_CLIENT` (`APP_ID`,`CLIENT_ID`, `LIMIT`) VISIBLE,
      CONSTRAINT `FK1_OWNER_LIMIT_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
      CONSTRAINT `FK2_OWNER_LIMIT_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

