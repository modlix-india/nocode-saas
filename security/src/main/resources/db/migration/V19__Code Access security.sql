USE security;

CREATE TABLE `security_code_access` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `EMAIL_ID` varchar(256) NOT NULL COMMENT 'Email id of the client',
  `CODE` varchar(256) NOT NULL COMMENT 'unique access code for logging in',
  `APP_ID` bigint unsigned NOT NULL COMMENT 'App id to which this user belongs to.',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client id to which this user belongs to.',
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  PRIMARY KEY (`ID`),
  UNIQUE INDEX `access_code_UNIQUE` (`CODE`) VISIBLE,
  UNIQUE INDEX `EMAIL_APP_CLIENT` (`APP_ID`, `CLIENT_ID`, `EMAIL_ID` ) VISIBLE,
  CONSTRAINT `FK1_CODE_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_CODE_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
  )
  ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
