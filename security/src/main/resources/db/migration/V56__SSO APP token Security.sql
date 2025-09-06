use security;

DROP TABLE IF EXISTS `security`.`security_app_sso`;

DROP TABLE IF EXISTS `security`.`security_app_sso_token`;
DROP TABLE IF EXISTS `security`.`security_bundled_app`;
DROP TABLE IF EXISTS `security`.`security_app_sso_bundle`;

CREATE TABLE `security`.`security_app_sso_bundle`
(
    `ID`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_CODE` char(8)         NOT NULL COMMENT 'Client Code',
    `BUNDLE_NAME` varchar(255)    NOT NULL COMMENT 'SSO Bundle Name',
    `CREATED_BY`  bigint unsigned          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`  timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`  bigint unsigned          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT`  timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_APP_SSO_BUNDLE_CLIENT` (`CLIENT_CODE`, `BUNDLE_NAME`),
    KEY `FK1_APP_SSO_BUNDLE_CLIENT_CODE` (`CLIENT_CODE`),
    CONSTRAINT `FK1_APP_SSO_BUNDLE_CLIENT_CODE` FOREIGN KEY (`CLIENT_CODE`) REFERENCES `security_client` (`CODE`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `security`.`security_bundled_app`
(
    ID         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    BUNDLE_ID  bigint unsigned NOT NULL COMMENT 'Bundle ID',
    APP_CODE   char(64)        NOT NULL COMMENT 'Application Code',
    APP_URL_ID bigint unsigned NOT NULL COMMENT 'Application URL ID',
    CREATED_BY bigint unsigned          DEFAULT NULL COMMENT 'ID of the user who created this row',
    CREATED_AT timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_APP_BUNDLE_APP` (`BUNDLE_ID`, `APP_CODE`),
    KEY `FK1_BUNDLED_APP_URL_ID` (`APP_URL_ID`),
    CONSTRAINT `FK1_BUNDLED_APP_URL_ID` FOREIGN KEY (`APP_URL_ID`) REFERENCES `security_client_url` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    KEY `FK1_BUNDLED_APP_BUNDLE_ID` (`BUNDLE_ID`),
    CONSTRAINT `FK1_BUNDLED_APP_BUNDLE_ID` FOREIGN KEY (`BUNDLE_ID`) REFERENCES `security_app_sso_bundle` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    KEY `FK1_BUNDLED_APP_APP_CODE` (`APP_CODE`),
    CONSTRAINT `FK1_BUNDLED_APP_APP_CODE` FOREIGN KEY (`APP_CODE`) REFERENCES `security_app` (`APP_CODE`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `security`.`security_app_sso_token`
(
    `ID`         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `BUNDLE_ID`  bigint unsigned NOT NULL COMMENT 'Bundle ID',
    `USER_ID`    bigint unsigned NOT NULL COMMENT 'User id',
    `TOKEN`      char(36)        NOT NULL COMMENT 'UUID Token',
    `EXPIRES_AT` timestamp       NOT NULL DEFAULT '2022-01-01 01:01:01' COMMENT 'When the token expires',
    `IP_ADDRESS` varchar(50)     NOT NULL COMMENT 'User IP from where he logged in',
    `CREATED_BY` bigint unsigned          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` timestamp       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    PRIMARY KEY (`ID`),
    UNIQUE KEY `TOKEN` (`TOKEN`),
    KEY `FK1_APP_SSO_TOKEN_USER_ID` (`USER_ID`),
    CONSTRAINT `FK1_APP_SSO_TOKEN_USER_ID` FOREIGN KEY (`USER_ID`) REFERENCES `security_user` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    KEY `FK1_APP_SSO_TOKEN_BUNDLE_ID` (`BUNDLE_ID`),
    CONSTRAINT `FK1_APP_SSO_BUNDLE_USER_ID` FOREIGN KEY (`BUNDLE_ID`) REFERENCES `security_app_sso_bundle` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
