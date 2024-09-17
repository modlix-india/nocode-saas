    use security;

    DROP TABLE IF EXISTS `security_app_reg_integration_scopes`;
    DROP TABLE IF EXISTS `security_app_reg_integration`;
    DROP TABLE IF EXISTS `security_integration_scopes`;
    DROP TABLE IF EXISTS `security_integration_tokens`;
    DROP TABLE IF EXISTS `security_integration`;

    -- integration table
    CREATE TABLE `security_integration` (
        `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

        `CLIENT_ID` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Client ID',
        `NAME` VARCHAR(256) NOT NULL COMMENT 'Integration Name',
        `PLATFORM` VARCHAR(256) NOT NULL COMMENT 'Integration Platform Name',
        `DESCRIPTION` TEXT DEFAULT NULL COMMENT 'Integration Description',

        `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
        `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
        `UPDATED_BY` BIGINT UNSIGNED NULL COMMENT 'ID of the user who updated this row',
        `UPDATED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',


        PRIMARY KEY (`ID`),

        UNIQUE KEY (`CLIENT_ID`, `NAME`, `PLATFORM`),
        CONSTRAINT `FK1_INTEGRATION_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
    )
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

    -- integration scopes
    CREATE TABLE `security_integration_scopes` (
        `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

        `CLIENT_ID` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Client ID',
        `INTEGRATION_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Integration ID',
        `NAME` VARCHAR(256) NOT NULL COMMENT 'Scope Name',
        `DESCRIPTION` TEXT DEFAULT NULL COMMENT 'Scope Description',

        `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
        `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
        `UPDATED_BY` BIGINT UNSIGNED NULL COMMENT 'ID of the user who updated this row',
        `UPDATED_AT` TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

        PRIMARY KEY (`ID`),

        UNIQUE KEY (`CLIENT_ID`, `INTEGRATION_ID`, `NAME`),
        CONSTRAINT `FK1_INTEGRATION_SCOPE_INTEGRATION_ID` FOREIGN KEY (`INTEGRATION_ID`) REFERENCES `security_integration` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
        CONSTRAINT `FK2_INTEGRATION_SCOPE_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
    )
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

    -- app integration scopes
    CREATE TABLE `security_app_reg_integration` (
        `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

        `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'App ID',
        `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
        `INTEGRATION_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Integration ID',


        `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
        `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

        PRIMARY KEY (`ID`),

        UNIQUE KEY (`APP_ID`, `CLIENT_ID`, `INTEGRATION_ID`),
        CONSTRAINT `FK1_APP_REG_INTEGRATION_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
        CONSTRAINT `FK2_APP_REG_INTEGRATION_INTEGRATION_ID` FOREIGN KEY (`INTEGRATION_ID`) REFERENCES `security_integration` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
        CONSTRAINT `FK3_APP_REG_INTEGRATION_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
    )
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

    CREATE TABLE `security_app_reg_integration_scopes` (
        `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

        `APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'App ID',
        `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
        `INTEGRATION_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Integration ID',
        `INTEGRATION_SCOPE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Integration Scope ID',

        `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
        `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

        PRIMARY KEY (`ID`),

        UNIQUE KEY (`INTEGRATION_ID`, `INTEGRATION_SCOPE_ID`),
        CONSTRAINT `FK1_APP_REG_INTEGRATION_SCOPE_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
        CONSTRAINT `FK2_APP_REG_INTEGRATION_SCOPE_INTEGRATION_ID` FOREIGN KEY (`INTEGRATION_ID`) REFERENCES `security_integration` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
        CONSTRAINT `FK3_APP_REG_INTEGRATION_SCOPE_INTEGRATION_SCOPE_ID` FOREIGN KEY (`INTEGRATION_SCOPE_ID`) REFERENCES `security_integration_scopes` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
    )
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

    -- user integration tokens
    CREATE TABLE `security_integration_tokens` (
        `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

        `INTEGRATION_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Integration ID',
        `USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User ID',
        `TOKEN` VARCHAR(512) NOT NULL COMMENT 'Token',
        `REFRESH_TOKEN` VARCHAR(512) NOT NULL COMMENT 'Refresh Token',
        `EXPIRES_AT` TIMESTAMP NOT NULL COMMENT 'Token expiration time',

        `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

        PRIMARY KEY (`ID`),

        UNIQUE KEY (`INTEGRATION_ID`, `USER_ID`),
        CONSTRAINT `FK1_USER_INTEGRATION_TOKEN_INTEGRATION_ID` FOREIGN KEY (`INTEGRATION_ID`) REFERENCES `security_integration` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
        CONSTRAINT `FK2_USER_INTEGRATION_TOKEN_USER_ID` FOREIGN KEY (`USER_ID`) REFERENCES `security_user` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
    )
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- adding integration package, role and permissions

SELECT ID from `security_client` WHERE CODE = 'SYSTEM' LIMIT 1 INTO @v_client_system;

INSERT IGNORE INTO `security_permission` (CLIENT_ID, NAME, DESCRIPTION) VALUES
    (@v_client_system, 'Integration CREATE', 'Integration create'),
	(@v_client_system, 'Integration READ', 'Integration read'),
	(@v_client_system, 'Integration UPDATE', 'Integration update'),
	(@v_client_system, 'Integration DELETE', 'Integration delete');

INSERT IGNORE INTO `security_role` (CLIENT_ID, NAME, DESCRIPTION) VALUES
	(@v_client_system, 'Integration Manager', 'Role to hold Integration operations permissions');

SELECT ID from `security_role` WHERE NAME = 'Integration Manager' LIMIT 1 INTO @v_role_integration;

INSERT IGNORE INTO `security_role_permission` (ROLE_ID, PERMISSION_ID) VALUES
	(@v_role_integration, (SELECT ID FROM `security_permission` WHERE NAME = 'Integration CREATE' LIMIT 1)),
    (@v_role_integration, (SELECT ID FROM `security_permission` WHERE NAME = 'Integration READ' LIMIT 1)),
	(@v_role_integration, (SELECT ID FROM `security_permission` WHERE NAME = 'Integration UPDATE' LIMIT 1)),
	(@v_role_integration, (SELECT ID FROM `security_permission` WHERE NAME = 'Integration DELETE' LIMIT 1));

INSERT IGNORE INTO `security_package` (CLIENT_ID, CODE, NAME, DESCRIPTION, BASE) VALUES
	(@v_client_system, 'INTEG', 'Integrations Management', 'Integrations management roles and permissions will be part of this package', FALSE);

SELECT ID from `security_package` WHERE CODE = 'INTEG' LIMIT 1 INTO @v_package_integration;

INSERT IGNORE INTO `security_package_role` (ROLE_ID, PACKAGE_ID) VALUES
	(@v_role_integration, @v_package_integration);

-- adding integration package and role to the system client and system user

INSERT IGNORE INTO `security_client_package` (CLIENT_ID, PACKAGE_ID) VALUES
	(@v_client_system, @v_package_integration);

SELECT ID FROM `security`.`security_user` WHERE USER_NAME = 'sysadmin' LIMIT 1 INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID) VALUES
	(@v_user_sysadmin, @v_role_integration);