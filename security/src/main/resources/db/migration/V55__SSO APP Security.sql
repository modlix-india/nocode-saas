DROP TABLE IF EXISTS `security`.`security_app_sso`;

CREATE TABLE `security`.`security_app_sso`
(
    `ID`         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID`  bigint unsigned NOT NULL COMMENT 'Client ID',
    `APP_ID`     bigint unsigned NOT NULL COMMENT 'Application ID',
    `TO_APP_ID`  bigint unsigned NOT NULL COMMENT 'TO Application ID',
    `CREATED_BY` bigint unsigned          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` bigint unsigned          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_APP_SSO_CLIENT` (`CLIENT_ID`, `APP_ID`, `TO_APP_ID`),
    KEY `FK1_APP_SSO_CLIENT_ID` (`CLIENT_ID`),
    KEY `FK1_APP_SSO_ACCESS_APP_ID` (`APP_ID`),
    CONSTRAINT `FK1_APP_SSO_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK1_APP_SSO_TO_APP_ID` FOREIGN KEY (`TO_APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK1_APP_SSO_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
