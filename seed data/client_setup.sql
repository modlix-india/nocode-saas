-- V1__Initial script (SECURITY)

DROP DATABASE IF EXISTS `security`;

CREATE DATABASE IF NOT EXISTS `security` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `security`;

/* Client */
CREATE TABLE IF NOT EXISTS `security_client_type`
(
    ID          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    CODE        CHAR(4)         NOT NULL COMMENT 'Code',
    TYPE        VARCHAR(256)    NOT NULL COMMENT 'Type',
    DESCRIPTION TEXT                     DEFAULT NULL COMMENT 'Description of the client type',
    CREATED_BY  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    CREATED_AT  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    UPDATED_BY  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    UPDATED_AT  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (ID),
    UNIQUE KEY UK1_CLIENT_TYPE_CODE (CODE)
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_client_type` (`CODE`, `TYPE`, `DESCRIPTION`)
VALUES ('SYS', 'System',
        'System client to manage system. Primarily only one client of this type will be created to manage the entire system.'),
       ('BUS', 'Business',
        'Business client is for the clients who wants to create a business, may it be a business partner (marketing agency) or an industry player (real estate developer, bank or any business).');

CREATE TABLE IF NOT EXISTS `security_client`
(
    ID                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    CODE                   CHAR(8)         NOT NULL COMMENT 'Client code',
    NAME                   VARCHAR(256)    NOT NULL COMMENT 'Name of the client',
    TYPE_CODE              CHAR(4)         NOT NULL COMMENT 'Type of client',
    TOKEN_VALIDITY_MINUTES INT UNSIGNED    NOT NULL                      DEFAULT 30 COMMENT 'Token validity in minutes',
    LOCALE_CODE            VARCHAR(10)                                   DEFAULT 'en-US' COMMENT 'Client default locale',
    STATUS_CODE            ENUM ('ACTIVE','INACTIVE','DELETED','LOCKED') DEFAULT 'ACTIVE' COMMENT 'Status of the client',
    CREATED_BY             BIGINT UNSIGNED                               DEFAULT NULL COMMENT 'ID of the user who created this row',
    CREATED_AT             TIMESTAMP       NOT NULL                      DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    UPDATED_BY             BIGINT UNSIGNED                               DEFAULT NULL COMMENT 'ID of the user who updated this row',
    UPDATED_AT             TIMESTAMP       NOT NULL                      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (ID),
    UNIQUE KEY UK1_CLIENT_CODE (CODE),
    UNIQUE KEY UK2_CLIENT_NAME (NAME),
    CONSTRAINT FK1_CLIENT_CLIENT_TYPE_CODE FOREIGN KEY (TYPE_CODE) REFERENCES `security_client_type` (CODE) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_client` (`CODE`, `NAME`, `TYPE_CODE`)
VALUES ('SYSTEM', 'System Internal', 'SYS');

SELECT ID
from `security_client`
WHERE CODE = 'SYSTEM'
LIMIT 1
INTO @v_client_system;

CREATE TABLE IF NOT EXISTS `security_client_url`
(
    ID          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    CLIENT_ID   BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
    URL_PATTERN VARCHAR(256)    NOT NULL COMMENT 'URL Pattern to identify user\'s Client ID',

    CREATED_BY  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    CREATED_AT  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    UPDATED_BY  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    UPDATED_AT  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (ID),
    UNIQUE KEY UK1_URL_PATTERN (URL_PATTERN),
    CONSTRAINT FK1_CLIENT_URL_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES `security_client` (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `security_client_password_policy`
(
    ID                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    CLIENT_ID                BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',

    ATLEAST_ONE_UPPERCASE    BOOLEAN         NOT NULL DEFAULT FALSE COMMENT 'Atleast one uppercase letter',
    ATLEAST_ONE_LOWERCASE    BOOLEAN         NOT NULL DEFAULT FALSE COMMENT 'Atleast one lowercase letter',
    ATLEAST_ONE_DIGIT        BOOLEAN         NOT NULL DEFAULT FALSE COMMENT 'Atleast one digit',
    ATLEAST_ONE_SPECIAL_CHAR BOOLEAN         NOT NULL DEFAULT FALSE COMMENT 'Atleast one special characters',
    SPACES_ALLOWED           BOOLEAN         NOT NULL DEFAULT TRUE COMMENT 'Spaces are allowed',
    REGEX                    VARCHAR(512)             DEFAULT NULL COMMENT 'Matching regular expression',
    PERCENTAGE_NAME_MATCH    SMALLINT UNSIGNED        DEFAULT NULL COMMENT 'Percent that first and last name matching',
    PASS_EXPIRY_IN_DAYS      SMALLINT UNSIGNED        DEFAULT NULL COMMENT 'Expiry of password in days',
    PASS_EXPIRY_WARN_IN_DAYS SMALLINT UNSIGNED        DEFAULT NULL COMMENT 'Password expiration warning in days',
    PASS_MIN_LENGTH          SMALLINT UNSIGNED        DEFAULT NULL COMMENT 'Minimum Length for the password',
    PASS_MAX_LENGTH          SMALLINT UNSIGNED        DEFAULT NULL COMMENT 'Maximum Length for the password',
    NO_FAILED_ATTEMPTS       SMALLINT UNSIGNED        DEFAULT NULL COMMENT 'No of continuous attempts of authentication with wrong password',
    PASS_HISTORY_COUNT       SMALLINT UNSIGNED        DEFAULT NULL COMMENT 'Remember how many passwords',

    CREATED_BY               BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    CREATED_AT               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    UPDATED_BY               BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    UPDATED_AT               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (ID),
    UNIQUE KEY UK1_CLIENT_PWD_POL_ID (CLIENT_ID),
    CONSTRAINT FK1_CLIENT_PWD_POL_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES `security_client` (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `security_client_manage`
(

    ID               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    CLIENT_ID        BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
    MANAGE_CLIENT_ID BIGINT UNSIGNED NOT NULL COMMENT 'Client ID that manages this client',

    PRIMARY KEY (ID),
    CONSTRAINT FK1_CLIENT_MANAGE_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES `security_client` (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK1_CLIENT_MANAGE_MNG_CLIENT_ID FOREIGN KEY (MANAGE_CLIENT_ID) REFERENCES `security_client` (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

/* User */

CREATE TABLE IF NOT EXISTS `security_user`
(
    ID                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    CLIENT_ID               BIGINT UNSIGNED NOT NULL COMMENT 'Client ID for which this user belongs to',
    USER_NAME               CHAR(32)        NOT NULL                                          DEFAULT 'NONE' COMMENT 'User Name to login',
    EMAIL_ID                VARCHAR(320)    NOT NULL                                          DEFAULT 'NONE' COMMENT 'Email ID to login',
    PHONE_NUMBER            CHAR(32)        NOT NULL                                          DEFAULT 'NONE' COMMENT 'Phone Number to login',
    FIRST_NAME              VARCHAR(128)                                                      DEFAULT NULL COMMENT 'First name',
    LAST_NAME               VARCHAR(128)                                                      DEFAULT NULL COMMENT 'Last name',
    DESIGNATION             VARCHAR(256)                                                      DEFAULT NULL COMMENT 'Designation',
    MIDDLE_NAME             VARCHAR(128)                                                      DEFAULT NULL COMMENT 'Middle name',
    LOCALE_CODE             VARCHAR(10)                                                       DEFAULT 'en-US' COMMENT 'User\'s Locale',
    PASSWORD                VARCHAR(512)                                                      DEFAULT NULL COMMENT 'Password message digested string',
    PASSWORD_HASHED         BOOLEAN                                                           DEFAULT 1 COMMENT 'Password stored is hashed or not',

    ACCOUNT_NON_EXPIRED     BOOLEAN         NOT NULL                                          DEFAULT 1 COMMENT 'If false, means user is expired',
    ACCOUNT_NON_LOCKED      BOOLEAN         NOT NULL                                          DEFAULT 1 COMMENT 'If false, means user is locked',
    CREDENTIALS_NON_EXPIRED BOOLEAN         NOT NULL                                          DEFAULT 1 COMMENT 'If flase, password is expired',

    NO_FAILED_ATTEMPT       SMALLINT                                                          DEFAULT 0 COMMENT 'No of failed attempts',
    STATUS_CODE             ENUM ('ACTIVE','INACTIVE','DELETED','LOCKED', 'PASSWORD_EXPIRED') DEFAULT 'ACTIVE' COMMENT 'Status of the user',

    CREATED_BY              BIGINT UNSIGNED                                                   DEFAULT NULL COMMENT 'ID of the user who created this row',
    CREATED_AT              TIMESTAMP       NOT NULL                                          DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    UPDATED_BY              BIGINT UNSIGNED                                                   DEFAULT NULL COMMENT 'ID of the user who updated this row',
    UPDATED_AT              TIMESTAMP       NOT NULL                                          DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (ID),
    UNIQUE KEY UK1_USER_NAME (CLIENT_ID, USER_NAME, EMAIL_ID, PHONE_NUMBER),
    KEY K1_USER_NAME (USER_NAME),
    KEY K2_EMAIL_ID (EMAIL_ID),
    KEY K3_PHONE_NUMBER (PHONE_NUMBER),
    CONSTRAINT FK1_USER_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES security_client (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_user` (CLIENT_ID, USER_NAME, FIRST_NAME, LAST_NAME, PASSWORD, PASSWORD_HASHED)
values (@v_client_system, 'sysadmin', 'ADMIN', 'System', 'fincity@123', false);

SELECT ID
FROM `security_user`
WHERE USER_NAME = 'sysadmin'
LIMIT 1
INTO @v_user_sysadmin;

CREATE TABLE IF NOT EXISTS `security_past_passwords`
(
    ID              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    USER_ID         BIGINT UNSIGNED NOT NULL COMMENT 'User ID',
    PASSWORD        VARCHAR(512)             DEFAULT NULL COMMENT 'Password message digested string',
    PASSWORD_HASHED BOOLEAN                  DEFAULT 1 COMMENT 'Password stored is hashed or not',
    CREATED_BY      BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    CREATED_AT      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (ID),
    CONSTRAINT FK1_PAST_PASSWORD_USER_ID FOREIGN KEY (USER_ID) REFERENCES `security_user` (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_past_passwords` (USER_ID, PASSWORD, PASSWORD_HASHED)
VALUES (@v_user_sysadmin, 'fincity@123', false);

/* Role */

CREATE TABLE IF NOT EXISTS `security_role`
(
    ID          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    CLIENT_ID   BIGINT UNSIGNED NOT NULL COMMENT 'Client ID for which this role belongs to',
    NAME        VARCHAR(256)    NOT NULL COMMENT 'Name of the role',
    DESCRIPTION TEXT                     DEFAULT NULL COMMENT 'Description of the role',

    CREATED_BY  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    CREATED_AT  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    UPDATED_BY  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    UPDATED_AT  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (ID),
    UNIQUE KEY UK1_ROLE_NAME (NAME),
    CONSTRAINT FK1_ROLE_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES security_client (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_role` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'Client Manager', 'Role to hold client operations permissions'),
       (@v_client_system, 'User Manager', 'Role to hold user operations permissions'),
       (@v_client_system, 'Package Manager', 'Role to hold package operations permissions'),
       (@v_client_system, 'Role Manager', 'Role to hold role operations permissions'),
       (@v_client_system, 'Permission Manager', 'Role to hold permission operations permissions'),
       (@v_client_system, 'Client Type Manager', 'Role to hold client type operations permissions'),
       (@v_client_system, 'Client Update Manager', 'Role to hold client data update operations permissions');

SELECT ID
from `security_role`
WHERE NAME = 'Client Manager'
LIMIT 1
INTO @v_role_client;
SELECT ID
from `security_role`
WHERE NAME = 'User Manager'
LIMIT 1
INTO @v_role_user;
SELECT ID
from `security_role`
WHERE NAME = 'Package Manager'
LIMIT 1
INTO @v_role_package;
SELECT ID
from `security_role`
WHERE NAME = 'Role Manager'
LIMIT 1
INTO @v_role_role;
SELECT ID
from `security_role`
WHERE NAME = 'Permission Manager'
LIMIT 1
INTO @v_role_permission;
SELECT ID
from `security_role`
WHERE NAME = 'Client Type Manager'
LIMIT 1
INTO @v_role_client_type;
SELECT ID
from `security_role`
WHERE NAME = 'Client Update Manager'
LIMIT 1
INTO @v_role_client_update;

/* Permission */

CREATE TABLE IF NOT EXISTS `security_permission`
(
    ID          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    CLIENT_ID   BIGINT UNSIGNED NOT NULL COMMENT 'Client ID for which this permission belongs to',
    NAME        VARCHAR(256)    NOT NULL COMMENT 'Name of the permission',
    DESCRIPTION TEXT                     DEFAULT NULL COMMENT 'Description of the permission',

    CREATED_BY  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    CREATED_AT  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    UPDATED_BY  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    UPDATED_AT  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (ID),
    UNIQUE KEY UK1_PERMISSION_NAME (NAME),
    CONSTRAINT FK1_PERMISSION_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES security_client (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_permission` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'Client CREATE', 'Client create'),
       (@v_client_system, 'Client READ', 'Client read'),
       (@v_client_system, 'Client UPDATE', 'Client update'),
       (@v_client_system, 'Client DELETE', 'Client delete'),
       (@v_client_system, 'ASSIGN Package To Client', 'Assign package to client'),
       (@v_client_system, 'User CREATE', 'User create'),
       (@v_client_system, 'User READ', 'User read'),
       (@v_client_system, 'User UPDATE', 'User update'),
       (@v_client_system, 'User DELETE', 'User delete'),
       (@v_client_system, 'ASSIGN Role To User', 'Assign role to user'),
       (@v_client_system, 'ASSIGN Permission To User', 'Assign permission to user'),
       (@v_client_system, 'Package CREATE', 'Package create'),
       (@v_client_system, 'Package READ', 'Package read'),
       (@v_client_system, 'Package UPDATE', 'Package update'),
       (@v_client_system, 'Package DELETE', 'Package delete'),
       (@v_client_system, 'Role CREATE', 'Role create'),
       (@v_client_system, 'Role READ', 'Role read'),
       (@v_client_system, 'Role UPDATE', 'Role update'),
       (@v_client_system, 'Role DELETE', 'Role delete'),
       (@v_client_system, 'ASSIGN Role To Package', 'Assign role to package'),
       (@v_client_system, 'ASSIGN Permission To Role', 'Assign permission to role'),
       (@v_client_system, 'Permission CREATE', 'Permission create'),
       (@v_client_system, 'Permission READ', 'Permission read'),
       (@v_client_system, 'Permission UPDATE', 'Permission update'),
       (@v_client_system, 'Permission DELETE', 'Permission delete'),
       (@v_client_system, 'Client Type CREATE', 'Client type create'),
       (@v_client_system, 'Client Type READ', 'Client type read'),
       (@v_client_system, 'Client Type UPDATE', 'Client type update'),
       (@v_client_system, 'Client Type DELETE', 'Client type delete');

CREATE TABLE IF NOT EXISTS `security_role_permission`
(
    ID            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    ROLE_ID       BIGINT UNSIGNED NOT NULL COMMENT 'Role ID',
    PERMISSION_ID BIGINT UNSIGNED NOT NULL COMMENT 'Premission ID',

    PRIMARY KEY (ID),
    UNIQUE KEY UK1_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID),
    CONSTRAINT FK1_ROLE_PERM_ROLE_ID FOREIGN KEY (ROLE_ID) REFERENCES security_role (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK2_ROLE_PERM_PERMISSION_ID FOREIGN KEY (PERMISSION_ID) REFERENCES security_permission (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_role_permission` (ROLE_ID, PERMISSION_ID)
VALUES (@v_role_client, (SELECT ID FROM `security_permission` WHERE NAME = 'Client CREATE' LIMIT 1)),
       (@v_role_client, (SELECT ID FROM `security_permission` WHERE NAME = 'Client READ' LIMIT 1)),
       (@v_role_client, (SELECT ID FROM `security_permission` WHERE NAME = 'Client UPDATE' LIMIT 1)),
       (@v_role_client, (SELECT ID FROM `security_permission` WHERE NAME = 'Client DELETE' LIMIT 1)),
       (@v_role_client, (SELECT ID FROM `security_permission` WHERE NAME = 'ASSIGN Package To Client' LIMIT 1)),
       (@v_role_user, (SELECT ID FROM `security_permission` WHERE NAME = 'User CREATE' LIMIT 1)),
       (@v_role_user, (SELECT ID FROM `security_permission` WHERE NAME = 'User READ' LIMIT 1)),
       (@v_role_user, (SELECT ID FROM `security_permission` WHERE NAME = 'User UPDATE' LIMIT 1)),
       (@v_role_user, (SELECT ID FROM `security_permission` WHERE NAME = 'User DELETE' LIMIT 1)),
       (@v_role_user, (SELECT ID FROM `security_permission` WHERE NAME = 'ASSIGN Role To User' LIMIT 1)),
       (@v_role_user, (SELECT ID FROM `security_permission` WHERE NAME = 'ASSIGN Permission To User' LIMIT 1)),
       (@v_role_package, (SELECT ID FROM `security_permission` WHERE NAME = 'Package CREATE' LIMIT 1)),
       (@v_role_package, (SELECT ID FROM `security_permission` WHERE NAME = 'Package READ' LIMIT 1)),
       (@v_role_package, (SELECT ID FROM `security_permission` WHERE NAME = 'Package UPDATE' LIMIT 1)),
       (@v_role_package, (SELECT ID FROM `security_permission` WHERE NAME = 'Package DELETE' LIMIT 1)),
       (@v_role_role, (SELECT ID FROM `security_permission` WHERE NAME = 'Role CREATE' LIMIT 1)),
       (@v_role_role, (SELECT ID FROM `security_permission` WHERE NAME = 'Role READ' LIMIT 1)),
       (@v_role_role, (SELECT ID FROM `security_permission` WHERE NAME = 'Role UPDATE' LIMIT 1)),
       (@v_role_role, (SELECT ID FROM `security_permission` WHERE NAME = 'Role DELETE' LIMIT 1)),
       (@v_role_package, (SELECT ID FROM `security_permission` WHERE NAME = 'ASSIGN Role To Package' LIMIT 1)),
       (@v_role_role, (SELECT ID FROM `security_permission` WHERE NAME = 'ASSIGN Permission To Role' LIMIT 1)),
       (@v_role_permission, (SELECT ID FROM `security_permission` WHERE NAME = 'Permission CREATE' LIMIT 1)),
       (@v_role_permission, (SELECT ID FROM `security_permission` WHERE NAME = 'Permission READ' LIMIT 1)),
       (@v_role_permission, (SELECT ID FROM `security_permission` WHERE NAME = 'Permission UPDATE' LIMIT 1)),
       (@v_role_permission, (SELECT ID FROM `security_permission` WHERE NAME = 'Permission DELETE' LIMIT 1)),
       (@v_role_client_type, (SELECT ID FROM `security_permission` WHERE NAME = 'Client Type CREATE' LIMIT 1)),
       (@v_role_client_type, (SELECT ID FROM `security_permission` WHERE NAME = 'Client Type READ' LIMIT 1)),
       (@v_role_client_type, (SELECT ID FROM `security_permission` WHERE NAME = 'Client Type UPDATE' LIMIT 1)),
       (@v_role_client_type, (SELECT ID FROM `security_permission` WHERE NAME = 'Client Type DELETE' LIMIT 1)),
       (@v_role_client_update, (SELECT ID FROM `security_permission` WHERE NAME = 'Client UPDATE' LIMIT 1));

CREATE TABLE IF NOT EXISTS `security_user_role_permission`
(
    ID            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    USER_ID       BIGINT UNSIGNED NOT NULL COMMENT 'User ID',
    ROLE_ID       BIGINT UNSIGNED DEFAULT NULL COMMENT 'Role ID',
    PERMISSION_ID BIGINT UNSIGNED DEFAULT NULL COMMENT 'Permission ID',

    PRIMARY KEY (ID),
    KEY UK1_USER (USER_ID),
    CONSTRAINT FK1_USER_ROLE_USER_ID FOREIGN KEY (USER_ID) REFERENCES security_user (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK2_USER_ROLE_ROLE_ID FOREIGN KEY (ROLE_ID) REFERENCES security_role (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK3_USER_ROLE_PERMISSION_ID FOREIGN KEY (PERMISSION_ID) REFERENCES security_permission (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_user_role_permission` (USER_ID, ROLE_ID)
VALUES (@v_user_sysadmin, @v_role_client),
       (@v_user_sysadmin, @v_role_client_type),
       (@v_user_sysadmin, @v_role_user),
       (@v_user_sysadmin, @v_role_permission),
       (@v_user_sysadmin, @v_role_role),
       (@v_user_sysadmin, @v_role_package);

/* Package */

CREATE TABLE IF NOT EXISTS `security_package`
(
    ID          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    CLIENT_ID   BIGINT UNSIGNED NOT NULL COMMENT 'Client ID for which this permission belongs to',
    CODE        CHAR(8)         NOT NULL COMMENT 'Package code',
    NAME        VARCHAR(256)    NOT NULL COMMENT 'Name of the package',
    DESCRIPTION TEXT                     DEFAULT NULL COMMENT 'Description of the package',
    BASE        BOOLEAN         NOT NULL DEFAULT FALSE COMMENT 'Indicator if this package is for every client',

    CREATED_BY  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    CREATED_AT  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    UPDATED_BY  BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    UPDATED_AT  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (ID),
    UNIQUE KEY UK1_PACKAGE_CODE (CODE),
    UNIQUE KEY UK2_PACKAGE_NAME (NAME),
    CONSTRAINT FK1_PACKAGE_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES security_client (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_package` (CLIENT_ID, CODE, NAME, DESCRIPTION, BASE)
VALUES (@v_client_system, 'CLIENT', 'Client Management',
        'Client management roles and permissions will be part of this package', FALSE),
       (@v_client_system, 'CLIUPD', 'Client Update',
        'Client management roles and permissions will be part of this package', TRUE),
       (@v_client_system, 'CLITYP', 'Client Type Management',
        'Client management roles and permissions will be part of this package', FALSE),
       (@v_client_system, 'USER', 'User Management',
        'User management roles and permissions will be part of this package', TRUE),
       (@v_client_system, 'PACKAGE', 'Package Management',
        'Package management roles and permissions will be part of this package', FALSE),
       (@v_client_system, 'ROLE', 'Role Management',
        'Role management roles and permissions will be part of this package', TRUE),
       (@v_client_system, 'PERMISS', 'Permission Management',
        'Permission management roles and permissions will be part of this package', FALSE);

SELECT ID
from `security_package`
WHERE CODE = 'CLIENT'
LIMIT 1
INTO @v_package_client;
SELECT ID
from `security_package`
WHERE CODE = 'CLIUPD'
LIMIT 1
INTO @v_package_client_update;
SELECT ID
from `security_package`
WHERE CODE = 'CLITYP'
LIMIT 1
INTO @v_package_client_type;
SELECT ID
from `security_package`
WHERE CODE = 'USER'
LIMIT 1
INTO @v_package_user;
SELECT ID
from `security_package`
WHERE CODE = 'PACKAGE'
LIMIT 1
INTO @v_package_package;
SELECT ID
from `security_package`
WHERE CODE = 'ROLE'
LIMIT 1
INTO @v_package_role;
SELECT ID
from `security_package`
WHERE CODE = 'PERMISS'
LIMIT 1
INTO @v_package_permission;

CREATE TABLE IF NOT EXISTS `security_package_role`
(
    ID         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    PACKAGE_ID BIGINT UNSIGNED NOT NULL COMMENT 'Package ID',
    ROLE_ID    BIGINT UNSIGNED NOT NULL COMMENT 'Role ID',

    PRIMARY KEY (ID),
    UNIQUE KEY UK1_PACKAGE_ROLE (ROLE_ID, PACKAGE_ID),
    CONSTRAINT FK1_PACKAGE_ROLE_ROLE_ID FOREIGN KEY (ROLE_ID) REFERENCES security_role (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK2_PACKAGE_ROLE_PACKAGE_ID FOREIGN KEY (PACKAGE_ID) REFERENCES security_package (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_package_role` (ROLE_ID, PACKAGE_ID)
VALUES (@v_role_client, @v_package_client),
       (@v_role_client_type, @v_package_client_type),
       (@v_role_client_update, @v_package_client_update),
       (@v_role_user, @v_package_user),
       (@v_role_package, @v_package_package),
       (@v_role_role, @v_package_role),
       (@v_role_permission, @v_package_permission);


CREATE TABLE IF NOT EXISTS `security_client_package`
(
    ID         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    CLIENT_ID  BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
    PACKAGE_ID BIGINT UNSIGNED NOT NULL COMMENT 'Package ID',

    PRIMARY KEY (ID),
    UNIQUE KEY UK1_CLIENT_PACKAGE (CLIENT_ID, PACKAGE_ID),
    CONSTRAINT FK1_CLIENT_PACKAGE_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES security_client (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK2_CLIENT_PACKAGE_PACKAGE_ID FOREIGN KEY (PACKAGE_ID) REFERENCES security_package (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_client_package` (CLIENT_ID, PACKAGE_ID)
VALUES (@v_client_system, @v_package_client),
       (@v_client_system, @v_package_client_type),
       (@v_client_system, @v_package_user),
       (@v_client_system, @v_package_package),
       (@v_client_system, @v_package_role),
       (@v_client_system, @v_package_permission);

CREATE TABLE IF NOT EXISTS `security_user_token`
(
    ID         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    USER_ID    BIGINT UNSIGNED NOT NULL COMMENT 'User id',
    TOKEN      VARCHAR(512)    NOT NULL COMMENT 'JWT token',
    PART_TOKEN CHAR(50)        NOT NULL COMMENT 'Part of token to index',
    EXPIRES_AT TIMESTAMP       NOT NULL DEFAULT '2022-01-01 01:01:01.000001' COMMENT 'When the token expires',
    IP_ADDRESS VARCHAR(50)     NOT NULL COMMENT 'User IP from where he logged in',

    CREATED_BY BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    CREATED_AT TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (ID),
    INDEX (PART_TOKEN),
    CONSTRAINT FK1_USER_TOKEN_USER_ID FOREIGN KEY (USER_ID) REFERENCES `security_user` (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `security_sox_log`
(

    ID          BIGINT UNSIGNED                                                                     NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    OBJECT_ID   BIGINT UNSIGNED                                                                     NOT NULL COMMENT 'ID of the object where the change is happening',
    OBJECT_NAME ENUM ('USER', 'ROLE', 'PERMISSION', 'PACKAGE', 'CLIENT', 'CLIENT_TYPE')             NOT NULL COMMENT 'Operation on the object',
    ACTION_NAME ENUM ('CREATE', 'UPDATE', 'DELETE', 'READ', 'ASSIGN', 'UNASSIGN', 'OTHER', 'LOGIN') NOT NULL COMMENT 'Log action name',
    DESCRIPTION VARCHAR(1048)                                                                       NOT NULL COMMENT 'Log description',

    CREATED_BY  BIGINT UNSIGNED                                                                              DEFAULT NULL COMMENT 'ID of the user who created this row',
    CREATED_AT  TIMESTAMP                                                                           NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (ID),
    INDEX (CREATED_AT DESC),
    INDEX (OBJECT_NAME, ACTION_NAME)
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `security_org_structure`
(
    ID              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    CLIENT_ID       BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
    USER_ID         BIGINT UNSIGNED NOT NULL COMMENT 'User ID',
    DEFAULT_MANAGER TINYINT         NOT NULL DEFAULT 1 COMMENT 'Default manager, 0 if he is reporting to multiple managers',
    MANAGER_ID      BIGINT UNSIGNED NOT NULL COMMENT 'Manager ID',

    PRIMARY KEY (ID),
    UNIQUE KEY UK1_ORG_STRUCTURE (USER_ID, MANAGER_ID),
    CONSTRAINT FK1_ORG_STRUCTURE_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES security_client (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK2_ORG_STRUCTURE_USER_ID FOREIGN KEY (USER_ID) REFERENCES security_user (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK3_ORG_STRUCTURE_MANAGER_ID FOREIGN KEY (MANAGER_ID) REFERENCES security_user (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;


INSERT IGNORE INTO `security_permission` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'Application CREATE', 'Application create'),
       (@v_client_system, 'Application READ', 'Application read'),
       (@v_client_system, 'Application UPDATE', 'Application update'),
       (@v_client_system, 'Application DELETE', 'Application delete'),
       (@v_client_system, 'Function CREATE', 'Function create'),
       (@v_client_system, 'Function READ', 'Function read'),
       (@v_client_system, 'Function UPDATE', 'Function update'),
       (@v_client_system, 'Function DELETE', 'Function delete'),
       (@v_client_system, 'Page CREATE', 'Page create'),
       (@v_client_system, 'Page READ', 'Page read'),
       (@v_client_system, 'Page UPDATE', 'Page update'),
       (@v_client_system, 'Page DELETE', 'Page delete'),
       (@v_client_system, 'Theme CREATE', 'Theme create'),
       (@v_client_system, 'Theme READ', 'Theme read'),
       (@v_client_system, 'Theme UPDATE', 'Theme update'),
       (@v_client_system, 'Theme DELETE', 'Theme delete'),
       (@v_client_system, 'Personalization CLEAR', 'Personalization Clear');

INSERT IGNORE INTO `security_role` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'Application Manager', 'Role to hold application operations permissions'),
       (@v_client_system, 'System Application Manager',
        'Role to hold application operations permissions for System Client'),
       (@v_client_system, 'Function Manager', 'Role to hold function operations permissions'),
       (@v_client_system, 'Page Manager', 'Role to hold page operations permissions'),
       (@v_client_system, 'Theme Manager', 'Role to hold theme operations permissions'),
       (@v_client_system, 'Personalization Manager', 'Role to hold personalization operations permissions');

SELECT ID
from `security_role`
WHERE NAME = 'Application Manager'
LIMIT 1
INTO @v_role_application;
SELECT ID
from `security_role`
WHERE NAME = 'System Application Manager'
LIMIT 1
INTO @v_role_system_application;
SELECT ID
from `security_role`
WHERE NAME = 'Function Manager'
LIMIT 1
INTO @v_role_function;
SELECT ID
from `security_role`
WHERE NAME = 'Page Manager'
LIMIT 1
INTO @v_role_page;
SELECT ID
from `security_role`
WHERE NAME = 'Theme Manager'
LIMIT 1
INTO @v_role_theme;
SELECT ID
from `security_role`
WHERE NAME = 'Personalization Manager'
LIMIT 1
INTO @v_role_personalization;

INSERT IGNORE INTO `security_role_permission` (ROLE_ID, PERMISSION_ID)
VALUES (@v_role_application, (SELECT ID FROM `security_permission` WHERE NAME = 'Application READ' LIMIT 1)),
       (@v_role_application, (SELECT ID FROM `security_permission` WHERE NAME = 'Application UPDATE' LIMIT 1)),
       (@v_role_application, (SELECT ID FROM `security_permission` WHERE NAME = 'Application DELETE' LIMIT 1)),

       (@v_role_system_application, (SELECT ID FROM `security_permission` WHERE NAME = 'Application CREATE' LIMIT 1)),
       (@v_role_system_application, (SELECT ID FROM `security_permission` WHERE NAME = 'Application READ' LIMIT 1)),
       (@v_role_system_application, (SELECT ID FROM `security_permission` WHERE NAME = 'Application UPDATE' LIMIT 1)),
       (@v_role_system_application, (SELECT ID FROM `security_permission` WHERE NAME = 'Application DELETE' LIMIT 1)),

       (@v_role_function, (SELECT ID FROM `security_permission` WHERE NAME = 'Function CREATE' LIMIT 1)),
       (@v_role_function, (SELECT ID FROM `security_permission` WHERE NAME = 'Function READ' LIMIT 1)),
       (@v_role_function, (SELECT ID FROM `security_permission` WHERE NAME = 'Function UPDATE' LIMIT 1)),
       (@v_role_function, (SELECT ID FROM `security_permission` WHERE NAME = 'Function DELETE' LIMIT 1)),

       (@v_role_page, (SELECT ID FROM `security_permission` WHERE NAME = 'Page CREATE' LIMIT 1)),
       (@v_role_page, (SELECT ID FROM `security_permission` WHERE NAME = 'Page READ' LIMIT 1)),
       (@v_role_page, (SELECT ID FROM `security_permission` WHERE NAME = 'Page UPDATE' LIMIT 1)),
       (@v_role_page, (SELECT ID FROM `security_permission` WHERE NAME = 'Page DELETE' LIMIT 1)),

       (@v_role_theme, (SELECT ID FROM `security_permission` WHERE NAME = 'Theme CREATE' LIMIT 1)),
       (@v_role_theme, (SELECT ID FROM `security_permission` WHERE NAME = 'Theme READ' LIMIT 1)),
       (@v_role_theme, (SELECT ID FROM `security_permission` WHERE NAME = 'Theme UPDATE' LIMIT 1)),
       (@v_role_theme, (SELECT ID FROM `security_permission` WHERE NAME = 'Theme DELETE' LIMIT 1)),

       (@v_role_personalization, (SELECT ID FROM `security_permission` WHERE NAME = 'Personalization CLEAR' LIMIT 1));


INSERT IGNORE INTO `security_package` (CLIENT_ID, CODE, NAME, DESCRIPTION, BASE)
VALUES (@v_client_system, 'APP', 'Application Management',
        'Application management roles and permissions will be part of this package', FALSE),
       (@v_client_system, 'APPSYS', 'System Application Management',
        'System Application management roles and permissions will be part of this package', FALSE),
       (@v_client_system, 'FUNCT', 'Function Management',
        'Function management roles and permissions will be part of this package', FALSE),
       (@v_client_system, 'PAGE', 'Page Management',
        'Page management roles and permissions will be part of this package', FALSE),
       (@v_client_system, 'THEME', 'Theme Management',
        'Theme management roles and permissions will be part of this package', FALSE),
       (@v_client_system, 'PERSON', 'Personalization Management',
        'Personalization management roles and permissions will be part of this package', FALSE);

SELECT ID
from `security_package`
WHERE CODE = 'APP'
LIMIT 1
INTO @v_package_app;
SELECT ID
from `security_package`
WHERE CODE = 'APPSYS'
LIMIT 1
INTO @v_package_app_sys;
SELECT ID
from `security_package`
WHERE CODE = 'FUNCT'
LIMIT 1
INTO @v_package_funct;
SELECT ID
from `security_package`
WHERE CODE = 'PAGE'
LIMIT 1
INTO @v_package_page;
SELECT ID
from `security_package`
WHERE CODE = 'THEME'
LIMIT 1
INTO @v_package_theme;
SELECT ID
from `security_package`
WHERE CODE = 'PERSON'
LIMIT 1
INTO @v_package_person;

INSERT IGNORE INTO `security_package_role` (ROLE_ID, PACKAGE_ID)
VALUES (@v_role_application, @v_package_app),
       (@v_role_system_application, @v_package_app_sys),
       (@v_role_function, @v_package_funct),
       (@v_role_page, @v_package_page),
       (@v_role_theme, @v_package_theme),
       (@v_role_personalization, @v_package_person);

INSERT IGNORE INTO `security_client_package` (CLIENT_ID, PACKAGE_ID)
VALUES (@v_client_system, @v_package_app_sys),
       (@v_client_system, @v_package_funct),
       (@v_client_system, @v_package_page),
       (@v_client_system, @v_package_theme),
       (@v_client_system, @v_package_person);

-- V2__Appcode changes script (SECURITY)

ALTER TABLE `security`.`security_client_url`
    ADD COLUMN `APP_CODE` VARCHAR(256) NOT NULL AFTER `URL_PATTERN`,
    CHANGE COLUMN `URL_PATTERN` `URL_PATTERN` VARCHAR(512) NOT NULL COMMENT 'URL Pattern to identify user\'s Client ID';

ALTER TABLE `security`.`security_client_url`
    CHANGE COLUMN `APP_CODE` `APP_CODE` CHAR(64) NOT NULL;

CREATE TABLE `security`.`security_app`
(
    `ID`         BIGINT UNSIGNED                     NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID`  BIGINT UNSIGNED                     NOT NULL COMMENT 'Client ID',
    `APP_NAME`   VARCHAR(512)                        NOT NULL COMMENT 'Name of the application',
    `APP_CODE`   CHAR(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Code of the application',
    `APP_TYPE`   ENUM ('APP','SITE','POSTER')        NOT NULL DEFAULT 'APP' COMMENT 'Application type',
    `CREATED_BY` BIGINT UNSIGNED                              DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED                              DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_APPCODE` (`APP_CODE`),
    KEY `FK1_APP_CLIENT_ID` (`CLIENT_ID`),
    CONSTRAINT `FK1_APP_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

ALTER TABLE `security`.`security_client_url`
    ADD CONSTRAINT `FK1_CLIENT_URL_APP_CODE`
        FOREIGN KEY (APP_CODE)
            REFERENCES `security`.`security_app` (APP_CODE)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

INSERT INTO `security`.`security_app` (`CLIENT_ID`, `APP_NAME`, `APP_CODE`, `APP_TYPE`)
VALUES ('1', 'Under Construction', 'nothing', 'APP');
INSERT INTO `security`.`security_app` (`CLIENT_ID`, `APP_NAME`, `APP_CODE`, `APP_TYPE`)
VALUES ('1', 'App Builder', 'appbuilder', 'APP');

ALTER TABLE `security`.`security_permission`
    ADD COLUMN `APP_ID` BIGINT UNSIGNED NULL DEFAULT NULL AFTER `NAME`;
ALTER TABLE `security`.`security_permission`
    ADD CONSTRAINT `FK2_PERMISSION_APP_ID`
        FOREIGN KEY (APP_ID)
            REFERENCES `security`.`security_app` (ID)
            ON DELETE RESTRICT
            ON UPDATE RESTRICT;

ALTER TABLE `security`.`security_role`
    ADD COLUMN `APP_ID` BIGINT UNSIGNED NULL DEFAULT NULL AFTER `NAME`;
ALTER TABLE `security`.`security_role`
    ADD CONSTRAINT `FK2_ROLE_APP_ID`
        FOREIGN KEY (APP_ID)
            REFERENCES `security`.`security_app` (ID)
            ON DELETE RESTRICT
            ON UPDATE RESTRICT;

ALTER TABLE `security`.`security_sox_log`
    CHANGE COLUMN `OBJECT_NAME` `OBJECT_NAME` ENUM ('USER', 'ROLE', 'PERMISSION', 'PACKAGE', 'CLIENT', 'CLIENT_TYPE', 'APP') NOT NULL COMMENT 'Operation on the object';

update security.security_permission
set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1)
where name like 'Application %';
update security.security_permission
set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1)
where name like 'Function %';
update security.security_permission
set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1)
where name like 'Page %';
update security.security_permission
set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1)
where name like 'Theme %';

update security.security_role
set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1)
where name like 'Application %';
update security.security_role
set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1)
where name like 'System %';
update security.security_role
set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1)
where name like 'Function %';
update security.security_role
set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1)
where name like 'Page %';
update security.security_role
set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1)
where name like 'Theme %';

select id
from security.security_user
limit 1
into @V_SYS_USER_ID;

insert into security.security_user_role_permission (user_id, role_id)
select @V_SYS_USER_ID, id
from security.security_role
where name like 'Theme %'
   or name like 'Page %'
   or name like 'Function %'
   or name like 'Application %';

-- V3__Files service script (SECURITY)

USE security;

SELECT ID
from `security`.`security_client`
WHERE CODE = 'SYSTEM'
LIMIT 1
INTO @v_client_system;

SELECT ID
from `security`.`security_app`
WHERE app_code = 'appbuilder'
LIMIT 1
INTO @v_app_appbuilder;

INSERT INTO `security`.`security_role` (CLIENT_ID, NAME, APP_ID, DESCRIPTION)
VALUES (@v_client_system, 'Files Manager', @v_app_appbuilder, 'Role to hold static files operations permissions');

INSERT INTO `security`.`security_permission` (CLIENT_ID, NAME, APP_ID, DESCRIPTION)
VALUES (@v_client_system, 'STATIC Files PATH', @v_app_appbuilder, 'Static files path management'),
       (@v_client_system, 'SECURED Files PATH', @v_app_appbuilder, 'Secured files path management');

SELECT ID
from `security`.`security_role`
WHERE NAME = 'Files Manager'
LIMIT 1
INTO @v_role_files;

INSERT IGNORE INTO `security`.`security_role_permission` (ROLE_ID, PERMISSION_ID)
VALUES (@v_role_files, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Static Files PATH' LIMIT 1)),
       (@v_role_files, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Secured Files PATH' LIMIT 1));

SELECT ID
FROM `security`.`security_user`
WHERE USER_NAME = 'sysadmin'
LIMIT 1
INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID)
VALUES (@v_user_sysadmin, @v_role_files);

-- V4__Client password policy script (SECURITY)

USE security;

INSERT IGNORE INTO security_permission (CLIENT_ID, NAME, DESCRIPTION)
values (1, 'Client Password Policy READ', 'client password policy read')
     , (1, 'Client Password Policy CREATE', 'client password policy create')
     , (1, 'Client Password Policy UPDATE', 'client password policy update')
     , (1, 'Client Password Policy DELETE', 'client password policy delete');

-- V5__Data service script (SECURITY)

USE security;

SELECT ID
from `security`.`security_client`
WHERE CODE = 'SYSTEM'
LIMIT 1
INTO @v_client_system;

SELECT ID
from `security`.`security_app`
WHERE app_code = 'appbuilder'
LIMIT 1
INTO @v_app_appbuilder;

INSERT INTO `security`.`security_role` (CLIENT_ID, NAME, APP_ID, DESCRIPTION)
VALUES (@v_client_system, 'Data Manager', @v_app_appbuilder, 'Role to hold Data operations permissions');

INSERT INTO `security`.`security_permission` (CLIENT_ID, NAME, APP_ID, DESCRIPTION)
VALUES (@v_client_system, 'Storage CREATE', @v_app_appbuilder, 'Storage create'),
       (@v_client_system, 'Storage READ', @v_app_appbuilder, 'Storage read'),
       (@v_client_system, 'Storage UPDATE', @v_app_appbuilder, 'Storage update'),
       (@v_client_system, 'Storage DELETE', @v_app_appbuilder, 'Storage delete');

SELECT ID
from `security`.`security_role`
WHERE NAME = 'Data Manager'
LIMIT 1
INTO @v_role_data;

INSERT IGNORE INTO `security`.`security_role_permission` (ROLE_ID, PERMISSION_ID)
VALUES (@v_role_data, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Storage CREATE' LIMIT 1)),
       (@v_role_data, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Storage READ' LIMIT 1)),
       (@v_role_data, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Storage UPDATE' LIMIT 1)),
       (@v_role_data, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Storage DELETE' LIMIT 1));

SELECT ID
FROM `security`.`security_user`
WHERE USER_NAME = 'sysadmin'
LIMIT 1
INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID)
VALUES (@v_user_sysadmin, @v_role_data);

INSERT INTO `security`.`security_role` (CLIENT_ID, NAME, APP_ID, DESCRIPTION)
VALUES (@v_client_system, 'Data Connection Manager', @v_app_appbuilder,
        'Role to hold Data connection operations permissions');

INSERT INTO `security`.`security_permission` (CLIENT_ID, NAME, APP_ID, DESCRIPTION)
VALUES (@v_client_system, 'Connection CREATE', @v_app_appbuilder, 'Connection create'),
       (@v_client_system, 'Connection READ', @v_app_appbuilder, 'Connection read'),
       (@v_client_system, 'Connection UPDATE', @v_app_appbuilder, 'Connection update'),
       (@v_client_system, 'Connection DELETE', @v_app_appbuilder, 'Connection delete');

SELECT ID
from `security`.`security_role`
WHERE NAME = 'Data Connection Manager'
LIMIT 1
INTO @v_role_connection;

INSERT IGNORE INTO `security`.`security_role_permission` (ROLE_ID, PERMISSION_ID)
VALUES (@v_role_data, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Connection CREATE' LIMIT 1)),
       (@v_role_data, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Connection READ' LIMIT 1)),
       (@v_role_data, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Connection UPDATE' LIMIT 1)),
       (@v_role_data, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Connection DELETE' LIMIT 1));

SELECT ID
FROM `security`.`security_user`
WHERE USER_NAME = 'sysadmin'
LIMIT 1
INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID)
VALUES (@v_user_sysadmin, @v_role_connection);

CREATE TABLE `security`.`security_app_access`
(
    `ID`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID`   BIGINT UNSIGNED  NOT NULL COMMENT 'Client ID',
    `APP_ID`      BIGINT UNSIGNED  NOT NULL COMMENT 'Application ID',
    `EDIT_ACCESS` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Edit access',
    `CREATED_BY`  BIGINT UNSIGNED           DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`  TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`  BIGINT UNSIGNED           DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT`  TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_APPCLIENT` (`CLIENT_ID`, `APP_ID`),
    KEY `FK1_APP_CLIENT_ID` (`CLIENT_ID`),
    CONSTRAINT `FK1_APP_ACCESS_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK1_APP_ACCESS_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- V1__Initial script (FILES)

DROP DATABASE IF EXISTS `files`;

CREATE DATABASE IF NOT EXISTS `files` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `files`;

CREATE TABLE IF NOT EXISTS `files`.`files_access_path`
(
    ID                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    CLIENT_CODE           CHAR(8)         NOT NULL COMMENT 'Client code',
    USER_ID               BIGINT UNSIGNED            DEFAULT NULL COMMENT 'USER ID',
    RESOURCE_TYPE         ENUM ('STATIC', 'SECURED') DEFAULT 'STATIC' COMMENT 'Static or Secured resource',
    ACCESS_NAME           VARCHAR(256)               DEFAULT NULL COMMENT 'Role or Permission Name',
    WRITE_ACCESS          TINYINT                    DEFAULT 0 COMMENT 'Write access',
    PATH                  VARCHAR(1024)   NOT NULL COMMENT 'Path to the resource',
    ALLOW_SUB_PATH_ACCESS TINYINT                    DEFAULT 1 COMMENT 'Allow sub paths with same access',
    CREATED_BY            BIGINT UNSIGNED            DEFAULT NULL COMMENT 'ID of the user who created this row',
    CREATED_AT            TIMESTAMP       NOT NULL   DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    UPDATED_BY            BIGINT UNSIGNED            DEFAULT NULL COMMENT 'ID of the user who updated this row',
    UPDATED_AT            TIMESTAMP       NOT NULL   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (ID)
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- V6__Common Mongo changes (SECURITY)

use security;

select id
from `security`.`security_role`
where name = 'Application Manager'
limit 1
into @v_role_app_manager;
select id
from `security`.`security_permission`
where name = 'Application CREATE'
limit 1
into @v_permision_app_create;
INSERT INTO `security`.`security_role_permission` (role_id, permission_id)
values (@v_role_app_manager, @v_permision_app_create);

SELECT ID
from `security`.`security_client`
WHERE CODE = 'SYSTEM'
LIMIT 1
INTO @v_client_system;

SELECT ID
from `security`.`security_app`
WHERE app_code = 'appbuilder'
LIMIT 1
INTO @v_app_appbuilder;

INSERT INTO `security`.`security_role` (CLIENT_ID, NAME, APP_ID, DESCRIPTION)
VALUES (@v_client_system, 'Schema Manager', @v_app_appbuilder, 'Role to hold Schema operations permissions');

INSERT INTO `security`.`security_permission` (CLIENT_ID, NAME, APP_ID, DESCRIPTION)
VALUES (@v_client_system, 'Schema CREATE', @v_app_appbuilder, 'Schema create'),
       (@v_client_system, 'Schema READ', @v_app_appbuilder, 'Schema read'),
       (@v_client_system, 'Schema UPDATE', @v_app_appbuilder, 'Schema update'),
       (@v_client_system, 'Schema DELETE', @v_app_appbuilder, 'Schema delete');

SELECT ID
from `security`.`security_role`
WHERE NAME = 'Schema Manager'
LIMIT 1
INTO @v_role_schema;

INSERT IGNORE INTO `security`.`security_role_permission` (ROLE_ID, PERMISSION_ID)
VALUES (@v_role_schema, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Schema CREATE' LIMIT 1)),
       (@v_role_schema, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Schema READ' LIMIT 1)),
       (@v_role_schema, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Schema UPDATE' LIMIT 1)),
       (@v_role_schema, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Schema DELETE' LIMIT 1));

SELECT ID
FROM `security`.`security_user`
WHERE USER_NAME = 'sysadmin'
LIMIT 1
INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID)
VALUES (@v_user_sysadmin, @v_role_data);


-- V7__BPM Change script (SECURITY)

USE security;

SELECT ID
from `security`.`security_client`
WHERE CODE = 'SYSTEM'
LIMIT 1
INTO @v_client_system;

INSERT INTO `security`.`security_role` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'Workflow Manager', 'Role to hold Workflow operations permissions'),
       (@v_client_system, 'Template Manager', 'Role to hold Template operations permissions');

SELECT ID
from `security`.`security_role`
WHERE NAME = 'Workflow Manager'
LIMIT 1
INTO @v_role_workflow;
SELECT ID
from `security`.`security_role`
WHERE NAME = 'Template Manager'
LIMIT 1
INTO @v_role_template;

INSERT INTO `security`.`security_permission` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'Workflow CREATE', 'Workflow create'),
       (@v_client_system, 'Workflow READ', 'Workflow read'),
       (@v_client_system, 'Workflow UPDATE', 'Workflow update'),
       (@v_client_system, 'Workflow DELETE', 'Workflow delete'),
       (@v_client_system, 'Template CREATE', 'Template create'),
       (@v_client_system, 'Template READ', 'Template read'),
       (@v_client_system, 'Template UPDATE', 'Template update'),
       (@v_client_system, 'Template DELETE', 'Template delete');

INSERT IGNORE INTO `security`.`security_role_permission` (ROLE_ID, PERMISSION_ID)
VALUES (@v_role_workflow, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Workflow CREATE' LIMIT 1)),
       (@v_role_workflow, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Workflow READ' LIMIT 1)),
       (@v_role_workflow, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Workflow UPDATE' LIMIT 1)),
       (@v_role_workflow, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Workflow DELETE' LIMIT 1)),
       (@v_role_template, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Template CREATE' LIMIT 1)),
       (@v_role_template, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Template READ' LIMIT 1)),
       (@v_role_template, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Template UPDATE' LIMIT 1)),
       (@v_role_template, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Template DELETE' LIMIT 1));

SELECT ID
from `security`.`security_app`
WHERE app_code = 'appbuilder'
LIMIT 1
INTO @v_app_appbuilder;

INSERT INTO `security`.`security_role` (CLIENT_ID, NAME, APP_ID, DESCRIPTION)
VALUES (@v_client_system, 'Actions Manager', @v_app_appbuilder, 'Role to hold Actions operations permissions');

SELECT ID
from `security`.`security_role`
WHERE NAME = 'Actions Manager'
LIMIT 1
INTO @v_role_actions;

INSERT INTO `security`.`security_permission` (CLIENT_ID, NAME, APP_ID, DESCRIPTION)
VALUES (@v_client_system, 'Actions CREATE', @v_app_appbuilder, 'Actions create'),
       (@v_client_system, 'Actions READ', @v_app_appbuilder, 'Actions read'),
       (@v_client_system, 'Actions UPDATE', @v_app_appbuilder, 'Actions update'),
       (@v_client_system, 'Actions DELETE', @v_app_appbuilder, 'Actions delete');

INSERT IGNORE INTO `security`.`security_role_permission` (ROLE_ID, PERMISSION_ID)
VALUES (@v_role_actions, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Actions CREATE' LIMIT 1)),
       (@v_role_actions, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Actions READ' LIMIT 1)),
       (@v_role_actions, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Actions UPDATE' LIMIT 1)),
       (@v_role_actions, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Actions DELETE' LIMIT 1));

INSERT IGNORE INTO `security_package` (CLIENT_ID, CODE, NAME, DESCRIPTION, BASE)
VALUES (@v_client_system, 'FILES', 'Files Management',
        'Files management roles and permissions will be part of this package', FALSE),
       (@v_client_system, 'DATA', 'Data Management',
        'Data management roles and permissions will be part of this package', FALSE),
       (@v_client_system, 'WRKFL', 'Workflow Management',
        'Workflow management roles and permissions will be part of this package', FALSE),
       (@v_client_system, 'TEMPLT', 'Template Management',
        'Template management roles and permissions will be part of this package', FALSE);

SELECT ID
from `security`.`security_role`
WHERE NAME = 'Files Manager'
LIMIT 1
INTO @v_role_files;
SELECT ID
from `security`.`security_role`
WHERE NAME = 'Data Manager'
LIMIT 1
INTO @v_role_data;
SELECT ID
from `security`.`security_role`
WHERE NAME = 'Data Connection Manager'
LIMIT 1
INTO @v_role_connection;

SELECT ID
from `security_package`
WHERE CODE = 'FILES'
LIMIT 1
INTO @v_package_files;
SELECT ID
from `security_package`
WHERE CODE = 'DATA'
LIMIT 1
INTO @v_package_data;
SELECT ID
from `security_package`
WHERE CODE = 'WRKFL'
LIMIT 1
INTO @v_package_workflow;
SELECT ID
from `security_package`
WHERE CODE = 'TEMPLT'
LIMIT 1
INTO @v_package_templates;

INSERT IGNORE INTO `security_package_role` (ROLE_ID, PACKAGE_ID)
VALUES (@v_role_files, @v_package_files),
       (@v_role_data, @v_package_data),
       (@v_role_connection, @v_package_data),
       (@v_role_workflow, @v_package_workflow),
       (@v_role_actions, @v_package_workflow),
       (@v_role_template, @v_package_templates);

INSERT IGNORE INTO `security_client_package` (CLIENT_ID, PACKAGE_ID)
VALUES (@v_client_system, @v_package_files),
       (@v_client_system, @v_package_data),
       (@v_client_system, @v_package_workflow),
       (@v_client_system, @v_package_templates);

SELECT ID
FROM `security`.`security_user`
WHERE USER_NAME = 'sysadmin'
LIMIT 1
INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID)
VALUES (@v_user_sysadmin, @v_role_actions),
       (@v_user_sysadmin, @v_role_template),
       (@v_user_sysadmin, @v_role_workflow);

-- V8__Style permissions script (SECURITY)

use security;

SELECT ID
from `security_client`
WHERE CODE = 'SYSTEM'
LIMIT 1
INTO @v_client_system;

INSERT IGNORE INTO `security_permission` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'Style CREATE', 'Style create'),
       (@v_client_system, 'Style READ', 'Style read'),
       (@v_client_system, 'Style UPDATE', 'Style update'),
       (@v_client_system, 'Style DELETE', 'Style delete');

update security.security_permission
set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1)
where name like 'Style %';

INSERT IGNORE INTO `security_role` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'Style Manager', 'Role to hold style operations permissions');

update security.security_role
set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1)
where name like 'Style %';

SELECT ID
from `security_role`
WHERE NAME = 'Style Manager'
LIMIT 1
INTO @v_role_style;

INSERT IGNORE INTO `security_role_permission` (ROLE_ID, PERMISSION_ID)
VALUES (@v_role_style, (SELECT ID FROM `security_permission` WHERE NAME = 'Style CREATE' LIMIT 1)),
       (@v_role_style, (SELECT ID FROM `security_permission` WHERE NAME = 'Style READ' LIMIT 1)),
       (@v_role_style, (SELECT ID FROM `security_permission` WHERE NAME = 'Style UPDATE' LIMIT 1)),
       (@v_role_style, (SELECT ID FROM `security_permission` WHERE NAME = 'Style DELETE' LIMIT 1));

INSERT IGNORE INTO `security_package` (CLIENT_ID, CODE, NAME, DESCRIPTION, BASE)
VALUES (@v_client_system, 'STYLE', 'Style Management',
        'Style management roles and permissions will be part of this package', FALSE);

SELECT ID
FROM `security`.`security_user`
WHERE USER_NAME = 'sysadmin'
LIMIT 1
INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID)
VALUES (@v_user_sysadmin, @v_role_style);

SELECT ID
from `security_package`
WHERE CODE = 'STYLE'
LIMIT 1
INTO @v_package_style;

INSERT IGNORE INTO `security_client_package` (CLIENT_ID, PACKAGE_ID)
VALUES (@v_client_system, @v_package_style);

-- V9__Removed app from roles and permissions.sql (SECURITY)
UPDATE security.security_permission
SET APP_ID = null
WHERE APP_ID = (SELECT id from security.security_app WHERE app_code = 'appbuilder' LIMIT 1);

UPDATE security.security_role
SET APP_ID = null
WHERE APP_ID = (SELECT id from security.security_app WHERE app_code = 'appbuilder' LIMIT 1);

-- V10__Transport roles and permissions.sql (SECURITY)

use security;

SELECT ID
from `security_client`
WHERE CODE = 'SYSTEM'
LIMIT 1
INTO @v_client_system;

INSERT IGNORE INTO `security_permission` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'Transport CREATE', 'Transport create'),
       (@v_client_system, 'Transport READ', 'Transport read'),
       (@v_client_system, 'Transport UPDATE', 'Transport update'),
       (@v_client_system, 'Transport DELETE', 'Transport delete');

INSERT IGNORE INTO `security_role` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'Transport Manager', 'Role to hold Transport operations permissions');

SELECT ID
from `security_role`
WHERE NAME = 'Transport Manager'
LIMIT 1
INTO @v_role_transport;

INSERT IGNORE INTO `security_role_permission` (ROLE_ID, PERMISSION_ID)
VALUES (@v_role_transport, (SELECT ID FROM `security_permission` WHERE NAME = 'Transport CREATE' LIMIT 1)),
       (@v_role_transport, (SELECT ID FROM `security_permission` WHERE NAME = 'Transport READ' LIMIT 1)),
       (@v_role_transport, (SELECT ID FROM `security_permission` WHERE NAME = 'Transport UPDATE' LIMIT 1)),
       (@v_role_transport, (SELECT ID FROM `security_permission` WHERE NAME = 'Transport DELETE' LIMIT 1));

INSERT IGNORE INTO `security_package` (CLIENT_ID, CODE, NAME, DESCRIPTION, BASE)
VALUES (@v_client_system, 'TRANSP', 'Transport Management',
        'Transport management roles and permissions will be part of this package', FALSE);

SELECT ID
FROM `security`.`security_user`
WHERE USER_NAME = 'sysadmin'
LIMIT 1
INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID)
VALUES (@v_user_sysadmin, @v_role_transport);

SELECT ID
from `security_package`
WHERE CODE = 'TRANSP'
LIMIT 1
INTO @v_package_transport;

INSERT IGNORE INTO `security_client_package` (CLIENT_ID, PACKAGE_ID)
VALUES (@v_client_system, @v_package_transport);

SELECT ID
FROM `security`.`security_user`
WHERE USER_NAME = 'sysadmin'
LIMIT 1
INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID)
VALUES (@v_user_sysadmin, @v_role_transport);

-- V11__Default packages and roles for app (SECURITY)

use security;

CREATE TABLE IF NOT EXISTS `security_app_package`
(
    ID         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    CLIENT_ID  BIGINT UNSIGNED NOT NULL COMMENT 'Client ID for which this APP PACKAGE relation belongs to',
    APP_ID     BIGINT UNSIGNED NOT NULL COMMENT 'App ID for which this APP belongs to',
    PACKAGE_ID BIGINT UNSIGNED NOT NULL COMMENT 'Package ID',

    PRIMARY KEY (ID),
    UNIQUE KEY (CLIENT_ID, APP_ID, PACKAGE_ID),
    CONSTRAINT FK1_APP_PCK_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES security_client (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK2_APP_PCK_APP_ID FOREIGN KEY (APP_ID) REFERENCES security_app (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK3_APP_PCK_PCK_ID FOREIGN KEY (PACKAGE_ID) REFERENCES security_package (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `security_app_user_role`
(
    ID        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    CLIENT_ID BIGINT UNSIGNED NOT NULL COMMENT 'Client ID for which this APP PACKAGE relation belongs to',
    APP_ID    BIGINT UNSIGNED NOT NULL COMMENT 'App ID for which this APP belongs to',
    ROLE_ID   BIGINT UNSIGNED NOT NULL COMMENT 'Role ID',

    PRIMARY KEY (ID),
    UNIQUE KEY (CLIENT_ID, APP_ID, ROLE_ID),
    CONSTRAINT FK1_APP_UR_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES security_client (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK2_APP_UR_APP_ID FOREIGN KEY (APP_ID) REFERENCES security_app (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK3_APP_UR_ROLE_ID FOREIGN KEY (ROLE_ID) REFERENCES security_role (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `security`.`security_client`
    DROP INDEX `UK2_CLIENT_NAME`;

-- V12__Making new client type (SECURITY)
use security;

INSERT INTO `security`.`security_client_type` (`CODE`, `TYPE`, `DESCRIPTION`)
VALUES ('INDV', 'Individual', 'Individual user who are not business clients');

-- V13__Event def and actions roles and permissions (SECURITY)

use security;

SELECT ID
from `security_client`
WHERE CODE = 'SYSTEM'
LIMIT 1
INTO @v_client_system;

INSERT IGNORE INTO `security_permission` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'EventDefinition CREATE', 'EventDefinition create'),
       (@v_client_system, 'EventDefinition READ', 'EventDefinition read'),
       (@v_client_system, 'EventDefinition UPDATE', 'EventDefinition update'),
       (@v_client_system, 'EventDefinition DELETE', 'EventDefinition delete');

INSERT IGNORE INTO `security_role` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'EventDefinition Manager', 'Role to hold Event Definition operations permissions');

SELECT ID
from `security_role`
WHERE NAME = 'EventDefinition Manager'
LIMIT 1
INTO @v_role_evedef;

INSERT IGNORE INTO `security_role_permission` (ROLE_ID, PERMISSION_ID)
VALUES (@v_role_evedef, (SELECT ID FROM `security_permission` WHERE NAME = 'EventDefinition CREATE' LIMIT 1)),
       (@v_role_evedef, (SELECT ID FROM `security_permission` WHERE NAME = 'EventDefinition READ' LIMIT 1)),
       (@v_role_evedef, (SELECT ID FROM `security_permission` WHERE NAME = 'EventDefinition UPDATE' LIMIT 1)),
       (@v_role_evedef, (SELECT ID FROM `security_permission` WHERE NAME = 'EventDefinition DELETE' LIMIT 1));

INSERT IGNORE INTO `security_package` (CLIENT_ID, CODE, NAME, DESCRIPTION, BASE)
VALUES (@v_client_system, 'EVEDE', 'EventDefinition Management',
        'Event Definition management roles and permissions will be part of this package', FALSE);

SELECT ID
FROM `security`.`security_user`
WHERE USER_NAME = 'sysadmin'
LIMIT 1
INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID)
VALUES (@v_user_sysadmin, @v_role_evedef);

SELECT ID
from `security_package`
WHERE CODE = 'EVEDE'
LIMIT 1
INTO @v_package_evedef;

INSERT IGNORE INTO `security_client_package` (CLIENT_ID, PACKAGE_ID)
VALUES (@v_client_system, @v_package_evedef);

SELECT ID
FROM `security`.`security_user`
WHERE USER_NAME = 'sysadmin'
LIMIT 1
INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID)
VALUES (@v_user_sysadmin, @v_role_evedef);


INSERT IGNORE INTO `security_permission` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'EventAction CREATE', 'EventAction create'),
       (@v_client_system, 'EventAction READ', 'EventAction read'),
       (@v_client_system, 'EventAction UPDATE', 'EventAction update'),
       (@v_client_system, 'EventAction DELETE', 'EventAction delete');

INSERT IGNORE INTO `security_role` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'EventAction Manager', 'Role to hold Event Action operations permissions');

SELECT ID
from `security_role`
WHERE NAME = 'EventAction Manager'
LIMIT 1
INTO @v_role_eveact;

INSERT IGNORE INTO `security_role_permission` (ROLE_ID, PERMISSION_ID)
VALUES (@v_role_eveact, (SELECT ID FROM `security_permission` WHERE NAME = 'EventAction CREATE' LIMIT 1)),
       (@v_role_eveact, (SELECT ID FROM `security_permission` WHERE NAME = 'EventAction READ' LIMIT 1)),
       (@v_role_eveact, (SELECT ID FROM `security_permission` WHERE NAME = 'EventAction UPDATE' LIMIT 1)),
       (@v_role_eveact, (SELECT ID FROM `security_permission` WHERE NAME = 'EventAction DELETE' LIMIT 1));

INSERT IGNORE INTO `security_package` (CLIENT_ID, CODE, NAME, DESCRIPTION, BASE)
VALUES (@v_client_system, 'EVEAC', 'EventAction Management',
        'Event Action management roles and permissions will be part of this package', FALSE);

SELECT ID
FROM `security`.`security_user`
WHERE USER_NAME = 'sysadmin'
LIMIT 1
INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID)
VALUES (@v_user_sysadmin, @v_role_eveact);

SELECT ID
from `security_package`
WHERE CODE = 'EVEAC'
LIMIT 1
INTO @v_package_eveact;

INSERT IGNORE INTO `security_client_package` (CLIENT_ID, PACKAGE_ID)
VALUES (@v_client_system, @v_package_eveact);

SELECT ID
FROM `security`.`security_user`
WHERE USER_NAME = 'sysadmin'
LIMIT 1
INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID)
VALUES (@v_user_sysadmin, @v_role_eveact);

-- V14__Package and role correction (SECURITY)

INSERT IGNORE INTO `security`.`security_package` (`CLIENT_ID`, `CODE`, `NAME`, `DESCRIPTION`, `BASE`)
VALUES ('1', 'SCHEM', 'Schema Management', 'Schema management roles and permissions will be part of this package', '0');

SELECT id
from security.security_package
where code = 'SCHEM'
into @v_package_schema;
SELECT id
from security.security_package
where code = 'EVEAC'
into @v_package_event_action;
SELECT id
from security.security_package
where code = 'EVEDE'
into @v_package_event_def;
SELECT id
from security.security_package
where code = 'TRANSP'
into @v_package_tranport;
SELECT id
from security.security_package
where code = 'STYLE'
into @v_package_style;

SELECT id
from security.security_role
where name = 'EventAction Manager'
into @v_role_event_action;
SELECT id
from security.security_role
where name = 'EventDefinition Manager'
into @v_role_event_def;
SELECT id
from security.security_role
where name = 'Transport Manager'
into @v_role_transport;
SELECT id
from security.security_role
where name = 'Schema Manager'
into @v_role_schema;
SELECT id
from security.security_role
where name = 'Style Manager'
into @v_role_style;

INSERT IGNORE INTO `security`.`security_package_role` (role_id, package_id)
values (@v_role_event_action, @v_package_event_action),
       (@v_role_event_def, @v_package_event_def),
       (@v_role_transport, @v_package_schema),
       (@v_role_schema, @v_package_tranport),
       (@v_role_style, @v_package_style);

-- V15__SSL related scripts (SECURITY)

use security;

DROP TABLE IF EXISTS `security_ssl_challenge`;
DROP TABLE IF EXISTS `security_ssl_request`;
DROP TABLE IF EXISTS `security_ssl_certificate`;

CREATE TABLE `security_ssl_certificate`
(
    `ID`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `URL_ID`          BIGINT UNSIGNED NOT NULL COMMENT 'URL ID for which this SSL certificate belongs to',
    `CRT`             TEXT            NOT NULL COMMENT 'SSL certificate',
    `CRT_CHAIN`       TEXT            NOT NULL COMMENT 'SSL certificate chain',
    `CRT_KEY`         TEXT            NOT NULL COMMENT 'SSL certificate key',
    `CSR`             TEXT            NOT NULL COMMENT 'SSL certificate signing request',
    `DOMAINS`         VARCHAR(1024)   NOT NULL COMMENT 'Domains for which this SSL certificate is valid',
    `ORGANIZATION`    VARCHAR(1024)   NOT NULL COMMENT 'Organization for which this SSL certificate is valid',
    `EXPIRY_DATE`     TIMESTAMP       NOT NULL COMMENT 'Expiry date of this SSL certificate',
    `ISSUER`          VARCHAR(1024)   NOT NULL COMMENT 'Issuer of this SSL certificate',
    `CURRENT`         BOOLEAN         NOT NULL DEFAULT TRUE COMMENT 'Is this the current SSL certificate for the URL',
    `AUTO_RENEW_TILL` TIMESTAMP       NULL     DEFAULT NULL COMMENT 'Time till which this SSL certificate is auto renewed',

    `CREATED_BY`      BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`      BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT`      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    CONSTRAINT `FK1_SSL_CRT_CLNT_URL_ID` FOREIGN KEY (`URL_ID`) REFERENCES `security_client_url` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE TABLE `security_ssl_request`
(
    `ID`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `URL_ID`        BIGINT UNSIGNED NOT NULL COMMENT 'URL ID for which this SSL certificate belongs to',
    `DOMAINS`       VARCHAR(1024)   NOT NULL COMMENT 'Domains for which this SSL certificate is valid',
    `ORGANIZATION`  VARCHAR(1024)   NOT NULL COMMENT 'Organization for which this SSL certificate is valid',
    `CRT_KEY`       TEXT            NOT NULL COMMENT 'SSL certificate key',
    `CSR`           TEXT            NOT NULL COMMENT 'SSL certificate signing request',
    `VALIDITY`      INT UNSIGNED    NOT NULL COMMENT 'Validity of the SSL certificate in months',
    `FAILED_REASON` TEXT                     DEFAULT NULL COMMENT 'Reason for challenge failure',
    `UPDATED_BY`    BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT`    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY (`URL_ID`),
    CONSTRAINT `FK1_SSL_REQ_CLNT_URL_ID` FOREIGN KEY (`URL_ID`) REFERENCES `security_client_url` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE TABLE `security_ssl_challenge`
(
    `ID`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `REQUEST_ID`        BIGINT UNSIGNED NOT NULL COMMENT 'SSL request ID for which this challenge belongs to',
    `CHALLENGE_TYPE`    VARCHAR(32)     NOT NULL COMMENT 'Challenge type',
    `DOMAIN`            VARCHAR(1024)   NOT NULL COMMENT 'Domain for which this challenge is valid',
    `TOKEN`             VARCHAR(1024)   NOT NULL COMMENT 'Challenge token for HTTP-01 challenge/Challenge TXT record name for DNS-01 challenge',
    `AUTHORIZATION`     VARCHAR(1024)   NOT NULL COMMENT 'Challenge key authorization for HTTP-01 challenge/Digest for DNS-01 challenge',

    `STATUS`            VARCHAR(128)    NOT NULL DEFAULT 'PENDING' COMMENT 'Challenge status',
    `FAILED_REASON`     TEXT                     DEFAULT NULL COMMENT 'Reason for challenge failure',
    `LAST_VALIDATED_AT` TIMESTAMP       NULL     DEFAULT NULL COMMENT 'Time when this challenge is validated',
    `RETRY_COUNT`       INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT 'Number of times this challenge is retried',

    `CREATED_BY`        BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`        BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT`        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    CONSTRAINT `FK1_SSL_CHLNG_REQ_ID` FOREIGN KEY (`REQUEST_ID`) REFERENCES `security_ssl_request` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- V16__App Properties script (SECURITY)

use security;

DROP TABLE IF EXISTS `security_app_property`;

CREATE TABLE `security_app_property`
(
    `ID`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `APP_ID`     BIGINT UNSIGNED NOT NULL COMMENT 'App ID for which this property belongs to',
    `CLIENT_ID`  BIGINT UNSIGNED NOT NULL COMMENT 'Client ID for which this property belongs to',

    `NAME`       VARCHAR(128)    NOT NULL COMMENT 'Name of the property',
    `VALUE`      TEXT            NULL     DEFAULT NULL COMMENT 'Value of the property',
    `CREATED_BY` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY (`APP_ID`, `CLIENT_ID`, `NAME`),
    CONSTRAINT `FK1_APP_PROP_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_PROP_CLNT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `security_app`
    ADD COLUMN `IS_TEMPLATE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Is this app or site a template?';

-- V17__APPID to package (SECURITY)

use security;


ALTER TABLE `security`.`security_package`
    ADD COLUMN `APP_ID` BIGINT UNSIGNED NULL DEFAULT NULL AFTER `CLIENT_ID`;

ALTER TABLE `security`.`security_package`
    ADD CONSTRAINT `FK2_PACKAGE_APP_ID`
        FOREIGN KEY (`APP_ID`)
            REFERENCES `security`.`security_app` (`ID`)
            ON DELETE RESTRICT
            ON UPDATE RESTRICT;

-- V1__Initial script.sql (CORE)

DROP DATABASE IF EXISTS `core`;

CREATE DATABASE IF NOT EXISTS `core` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


-- V2__Core Tokens script.sql (CORE)
use core;

CREATE TABLE IF NOT EXISTS core_tokens
(
    ID              BIGINT UNSIGNED            NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    USER_ID         BIGINT UNSIGNED            NULL COMMENT 'User ID',
    CLIENT_CODE     CHAR(8)                    NOT NULL COMMENT 'Client Code',
    APP_CODE        CHAR(8)                    NOT NULL COMMENT 'App Code',
    CONNECTION_NAME VARCHAR(255)               NOT NULL COMMENT 'Connection for which token is generated',
    TOKEN_TYPE      ENUM ('ACCESS', 'REFRESH') NOT NULL COMMENT 'Type of token that is generated',
    TOKEN           TEXT                       NOT NULL COMMENT 'Generated Token',
    IS_REVOKED      BOOLEAN                    NOT NULL DEFAULT 0 COMMENT 'If false, means token is working',
    EXPIRES_AT      TIMESTAMP COMMENT 'Time when this token will expire',

    CREATED_BY      BIGINT UNSIGNED                     DEFAULT NULL COMMENT 'ID of the user who created this row',
    CREATED_AT      TIMESTAMP                  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (ID),
    KEY K1_USER_CLIENT_APP_CODE_CONNECTION (USER_ID, CLIENT_CODE, APP_CODE, CONNECTION_NAME),
    KEY K2_CLIENT_APP_CONNECTION (CLIENT_CODE, APP_CODE, CONNECTION_NAME)
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- V2__Update core tokens script.sql (CORE)

use core;

ALTER TABLE core_tokens
    ADD COLUMN STATE             CHAR(64)        DEFAULT NULL COMMENT 'State of the token' AFTER APP_CODE,
    ADD COLUMN TOKEN_METADATA    JSON            DEFAULT NULL COMMENT 'Metadata of the token' AFTER TOKEN,
    ADD COLUMN IS_LIFETIME_TOKEN BOOLEAN   NOT NULL DEFAULT 0 COMMENT 'If true, token will not have expiry' AFTER EXPIRES_AT,
    ADD COLUMN UPDATED_BY        BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row' AFTER CREATED_BY,
    ADD COLUMN UPDATED_AT        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated' AFTER CREATED_AT,
    MODIFY COLUMN APP_CODE char(64) NOT NULL COMMENT 'App Code',
    MODIFY COLUMN CLIENT_CODE char(8) NOT NULL COMMENT 'Client Code',
    MODIFY COLUMN TOKEN TEXT DEFAULT NULL COMMENT 'Generated Token',
    MODIFY COLUMN EXPIRES_AT TIMESTAMP NULL COMMENT 'Time when this token will expire';

ALTER TABLE core_tokens
    ADD CONSTRAINT UK_CORE_TOKEN_STATE UNIQUE (STATE);


-- V1__Initial script.sql (MULTI)

DROP DATABASE IF EXISTS `multi`;

CREATE DATABASE IF NOT EXISTS `multi` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


-- V1__Initial script.sql (SCHEDULAR)

DROP DATABASE IF EXISTS `schedular`;

CREATE DATABASE IF NOT EXISTS `schedular` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- V18__APP access type (SECURITY)

use security;


ALTER TABLE `security`.`security_app`
    DROP COLUMN `IS_TEMPLATE`,
    ADD COLUMN `APP_ACCESS_TYPE` ENUM ('OWN', 'ANY', 'EXPLICIT') NOT NULL DEFAULT 'OWN' AFTER `APP_TYPE`;

-- V19__APP Thumbnail URL (SECURITY)

use security;


ALTER TABLE `security`.`security_app`
    ADD COLUMN `THUMB_URL` VARCHAR(1024) NULL DEFAULT NULL AFTER `APP_ACCESS_TYPE`;

-- V20__Code Access security (SEUCRITY)

USE security;

CREATE TABLE `security_code_access`
(
    `ID`         BIGINT unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `EMAIL_ID`   VARCHAR(320)    NOT NULL COMMENT 'Email id of the client',
    `CODE`       CHAR(32)        NOT NULL COMMENT 'Unique access code for logging in',
    `APP_ID`     BIGINT unsigned NOT NULL COMMENT 'App id to which this user belongs to.',
    `CLIENT_ID`  BIGINT unsigned NOT NULL COMMENT 'Client id to which this user belongs to.',
    `CREATED_BY` BIGINT unsigned          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    PRIMARY KEY (`ID`),
    UNIQUE INDEX `UK1_CODE_ACCESS_CODE` (`APP_ID`, `CLIENT_ID`, `CODE`) VISIBLE,
    UNIQUE INDEX `UK1_CODE_ACCESS_EMAIL_APP_CLIENT` (`APP_ID`, `CLIENT_ID`, `EMAIL_ID`) VISIBLE,
    CONSTRAINT `FK1_CODE_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK2_CODE_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;


-- V21__App Dependency script (SECURITY)
use security;

DROP TABLE IF EXISTS `security_app_dependency`;

CREATE TABLE `security_app_dependency`
(
    `ID`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `APP_ID`     BIGINT UNSIGNED NOT NULL COMMENT 'App ID',
    `DEP_APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'App ID of the dependent app',

    `CREATED_BY` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY (`APP_ID`, `DEP_APP_ID`),
    CONSTRAINT `FK1_APP_DEP_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_DEP_DEP_APP_ID` FOREIGN KEY (`DEP_APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `security_app_reg_access`;

CREATE TABLE `security_app_reg_access`
(
    `ID`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `CLIENT_ID`  BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
    `APP_ID`     BIGINT UNSIGNED NOT NULL COMMENT 'App ID',
    `DEP_APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'App ID of the dependent app',

    `CREATED_BY` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY (`CLIENT_ID`, `APP_ID`, `DEP_APP_ID`),
    CONSTRAINT `FK1_APP_REG_ACC_CLNT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_ACC_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK3_APP_REG_ACC_DEP_APP_ID` FOREIGN KEY (`DEP_APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `security_app_reg_customer_access`;

CREATE TABLE `security_app_reg_customer_access`
(
    `ID`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `CLIENT_ID`  BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
    `APP_ID`     BIGINT UNSIGNED NOT NULL COMMENT 'App ID',
    `DEP_APP_ID` BIGINT UNSIGNED NOT NULL COMMENT 'App ID of the dependent app',

    `CREATED_BY` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY (`CLIENT_ID`, `APP_ID`, `DEP_APP_ID`),
    CONSTRAINT `FK1_APP_REG_CUST_ACC_CLNT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_CUST_ACC_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `security_app_customer_user_role`;

CREATE TABLE `security_app_customer_user_role`
(
    `ID`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `CLIENT_ID`  BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
    `APP_ID`     BIGINT UNSIGNED NOT NULL COMMENT 'App ID',
    `ROLE_ID`    BIGINT UNSIGNED NOT NULL COMMENT 'Role ID',

    `CREATED_BY` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY (`CLIENT_ID`, `APP_ID`, `ROLE_ID`),
    CONSTRAINT `FK1_APP_CUST_USER_ROLE_CLNT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_CUST_USER_ROLE_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK3_APP_CUST_USER_ROLE_ROLE_ID` FOREIGN KEY (`ROLE_ID`) REFERENCES `security_role` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `security_app_customer_package`;

CREATE TABLE `security_app_customer_package`
(
    `ID`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `CLIENT_ID`  BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
    `APP_ID`     BIGINT UNSIGNED NOT NULL COMMENT 'App ID',
    `PACKAGE_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Package ID',

    `CREATED_BY` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY (`CLIENT_ID`, `APP_ID`, `PACKAGE_ID`),
    CONSTRAINT `FK1_APP_CUST_PKG_CLNT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_CUST_PKG_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK3_APP_CUST_PKG_PKG_ID` FOREIGN KEY (`PACKAGE_ID`) REFERENCES `security_package` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- V22 Registration details (SECURITY)
use security;

DROP TABLE IF EXISTS `security_app_customer_package`;
DROP TABLE IF EXISTS `security_app_customer_user_role`;
DROP TABLE IF EXISTS `security_app_reg_customer_access`;
DROP TABLE IF EXISTS `security_app_package`;
DROP TABLE IF EXISTS `security_app_user_role`;


ALTER TABLE `security`.`security_app`
    ADD COLUMN `APP_USAGE_TYPE` ENUM ('S', 'B', 'B2C', 'B2B', 'B2X', 'B2B2B', 'B2B2C', 'B2B2X', 'B2X2C', 'B2X2X') NOT NULL DEFAULT 'S' COMMENT 'S - Standalone (Mostly for sites), B - Business only, B to C Consumer, B Business, X Any, and so on so forth.' AFTER `THUMB_URL`;

ALTER TABLE `security`.`security_client`
    ADD COLUMN `BUSINESS_TYPE` CHAR(10) NOT NULL DEFAULT 'COMMON' COMMENT 'At each llevel of business client, customer and consumer there can be different business types.' AFTER `STATUS_CODE`;

DROP TABLE IF EXISTS `security_app_reg_access`;

CREATE TABLE `security_app_reg_access`
(
    `ID`            BIGINT UNSIGNED                         NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `CLIENT_ID`     BIGINT UNSIGNED                         NOT NULL COMMENT 'Client ID',
    `CLIENT_TYPE`   CHAR(4)                                 NOT NULL DEFAULT 'BUS' COMMENT 'Client type',
    `APP_ID`        BIGINT UNSIGNED                         NOT NULL COMMENT 'App ID',
    `ALLOW_APP_ID`  BIGINT UNSIGNED                         NOT NULL COMMENT 'App ID of the dependent app',
    `LEVEL`         ENUM ('CLIENT', 'CUSTOMER', 'CONSUMER') NOT NULL DEFAULT 'CLIENT' COMMENT 'Access level',
    `BUSINESS_TYPE` CHAR(10)                                NOT NULL DEFAULT 'COMMON' COMMENT 'Business type',

    `CREATED_BY`    BIGINT UNSIGNED                                  DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`    TIMESTAMP                               NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (`ID`),
    UNIQUE KEY (`CLIENT_ID`, `APP_ID`, `ALLOW_APP_ID`, `CLIENT_TYPE`, `LEVEL`, `BUSINESS_TYPE`),
    CONSTRAINT `FK1_APP_REG_ACC_CLNT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_ACC_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK3_APP_REG_ACC_ALLOW_APP_ID` FOREIGN KEY (`ALLOW_APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK4_APP_REG_ACC_CLIENT_TYPE` FOREIGN KEY (`CLIENT_TYPE`) REFERENCES `security_client_type` (`CODE`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `security_app_reg_package`;

CREATE TABLE `security_app_reg_package`
(
    `ID`            BIGINT UNSIGNED                         NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `CLIENT_ID`     BIGINT UNSIGNED                         NOT NULL COMMENT 'Client ID',
    `CLIENT_TYPE`   CHAR(4)                                 NOT NULL DEFAULT 'BUS' COMMENT 'Client type',
    `APP_ID`        BIGINT UNSIGNED                         NOT NULL COMMENT 'App ID',
    `PACKAGE_ID`    BIGINT UNSIGNED                         NOT NULL COMMENT 'Package ID',
    `LEVEL`         ENUM ('CLIENT', 'CUSTOMER', 'CONSUMER') NOT NULL DEFAULT 'CLIENT' COMMENT 'Access level',
    `BUSINESS_TYPE` CHAR(10)                                NOT NULL DEFAULT 'COMMON' COMMENT 'Business type',

    `CREATED_BY`    BIGINT UNSIGNED                                  DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`    TIMESTAMP                               NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (`ID`),

    UNIQUE KEY (`CLIENT_ID`, `APP_ID`, `PACKAGE_ID`, `CLIENT_TYPE`, `LEVEL`, `BUSINESS_TYPE`),
    CONSTRAINT `FK1_APP_REG_PKG_CLNT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_PKG_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK3_APP_REG_PKG_PKG_ID` FOREIGN KEY (`PACKAGE_ID`) REFERENCES `security_package` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK4_APP_REG_PKG_CLIENT_TYPE` FOREIGN KEY (`CLIENT_TYPE`) REFERENCES `security_client_type` (`CODE`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `security_app_reg_user_role`;

CREATE TABLE `security_app_reg_user_role`
(
    `ID`            BIGINT UNSIGNED                         NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `CLIENT_ID`     BIGINT UNSIGNED                         NOT NULL COMMENT 'Client ID',
    `CLIENT_TYPE`   CHAR(4)                                 NOT NULL DEFAULT 'BUS' COMMENT 'Client type',
    `APP_ID`        BIGINT UNSIGNED                         NOT NULL COMMENT 'App ID',
    `ROLE_ID`       BIGINT UNSIGNED                         NOT NULL COMMENT 'Role ID',
    `LEVEL`         ENUM ('CLIENT', 'CUSTOMER', 'CONSUMER') NOT NULL DEFAULT 'CLIENT' COMMENT 'Access level',
    `BUSINESS_TYPE` CHAR(10)                                NOT NULL DEFAULT 'COMMON' COMMENT 'Business type',

    `CREATED_BY`    BIGINT UNSIGNED                                  DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`    TIMESTAMP                               NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (`ID`),

    UNIQUE KEY (`CLIENT_ID`, `APP_ID`, `ROLE_ID`, `CLIENT_TYPE`, `LEVEL`, `BUSINESS_TYPE`),
    CONSTRAINT `FK1_APP_REG_ROLE_CLNT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_ROLE_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK3_APP_REG_ROLE_ROLE_ID` FOREIGN KEY (`ROLE_ID`) REFERENCES `security_role` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK4_APP_REG_ROLE_CLIENT_TYPE` FOREIGN KEY (`CLIENT_TYPE`) REFERENCES `security_client_type` (`CODE`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `security_app_reg_file_access`;

CREATE TABLE `security_app_reg_file_access`
(
    `ID`                    BIGINT UNSIGNED                         NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `CLIENT_ID`             BIGINT UNSIGNED                         NOT NULL COMMENT 'Client ID',
    `CLIENT_TYPE`           CHAR(4)                                 NOT NULL DEFAULT 'BUS' COMMENT 'Client type',
    `APP_ID`                BIGINT UNSIGNED                         NOT NULL COMMENT 'App ID',
    `LEVEL`                 ENUM ('CLIENT', 'CUSTOMER', 'CONSUMER') NOT NULL DEFAULT 'CLIENT' COMMENT 'Access level',
    `BUSINESS_TYPE`         CHAR(10)                                NOT NULL DEFAULT 'COMMON' COMMENT 'Business type',

    `RESOURCE_TYPE`         ENUM ('STATIC','SECURED')               NOT NULL DEFAULT 'STATIC' COMMENT 'Resource type',
    `ACCESS_NAME`           VARCHAR(256)                            NOT NULL COMMENT 'Access name - Usually the role expression',
    `WRITE_ACCESS`          BOOLEAN                                 NOT NULL DEFAULT FALSE COMMENT 'Write access',
    `PATH`                  VARCHAR(512)                            NOT NULL COMMENT 'Path of the file',
    `ALLOW_SUB_PATH_ACCESS` BOOLEAN                                 NOT NULL DEFAULT TRUE COMMENT 'Allow sub path access',

    `CREATED_BY`            BIGINT UNSIGNED                                  DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`            TIMESTAMP                               NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (`ID`),

    CONSTRAINT `FK1_APP_REG_FILE_ACC_CLNT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_FILE_ACC_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK4_APP_REG_FILE_ACC_CLIENT_TYPE` FOREIGN KEY (`CLIENT_TYPE`) REFERENCES `security_client_type` (`CODE`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE `security`.`security_app_reg_access`
    ADD COLUMN `WRITE_ACCESS` TINYINT(1) NOT NULL DEFAULT 0 AFTER `ALLOW_APP_ID`;

-- V23__Address (SECUITY)

use security;

DROP TABLE IF EXISTS `security_client_address`;
DROP TABLE IF EXISTS `security_user_address`;
DROP TABLE IF EXISTS `security_address`;

CREATE TABLE `security_address`
(
    `ID`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `ADDRESS_TYPE`    VARCHAR(128)             DEFAULT NULL COMMENT 'Address type',
    `ADDRESS_COMMENT` VARCHAR(512)             DEFAULT NULL COMMENT 'Address comment',

    `ADDRESS_LINE1`   VARCHAR(512)             DEFAULT NULL COMMENT 'Address line 1',
    `ADDRESS_LINE2`   VARCHAR(512)             DEFAULT NULL COMMENT 'Address line 2',
    `ADDRESS_LINE3`   VARCHAR(512)             DEFAULT NULL COMMENT 'Address line 3',
    `CITY`            VARCHAR(256)             DEFAULT NULL COMMENT 'City',
    `STATE`           VARCHAR(256)             DEFAULT NULL COMMENT 'State',
    `COUNTRY`         VARCHAR(256)             DEFAULT NULL COMMENT 'Country',
    `PIN`             VARCHAR(32)              DEFAULT NULL COMMENT 'Pin code',
    `PHONE`           VARCHAR(32)              DEFAULT NULL COMMENT 'Phone number',
    `LANDMARK`        VARCHAR(512)             DEFAULT NULL COMMENT 'Landmark',

    `LATITUDE`        DECIMAL(10, 8)           DEFAULT NULL COMMENT 'Latitude',
    `LONGITUDE`       DECIMAL(11, 8)           DEFAULT NULL COMMENT 'Longitude',

    `MAP_LOCATION`    TEXT                     DEFAULT NULL COMMENT 'Map location',


    `CREATED_BY`      BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`      BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who last updated this row',
    `UPDATED_AT`      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is last updated',

    PRIMARY KEY (`ID`)
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE TABLE `security_user_address`
(
    `ID`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `USER_ID`    BIGINT UNSIGNED NOT NULL COMMENT 'User ID',
    `ADDRESS_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Address ID',

    PRIMARY KEY (`ID`),
    CONSTRAINT `FK1_USER_ADDRESS_USER_ID` FOREIGN KEY (`USER_ID`) REFERENCES `security_user` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_USER_ADDRESS_ADDRESS_ID` FOREIGN KEY (`ADDRESS_ID`) REFERENCES `security_address` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE TABLE `security_client_address`
(
    `ID`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `CLIENT_ID`  BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
    `ADDRESS_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Address ID',

    PRIMARY KEY (`ID`),
    CONSTRAINT `FK1_CLIENT_ADDRESS_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_CLIENT_ADDRESS_ADDRESS_ID` FOREIGN KEY (`ADDRESS_ID`) REFERENCES `security_address` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- V3__Path defaults.sql(FILES)
use files;

ALTER TABLE `files`.`files_access_path`
    CHANGE COLUMN `PATH` `PATH` VARCHAR(1024) NOT NULL DEFAULT '' COMMENT 'Path to the resource';

-- V24__Update Packages and Roles Constraints

use security;

ALTER TABLE `security`.`security_package`
    DROP CONSTRAINT `UK2_PACKAGE_NAME`;

ALTER TABLE `security`.`security_package`
    ADD CONSTRAINT `UK2_PACKAGE_NAME_APP_ID` UNIQUE (`NAME`, `APP_ID`);

ALTER TABLE `security`.`security_role`
    DROP CONSTRAINT `UK1_ROLE_NAME`;

ALTER TABLE `security`.`security_role`
    ADD CONSTRAINT `UK1_ROLE_NAME_APP_ID` UNIQUE (`NAME`, `APP_ID`);

-- V2__secured asset key.sql
CREATE TABLE `files_secured_access_key`
(
    `id`             bigint unsigned                          NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `path`           varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Path which needs to be secured.',
    `access_key`     char(16) COLLATE utf8mb4_unicode_ci      NOT NULL COMMENT 'Key used for securing the file.',
    `access_till`    timestamp                                NOT NULL COMMENT 'Time which the path can be accessed',
    `access_limit`   bigint unsigned                                   DEFAULT NULL COMMENT 'Maximum times in which the file can be accessed',
    `accessed_count` bigint unsigned                          NOT NULL DEFAULT '0' COMMENT 'Tracks count of file accessed',
    `created_by`     bigint unsigned                                   DEFAULT NULL COMMENT 'User id who created this row.',
    `created_at`     timestamp                                NULL     DEFAULT NULL COMMENT 'Time at which this row was created',
    PRIMARY KEY (`id`),
    UNIQUE KEY `access_key_UNIQUE` (`access_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- V4__Secured Asset Key.sql (Files)
DROP TABLE IF EXISTS files.`files_secured_access_key`;

CREATE TABLE files.`files_secured_access_keys`
(
    `ID`             BIGINT UNSIGNED                            NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `PATH`           VARCHAR(1024) COLLATE `utf8mb4_unicode_ci` NOT NULL COMMENT 'Path which needs to be secured.',
    `ACCESS_KEY`     CHAR(13) COLLATE `utf8mb4_unicode_ci`      NOT NULL COMMENT 'Key used for securing the file.',
    `ACCESS_TILL`    TIMESTAMP                                  NOT NULL COMMENT 'Time which the path can be accessed',
    `ACCESS_LIMIT`   BIGINT UNSIGNED                                     DEFAULT NULL COMMENT 'Maximum times in which the file can be accessed',
    `ACCESSED_COUNT` BIGINT UNSIGNED                            NOT NULL DEFAULT '0' COMMENT 'Tracks count of file accessed',
    `CREATED_BY`     BIGINT UNSIGNED                                     DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`     TIMESTAMP                                  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_ACCESS_KEY` (`ACCESS_KEY`)
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;


-- V25__User Integration Properties.sql(SECURITY)
use security;

DROP TABLE IF EXISTS `security_integration_tokens`;
DROP TABLE IF EXISTS `security_app_reg_integration_tokens`;
DROP TABLE IF EXISTS `security_app_reg_integration_scopes`;
DROP TABLE IF EXISTS `security_app_reg_integration`;
DROP TABLE IF EXISTS `security_integration_scopes`;
DROP TABLE IF EXISTS `security_integration`;

-- app integration scopes
CREATE TABLE `security_app_reg_integration`
(
    `ID`          BIGINT UNSIGNED                                           NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `APP_ID`      BIGINT UNSIGNED                                           NOT NULL COMMENT 'App ID',
    `CLIENT_ID`   BIGINT UNSIGNED                                           NOT NULL COMMENT 'Client ID',
    `PLATFORM`    ENUM ('GOOGLE', 'META', 'APPLE', 'SSO', 'MICROSOFT', 'X') NOT NULL COMMENT 'Platform',
    `INTG_ID`     VARCHAR(512)                                              NOT NULL COMMENT 'Integration ID',
    `INTG_SECRET` VARCHAR(512)                                              NOT NULL COMMENT 'Integration Secret',
    `LOGIN_URI`   VARCHAR(2083)                                             NOT NULL COMMENT 'URI for login',

    `CREATED_BY`  BIGINT UNSIGNED                                                    DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`  TIMESTAMP                                                 NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`  BIGINT UNSIGNED                                                    DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT`  TIMESTAMP                                                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),

    UNIQUE KEY UK1_APP_REG_INTEGRATION (`APP_ID`, `CLIENT_ID`, `PLATFORM`),
    CONSTRAINT `FK1_APP_REG_INTEGRATION_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_INTEGRATION_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- user integration tokens
CREATE TABLE `security_app_reg_integration_tokens`
(
    `ID`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `INTEGRATION_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Integration ID',
    `AUTH_CODE`      VARCHAR(512)             DEFAULT NULL COMMENT 'User Consent Auth Code',
    `STATE`          CHAR(64)                 DEFAULT NULL COMMENT 'Session id for login',
    `TOKEN`          VARCHAR(512)             DEFAULT NULL COMMENT 'Token',
    `REFRESH_TOKEN`  VARCHAR(512)             DEFAULT NULL COMMENT 'Refresh Token',
    `EXPIRES_AT`     TIMESTAMP                DEFAULT NULL COMMENT 'Token expiration time',
    `TOKEN_METADATA` JSON                     DEFAULT NULL COMMENT 'Token metadata',
    `USERNAME`       VARCHAR(320)             DEFAULT NULL COMMENT 'Username',
    `USER_METADATA`  JSON                     DEFAULT NULL COMMENT 'User metadata',

    `CREATED_AT`     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `CREATED_BY`     BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `UPDATED_BY`     BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT`     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),

    INDEX (STATE),

    UNIQUE KEY UK1_INTEGRATION_TOKEN (`INTEGRATION_ID`, `CREATED_BY`),
    UNIQUE KEY UK2_INTEGRATION_TOKEN_STATE (`STATE`),
    CONSTRAINT `FK1_INTEGRATION_TOKEN_APP_REG_INTEGRATION_ID` FOREIGN KEY (`INTEGRATION_ID`) REFERENCES `security_app_reg_integration` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;


-- adding integration package, role and permissions

SELECT ID
from `security_client`
WHERE CODE = 'SYSTEM'
LIMIT 1
INTO @v_client_system;

INSERT IGNORE INTO `security_permission` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'Integration CREATE', 'Integration create'),
       (@v_client_system, 'Integration READ', 'Integration read'),
       (@v_client_system, 'Integration UPDATE', 'Integration update'),
       (@v_client_system, 'Integration DELETE', 'Integration delete');

INSERT IGNORE INTO `security_role` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'Integration Manager', 'Role to hold Integration operations permissions');

SELECT ID
from `security_role`
WHERE NAME = 'Integration Manager'
LIMIT 1
INTO @v_role_integration;

INSERT IGNORE INTO `security_role_permission` (ROLE_ID, PERMISSION_ID)
VALUES (@v_role_integration, (SELECT ID FROM `security_permission` WHERE NAME = 'Integration CREATE' LIMIT 1)),
       (@v_role_integration, (SELECT ID FROM `security_permission` WHERE NAME = 'Integration READ' LIMIT 1)),
       (@v_role_integration, (SELECT ID FROM `security_permission` WHERE NAME = 'Integration UPDATE' LIMIT 1)),
       (@v_role_integration, (SELECT ID FROM `security_permission` WHERE NAME = 'Integration DELETE' LIMIT 1));

INSERT IGNORE INTO `security_package` (CLIENT_ID, CODE, NAME, DESCRIPTION, BASE)
VALUES (@v_client_system, 'INTEG', 'Integrations Management',
        'Integrations management roles and permissions will be part of this package', FALSE);

SELECT ID
from `security_package`
WHERE CODE = 'INTEG'
LIMIT 1
INTO @v_package_integration;

INSERT IGNORE INTO `security_package_role` (ROLE_ID, PACKAGE_ID)
VALUES (@v_role_integration, @v_package_integration);

-- adding integration package and role to the system client and system user

INSERT IGNORE INTO `security_client_package` (CLIENT_ID, PACKAGE_ID)
VALUES (@v_client_system, @v_package_integration);

SELECT ID
FROM `security`.`security_user`
WHERE USER_NAME = 'sysadmin'
LIMIT 1
INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID)
VALUES (@v_user_sysadmin, @v_role_integration);

-- V5__Cloud File System.sql (Files)
use files;

DROP TABLE IF EXISTS file_system_static;
DROP TABLE IF EXISTS file_system_secured;
DROP TABLE IF EXISTS file_system;
DROP TABLE IF EXISTS files_file_system;

CREATE TABLE files_file_system
(
    `ID`         BIGINT UNSIGNED            NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `TYPE`       ENUM ('STATIC', 'SECURED') NOT NULL DEFAULT 'STATIC' COMMENT 'Type of the file system',
    `CODE`       CHAR(8)                    NOT NULL COMMENT 'Client code',
    `NAME`       VARCHAR(512)               NOT NULL COMMENT 'Name of the file',
    `FILE_TYPE`  ENUM ('FILE', 'DIRECTORY') NOT NULL DEFAULT 'FILE' COMMENT 'Type of the file',
    `SIZE`       BIGINT UNSIGNED                     DEFAULT NULL COMMENT 'Size of the file',
    `PARENT_ID`  BIGINT UNSIGNED                     DEFAULT NULL COMMENT 'Parent ID of the file',
    `CREATED_BY` BIGINT UNSIGNED                     DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP                  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED                     DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT` TIMESTAMP                  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (ID),
    KEY `KEY_FILE_SYSTEM_TYPE_FILE_TYPE_PARENT_ID` (`TYPE`, `FILE_TYPE`, `PARENT_ID`),
    KEY `KEY_FILE_SYSTEM_CODE_TYPE_FILE_TYPE` (`CODE`, `TYPE`, `FILE_TYPE`),
    KEY `KEY_FILE_SYSTEM_CODE_NAME` (`CODE`, `NAME`),
    FOREIGN KEY (`PARENT_ID`) REFERENCES `files_file_system` (`ID`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

-- V26__Otp Create.sql
DROP TABLE IF EXISTS `security`.`security_otp`;

CREATE TABLE `security`.`security_otp`
(
    `ID`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each OTP entry',

    `APP_ID`      BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the application to which this OTP belongs. References security_app table',
    `USER_ID`     BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the user for whom this OTP is generated. References security_user table',

    `PURPOSE`     VARCHAR(255)    NOT NULL COMMENT 'Purpose or reason for the OTP (e.g., authentication, password reset, etc.)',
    `TARGET_TYPE` ENUM ('EMAIL', 'PHONE', 'BOTH') DEFAULT 'EMAIL' NOT NULL COMMENT 'The target medium for the OTP delivery: EMAIL, PHONE, or BOTH',

    `UNIQUE_CODE` CHAR(60)        NOT NULL COMMENT 'The hashed OTP code used for verification',
    `EXPIRES_AT`  TIMESTAMP       NOT NULL COMMENT 'Timestamp indicating when the OTP expires and becomes invalid',
    `IP_ADDRESS`  CHAR(45) COMMENT 'IP address of the user to track OTP generation or use, supports both IPv4 and IPv6',

    `CREATED_BY`  BIGINT UNSIGNED                 DEFAULT NULL,
    `CREATED_AT`  TIMESTAMP       NOT NULL        DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`ID`),
    CONSTRAINT `FK1_OTP_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK2_OTP_USER_ID` FOREIGN KEY (`USER_ID`) REFERENCES `security_user` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,

    INDEX (`EXPIRES_AT`),
    INDEX (`CREATED_AT` DESC),
    INDEX (`APP_ID`, `USER_ID`, `PURPOSE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

-- V27__Client Password Policy Add appcode.sql
ALTER TABLE security.security_client_password_policy
    DROP FOREIGN KEY `FK1_CLIENT_PWD_POL_CLIENT_ID`;

ALTER TABLE `security`.`security_client_password_policy`
    DROP INDEX `UK1_CLIENT_PWD_POL_ID`;

ALTER TABLE `security`.`security_client_password_policy`
    ADD COLUMN `APP_ID`         BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT 'Identifier for the application to which this OTP belongs. References security_app table' AFTER CLIENT_ID,
    ADD COLUMN `USER_LOCK_TIME` BIGINT UNSIGNED NOT NULL DEFAULT 30 COMMENT 'Time in minutes for which user need to be locked it policy violates' AFTER `NO_FAILED_ATTEMPTS`;

ALTER TABLE `security`.`security_client_password_policy`
    ADD CONSTRAINT `UK1_CLIENT_PWD_POL_CLIENT_ID_APP_ID` UNIQUE (`CLIENT_ID`, `APP_ID`),
    ADD CONSTRAINT `FK1_CLIENT_PWD_POL_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security`.`security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT `FK2_CLIENT_PWD_POL_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security`.`security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- V28__Client Otp Policy.sql
DROP TABLE IF EXISTS `security`.`security_client_otp_policy`;

CREATE TABLE `security`.`security_client_otp_policy`
(
    `ID`                 BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each OTP policy entry',
    `CLIENT_ID`          BIGINT UNSIGNED   NOT NULL COMMENT 'Identifier for the client to which this OTP policy belongs. References security_client table',
    `APP_ID`             BIGINT UNSIGNED   NOT NULL COMMENT 'Identifier for the application to which this OTP policy belongs. References security_app table',
    `TARGET_TYPE`        ENUM ('EMAIL', 'PHONE', 'BOTH') DEFAULT 'EMAIL' NOT NULL COMMENT 'The target medium for the OTP delivery: EMAIL, PHONE, or BOTH',
    `IS_CONSTANT`        TINYINT           NOT NULL      DEFAULT 0 COMMENT 'Flag indicating if OTP should be a constant value',
    `CONSTANT`           CHAR(10)          NULL COMMENT 'Value of OTP if IS_CONSTANT is true',
    `IS_NUMERIC`         TINYINT           NOT NULL      DEFAULT 1 COMMENT 'Flag indicating if OTP should contain only numeric characters',
    `IS_ALPHANUMERIC`    TINYINT           NOT NULL      DEFAULT 0 COMMENT 'Flag indicating if OTP should contain alphanumeric characters',
    `LENGTH`             SMALLINT UNSIGNED NOT NULL      DEFAULT 4 COMMENT 'Length of the OTP to be generated',
    `RESEND_SAME_OTP`    TINYINT           NOT NULL      DEFAULT 0 COMMENT 'Flag indication weather to send same OTP in resend request.',
    `NO_RESEND_ATTEMPTS` SMALLINT UNSIGNED NOT NULL      DEFAULT 3 COMMENT 'Maximum number of Resend attempts allowed before User is blocked',
    `EXPIRE_INTERVAL`    BIGINT UNSIGNED   NOT NULL      DEFAULT 5 COMMENT 'Time interval in minutes after which OTP will expire',
    `NO_FAILED_ATTEMPTS` SMALLINT UNSIGNED NOT NULL      DEFAULT 3 COMMENT 'Maximum number of failed attempts allowed before OTP is invalidated',
    `USER_LOCK_TIME`     BIGINT UNSIGNED   NOT NULL      DEFAULT 30 COMMENT 'Time in minutes for which user need to be locked it policy violates',

    `CREATED_BY`         BIGINT UNSIGNED                 DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`         TIMESTAMP         NOT NULL      DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`         BIGINT UNSIGNED                 DEFAULT NULL COMMENT 'ID of the user who last updated this row',
    `UPDATED_AT`         TIMESTAMP         NOT NULL      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is last updated',

    PRIMARY KEY (`ID`),
    CONSTRAINT `UK1_CLIENT_OTP_POL_CLIENT_ID_APP_ID` UNIQUE (`CLIENT_ID`, `APP_ID`),
    CONSTRAINT `FK1_CLIENT_OTP_POL_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK2_CLIENT_OTP_POL_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

-- V29__Client Pin Policy.sql
DROP TABLE IF EXISTS `security`.`security_client_pin_policy`;

CREATE TABLE `security`.`security_client_pin_policy`
(
    `ID`                      BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each PIN policy entry',
    `CLIENT_ID`               BIGINT UNSIGNED   NOT NULL COMMENT 'Identifier for the client to which this PIN policy belongs. References security_client table',
    `APP_ID`                  BIGINT UNSIGNED   NOT NULL COMMENT 'Identifier for the application to which this PIN policy belongs. References security_app table',
    `LENGTH`                  SMALLINT UNSIGNED NOT NULL DEFAULT 4 COMMENT 'Length of the PIN to be generated',
    `RE_LOGIN_AFTER_INTERVAL` BIGINT UNSIGNED   NOT NULL DEFAULT 120 COMMENT 'Time interval in minutes after which re-login is required',
    `EXPIRY_IN_DAYS`          SMALLINT UNSIGNED NOT NULL DEFAULT 30 COMMENT 'Number of days after which the PIN expires',
    `EXPIRY_WARN_IN_DAYS`     SMALLINT UNSIGNED NOT NULL DEFAULT 25 COMMENT 'Number of days before expiry to warn the user',
    `PIN_HISTORY_COUNT`       SMALLINT UNSIGNED NOT NULL DEFAULT 3 COMMENT 'Remember how many pin',
    `NO_FAILED_ATTEMPTS`      SMALLINT UNSIGNED NOT NULL DEFAULT 3 COMMENT 'Maximum number of failed attempts allowed before PIN login is blocked',
    `USER_LOCK_TIME`          BIGINT UNSIGNED   NOT NULL DEFAULT 30 COMMENT 'Time in minutes for which user need to be locked it policy violates',

    `CREATED_BY`              BIGINT UNSIGNED            DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`              TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`              BIGINT UNSIGNED            DEFAULT NULL COMMENT 'ID of the user who last updated this row',
    `UPDATED_AT`              TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is last updated',

    PRIMARY KEY (`ID`),
    CONSTRAINT `UK1_CLIENT_PIN_POL_CLIENT_ID_APP_ID` UNIQUE (`CLIENT_ID`, `APP_ID`),
    CONSTRAINT `FK1_CLIENT_PIN_POL_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK2_CLIENT_PIN_POL_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

-- V30__Client User Add Pin And Otp Fields.sql
ALTER TABLE `security`.`security_user`
    ADD COLUMN `PIN`                   VARCHAR(512) COMMENT 'PIN message digested string' AFTER `PASSWORD_HASHED`,
    ADD COLUMN `PIN_HASHED`            TINYINT  DEFAULT 1 COMMENT 'PIN stored is hashed or not' AFTER `PIN`,
    ADD COLUMN `NO_PIN_FAILED_ATTEMPT` SMALLINT DEFAULT 0 COMMENT 'No of failed attempts for PIN' AFTER `NO_FAILED_ATTEMPT`,
    ADD COLUMN `NO_OTP_RESEND_ATTEMPT` SMALLINT DEFAULT 0 COMMENT 'No of Resend attempts for OTP' AFTER `NO_PIN_FAILED_ATTEMPT`,
    ADD COLUMN `NO_OTP_FAILED_ATTEMPT` SMALLINT DEFAULT 0 COMMENT 'No of failed attempts for OTP' AFTER `NO_OTP_RESEND_ATTEMPT`,
    ADD COLUMN `LOCKED_UNTIL`          TIMESTAMP    NULL COMMENT 'If user is blocked based on STATUS_CODE, until when this will indicate' AFTER `STATUS_CODE`,
    ADD COLUMN `LOCKED_DUE_TO`         VARCHAR(512) NULL COMMENT 'Reason for the user blocking action' AFTER LOCKED_UNTIL;

-- V31__Client Past Pins.sql
DROP TABLE IF EXISTS `security`.`security_past_pins`;

CREATE TABLE IF NOT EXISTS `security`.`security_past_pins`
(
    `ID`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each Past PIN entry',
    `USER_ID`    BIGINT UNSIGNED NOT NULL COMMENT 'User ID',
    `PIN`        VARCHAR(512)             DEFAULT NULL COMMENT 'Pin message digested string',
    `PIN_HASHED` TINYINT                  DEFAULT 1 COMMENT 'Pin stored is hashed or not',
    `CREATED_BY` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (`ID`),
    CONSTRAINT `FK1_PAST_PIN_USER_ID` FOREIGN KEY (`USER_ID`) REFERENCES `security`.`security_user` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

-- V32__Alter Otp Policy Constant Column.sql
ALTER TABLE `security`.security_client_otp_policy
    RENAME COLUMN `CONSTANT` TO `CONSTANT_VALUE`;

-- V33__Alter Otp Add Phone And Email.sql
ALTER TABLE `security`.`security_otp`
    ADD COLUMN `EMAIL_ID`     VARCHAR(320) NULL COMMENT 'Email ID to which otp was sent' AFTER `USER_ID`,
    ADD COLUMN `PHONE_NUMBER` CHAR(32)     NULL COMMENT 'Phone Number to which otp was sent' AFTER `EMAIL_ID`;

ALTER TABLE `security`.`security_otp`
    ADD INDEX (`APP_ID`, `EMAIL_ID`, `PHONE_NUMBER`, `PURPOSE`);

-- V34__Create Client Hierarchy.sql
DROP TABLE IF EXISTS `security`.`security_client_hierarchy`;

CREATE TABLE `security`.`security_client_hierarchy`
(
    `ID`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID`             BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
    `MANAGE_CLIENT_LEVEL_0` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'Client ID that manages CLIENT_ID',
    `MANAGE_CLIENT_LEVEL_1` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'Client ID that manages MANAGE_CLIENT_LEVEL_0',
    `MANAGE_CLIENT_LEVEL_2` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'Client ID that manages MANAGE_CLIENT_LEVEL_1',
    `MANAGE_CLIENT_LEVEL_3` BIGINT UNSIGNED          DEFAULT NULL COMMENT 'Client ID that manages MANAGE_CLIENT_LEVEL_2',
    `CREATED_BY`            BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`            BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who last updated this row',
    `UPDATED_AT`            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is last updated',

    PRIMARY KEY (`ID`),

    UNIQUE KEY `UK1_SECURITY_CLIENT_HIERARCHY` (`CLIENT_ID`),
    CONSTRAINT `FK1_CLIENT_HIERARCHY_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security`.`security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK1_CLIENT_HIERARCHY_LEVEL_0` FOREIGN KEY (`MANAGE_CLIENT_LEVEL_0`) REFERENCES `security`.`security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK1_CLIENT_HIERARCHY_LEVEL_1` FOREIGN KEY (`MANAGE_CLIENT_LEVEL_1`) REFERENCES `security`.`security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK1_CLIENT_HIERARCHY_LEVEL_2` FOREIGN KEY (`MANAGE_CLIENT_LEVEL_2`) REFERENCES `security`.`security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK1_CLIENT_HIERARCHY_LEVEL_3` FOREIGN KEY (`MANAGE_CLIENT_LEVEL_3`) REFERENCES `security`.`security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT

) ENGINE = InnoDB
  DEFAULT CHARSET = `UTF8MB4`
  COLLATE = `UTF8MB4_UNICODE_CI`;

-- Adding SYSTEM to security.security_client_hierarchy
INSERT INTO `security`.`security_client_hierarchy` (`CLIENT_ID`,
                                                    `MANAGE_CLIENT_LEVEL_0`,
                                                    `MANAGE_CLIENT_LEVEL_1`,
                                                    `MANAGE_CLIENT_LEVEL_2`,
                                                    `MANAGE_CLIENT_LEVEL_3`)
    (SELECT `ID` AS `CLIENT_ID`, NULL, NULL, NULL, NULL
     FROM `security`.`security_client`
     WHERE `CODE` = 'SYSTEM');

-- Adding security.security_client_hierarchy from security.security_client_manage
INSERT INTO `security`.`security_client_hierarchy` (`CLIENT_ID`,
                                                    `MANAGE_CLIENT_LEVEL_0`,
                                                    `MANAGE_CLIENT_LEVEL_1`,
                                                    `MANAGE_CLIENT_LEVEL_2`,
                                                    `MANAGE_CLIENT_LEVEL_3`)
    (SELECT `scm1`.`CLIENT_ID`        AS `CLIENT_ID`,
            `scm1`.`MANAGE_CLIENT_ID` AS `LEVEL_0`,
            `scm2`.`MANAGE_CLIENT_ID` AS `LEVEL_1`,
            `scm3`.`MANAGE_CLIENT_ID` AS `LEVEL_2`,
            `scm4`.`MANAGE_CLIENT_ID` AS `LEVEL_3`
     FROM `security`.`security_client_manage` `scm1`
              LEFT JOIN `security`.`security_client_manage` `scm2`
                        ON `scm1`.`MANAGE_CLIENT_ID` = `scm2`.`CLIENT_ID`
              LEFT JOIN `security`.`security_client_manage` `scm3`
                        ON `scm2`.`MANAGE_CLIENT_ID` = `scm3`.`CLIENT_ID`
              LEFT JOIN `security`.`security_client_manage` `scm4`
                        ON `scm3`.`MANAGE_CLIENT_ID` = `scm4`.`CLIENT_ID`
     ORDER BY `scm1`.`CLIENT_ID`);

-- V6__Upload Download
use files;

DROP TABLE IF EXISTS file_upload_download;
DROP TABLE IF EXISTS files_upload_download;

CREATE TABLE files_upload_download
(
    `ID`            BIGINT UNSIGNED                   NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `TYPE`          ENUM ('UPLOAD', 'DOWNLOAD')       NOT NULL COMMENT 'Type of the ZIP',
    `RESOURCE_TYPE` ENUM ('STATIC', 'SECURED')        NOT NULL COMMENT 'Resource type',
    `CLIENT_CODE`   char(8)                           NOT NULL COMMENT 'Client Code to whom the folder belongs to',
    `PATH`          VARCHAR(1024)                     NOT NULL COMMENT 'Path of the folder',
    `CDN_URL`       VARCHAR(1024)                     NOT NULL COMMENT 'URL in the CDN',
    `STATUS`        ENUM ('PENDING', 'DONE', 'ERROR') NOT NULL DEFAULT 'PENDING' COMMENT 'Status of the process',
    `EXCEPTION`     text                                       DEFAULT NULL COMMENT 'Exception message if any',
    `CREATED_BY`    BIGINT UNSIGNED                            DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`    TIMESTAMP                         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`    BIGINT UNSIGNED                            DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT`    TIMESTAMP                         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

-- V35__Alter Security Otp UserId Null

ALTER TABLE `security`.`security_otp`
    MODIFY COLUMN `USER_ID` BIGINT UNSIGNED NULL COMMENT 'Identifier for the user for whom this OTP is generated. References security_user table';

-- V36__Alter Security Otp Updatable Fields

ALTER TABLE `security`.`security_otp`
    ADD COLUMN `VERIFY_LEGS_COUNTS` SMALLINT        DEFAULT 0 NOT NULL COMMENT 'Number of legs in otp verification. If 0 this otp will be completely verified and deleted' AFTER `IP_ADDRESS`,
    ADD COLUMN `UPDATED_BY`         BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who last updated this row' AFTER `CREATED_AT`,
    ADD COLUMN `UPDATED_AT`         TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is last updated' AFTER `UPDATED_BY`;

-- V37__Dropping unnecessary table

use security;
drop table if exists security_client_manage;
drop table if exists security_client_manage_hierarcy;
drop table if exists security_code_access;

-- V38__Security Profiles.sql

use security;

-- Dropping all the tables that are replaced with new tables
DROP TABLE IF EXISTS `security_app_reg_user_profile`;
DROP TABLE IF EXISTS `security_app_reg_user_designation`;
DROP TABLE IF EXISTS `security_app_reg_user_role_v2`;
DROP TABLE IF EXISTS `security_app_reg_profile`;
DROP TABLE IF EXISTS `security_app_reg_profile_restriction`;
DROP TABLE IF EXISTS `security_profile_client_restriction`;
DROP TABLE IF EXISTS `security_app_reg_designation`;
DROP TABLE IF EXISTS `security_app_reg_department`;
DROP TABLE IF EXISTS `security_profile_arrangement`;
DROP TABLE IF EXISTS `security_v2_user_role`;
DROP TABLE IF EXISTS `security_v2_role_permission`;
DROP TABLE IF EXISTS `security_v2_role_role`;
DROP TABLE IF EXISTS `security_profile_user`;
DROP TABLE IF EXISTS `security_profile_role`;
DROP TABLE IF EXISTS `security_client_profile`;
DROP TABLE IF EXISTS `security_v2_role`;

-- These are required in re runs
-- ALTER TABLE `security_user`
--   DROP CONSTRAINT `FK1_USER_DESIGNATION_ID`,
--   DROP CONSTRAINT `FK2_USER_REPORTING_TO_ID`;
--
-- ALTER TABLE `security_user`
--   DROP COLUMN `DESIGNATION_ID`,
--   DROP COLUMN `REPORTING_TO`;

DROP TABLE IF EXISTS `security_designation`;
DROP TABLE IF EXISTS `security_profile`;
DROP TABLE IF EXISTS `security_department`;

CREATE TABLE `security_profile`
(
    `ID`              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID`       bigint unsigned NOT NULL COMMENT 'Client ID for which this profile belongs to',
    `NAME`            varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Name of the profile',
    `APP_ID`          bigint unsigned NOT NULL,
    `DESCRIPTION`     text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         DEFAULT NULL COMMENT 'Description of the profile',
    `ROOT_PROFILE_ID` bigint unsigned                                               DEFAULT NULL COMMENT 'Profile ID to which the user is assigned',
    `ARRANGEMENT`     json                                                          DEFAULT NULL COMMENT 'Arrangement of the profile',

    `CREATED_BY`      bigint unsigned                                               DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`      timestamp       NOT NULL                                      DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`      bigint unsigned                                               DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT`      timestamp       NOT NULL                                      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PROFILE_NAME_APP_ID` (`NAME`, `APP_ID`),
    KEY `FK1_PROFILE_CLIENT_ID` (`CLIENT_ID`),
    KEY `FK2_PROFILE_APP_ID` (`APP_ID`),
    CONSTRAINT `FK1_PROFILE_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK2_PROFILE_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK3_PROFILE_ROOT_PROFILE_ID` FOREIGN KEY (`ROOT_PROFILE_ID`) REFERENCES `security_profile` (`ID`) ON DELETE RESTRICT ON UPDATE CASCADE
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;

-- Security V2 Role Table

CREATE TABLE `security_v2_role`
(
    `ID`          bigint unsigned                                               NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID`   bigint unsigned                                               NOT NULL COMMENT 'Client ID for which this role belongs to',
    `NAME`        varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Name of the role',
    `SHORT_NAME`  varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT 'Short name of the role',
    `DESCRIPTION` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Description of the role',
    `APP_ID`      bigint unsigned                                                        DEFAULT NULL COMMENT 'App ID for which this role belongs to',

    `CREATED_BY`  bigint unsigned                                                        DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`  timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`  bigint unsigned                                                        DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT`  timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (`ID`),
    KEY `FK1_V2_ROLE_CLIENT_ID` (`CLIENT_ID`),
    KEY `FK2_V2_ROLE_APP_ID` (`APP_ID`),
    CONSTRAINT `FK1_V2_ROLE_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK2_V2_ROLE_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Security Profile Role Table

CREATE TABLE `security_profile_role`
(
    `ID`         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `PROFILE_ID` bigint unsigned NOT NULL COMMENT 'Profile ID for which this role belongs to',
    `ROLE_ID`    bigint unsigned NOT NULL COMMENT 'Role ID for which this role belongs to',
    `EXCLUDE`    tinyint(1)      NOT NULL DEFAULT 0 COMMENT 'Flag to indicate if this role is excluded',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PROFILE_ROLE_APP_ID` (`PROFILE_ID`, `ROLE_ID`),
    KEY `FK1_PROFILE_ROLE_PROFILE_ID` (`PROFILE_ID`),
    KEY `FK2_PROFILE_ROLE_ROLE_ID` (`ROLE_ID`),
    CONSTRAINT `FK1_PROFILE_ROLE_PROFILE_ID` FOREIGN KEY (`PROFILE_ID`) REFERENCES `security_profile` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT `FK2_PROFILE_ROLE_ROLE_ID` FOREIGN KEY (`ROLE_ID`) REFERENCES `security_v2_role` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Security Profile Client restriction Table

CREATE TABLE `security_profile_client_restriction`
(
    `ID`         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `PROFILE_ID` bigint unsigned NOT NULL COMMENT 'Profile ID for which this restriction belongs to',
    `CLIENT_ID`  bigint unsigned NOT NULL COMMENT 'Client ID for which this restriction belongs to',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PROFILE_CLIENT_APP_ID` (`PROFILE_ID`, `CLIENT_ID`),
    KEY `FK1_PROFILE_CLIENT_RESTRICTION_PROFILE_ID` (`PROFILE_ID`),
    CONSTRAINT `FK1_PROFILE_CLIENT_RESTRICTION_PROFILE_ID` FOREIGN KEY (`PROFILE_ID`) REFERENCES `security_profile` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT `FK3_PROFILE_CLIENT_RESTRICTION_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;
-- Security Profile User Table

CREATE TABLE `security_profile_user`
(
    `ID`         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `PROFILE_ID` bigint unsigned NOT NULL COMMENT 'Profile ID for which this user belongs to',
    `USER_ID`    bigint unsigned NOT NULL COMMENT 'User ID for which this profile belongs to',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PROFILE_USER_PROFILE_ID_USER_ID` (`PROFILE_ID`, `USER_ID`),
    KEY `FK1_PROFILE_USER_PROFILE_ID` (`PROFILE_ID`),
    KEY `FK2_PROFILE_USER_USER_ID` (`USER_ID`),
    CONSTRAINT `FK1_PROFILE_USER_PROFILE_ID` FOREIGN KEY (`PROFILE_ID`) REFERENCES `security_profile` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT `FK2_PROFILE_USER_USER_ID` FOREIGN KEY (`USER_ID`) REFERENCES `security_user` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Security Role Role Table

CREATE TABLE `security_v2_role_role`
(
    `ID`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `ROLE_ID`     bigint unsigned NOT NULL COMMENT 'Role ID in which the sub role is nested',
    `SUB_ROLE_ID` bigint unsigned NOT NULL COMMENT 'Sub Role ID for which this role belongs to',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_ROLE_ROLE_ROLE_ID_SUB_ROLE_ID` (`ROLE_ID`, `SUB_ROLE_ID`),
    KEY `FK1_ROLE_ROLE_ROLE_ID` (`ROLE_ID`),
    KEY `FK2_ROLE_ROLE_SUB_ROLE_ID` (`SUB_ROLE_ID`),
    CONSTRAINT `FK1_ROLE_ROLE_ROLE_ID` FOREIGN KEY (`ROLE_ID`) REFERENCES `security_v2_role` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT `FK2_ROLE_ROLE_SUB_ROLE_ID` FOREIGN KEY (`SUB_ROLE_ID`) REFERENCES `security_v2_role` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Security Role Permission Table

CREATE TABLE `security_v2_role_permission`
(
    `ID`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `ROLE_ID`       bigint unsigned NOT NULL COMMENT 'Role ID for the permissions contained',
    `PERMISSION_ID` bigint unsigned NOT NULL COMMENT 'Permission ID for which this role has permission',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_ROLE_PERMISSION_ROLE_ID_PERMISSION_ID` (`ROLE_ID`, `PERMISSION_ID`),
    KEY `FK1_ROLE_PERMISSION_ROLE_ID` (`ROLE_ID`),
    KEY `FK2_ROLE_PERMISSION_PERMISSION_ID` (`PERMISSION_ID`),
    CONSTRAINT `FK1_ROLE_PERMISSION_ROLE_ID` FOREIGN KEY (`ROLE_ID`) REFERENCES `security_v2_role` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT `FK2_ROLE_PERMISSION_PERMISSION_ID` FOREIGN KEY (`PERMISSION_ID`) REFERENCES `security_permission` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Security V2 User Role Table

CREATE TABLE `security_v2_user_role`
(
    `ID`      bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `USER_ID` bigint unsigned NOT NULL COMMENT 'User ID to which this role is assigned',
    `ROLE_ID` bigint unsigned NOT NULL COMMENT 'Role ID assigned to the user',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_USER_ROLE_USER_ID_ROLE_ID` (`USER_ID`, `ROLE_ID`),
    KEY `FK1_USER_ROLE_V2_USER_ID` (`USER_ID`),
    KEY `FK2_USER_ROLE_V2_ROLE_ID` (`ROLE_ID`),
    CONSTRAINT `FK1_USER_ROLE_V2_USER_ID` FOREIGN KEY (`USER_ID`) REFERENCES `security_user` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT `FK2_USER_ROLE_V2_ROLE_ID` FOREIGN KEY (`ROLE_ID`) REFERENCES `security_v2_role` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Security Department Table
-- Security Department Table with Parent Department Reference
CREATE TABLE `security_department`
(
    `ID`                   bigint unsigned                                               NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID`            bigint unsigned                                               NOT NULL COMMENT 'Client ID for which this department belongs to',
    `NAME`                 varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Name of the department',
    `DESCRIPTION`          text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Description of the department',
    `PARENT_DEPARTMENT_ID` bigint unsigned                                                        DEFAULT NULL COMMENT 'Parent department for hierarchical structure',

    `CREATED_BY`           bigint unsigned                                                        DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`           timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`           bigint unsigned                                                        DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT`           timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    KEY `FK1_DEPARTMENT_CLIENT_ID` (`CLIENT_ID`),
    KEY `FK2_DEPARTMENT_PARENT_ID` (`PARENT_DEPARTMENT_ID`),

    CONSTRAINT `FK1_DEPARTMENT_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`)
        REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK2_DEPARTMENT_PARENT_ID` FOREIGN KEY (`PARENT_DEPARTMENT_ID`)
        REFERENCES `security_department` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Security Designation Table with Reporting & Hierarchy
CREATE TABLE `security_designation`
(
    `ID`                    bigint unsigned                                               NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID`             bigint unsigned                                               NOT NULL COMMENT 'Client ID for which this designation belongs to',
    `NAME`                  varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Name of the designation',
    `DESCRIPTION`           text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Description of the designation',
    `DEPARTMENT_ID`         bigint unsigned                                                        DEFAULT NULL COMMENT 'Department ID for which this designation belongs to',
    `PARENT_DESIGNATION_ID` bigint unsigned                                                        DEFAULT NULL COMMENT 'Parent designation for hierarchy',
    `NEXT_DESIGNATION_ID`   bigint unsigned                                                        DEFAULT NULL COMMENT 'Next designation in the hierarchy',
    `PROFILE_ID`            bigint unsigned                                                        DEFAULT NULL COMMENT 'Profile ID for which this designation belongs to',

    `CREATED_BY`            bigint unsigned                                                        DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`            timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`            bigint unsigned                                                        DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `UPDATED_AT`            timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',

    PRIMARY KEY (`ID`),
    KEY `FK1_DESIGNATION_CLIENT_ID` (`CLIENT_ID`),
    KEY `FK2_DESIGNATION_DEPARTMENT_ID` (`DEPARTMENT_ID`),
    KEY `FK3_DESIGNATION_PARENT_ID` (`PARENT_DESIGNATION_ID`),

    CONSTRAINT `FK1_DESIGNATION_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`)
        REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK2_DESIGNATION_DEPARTMENT_ID` FOREIGN KEY (`DEPARTMENT_ID`)
        REFERENCES `security_department` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `FK3_DESIGNATION_PARENT_ID` FOREIGN KEY (`PARENT_DESIGNATION_ID`)
        REFERENCES `security_designation` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `FK4_DESIGNATION_NEXT_DESIGNATION_ID` FOREIGN KEY (`NEXT_DESIGNATION_ID`)
        REFERENCES `security_designation` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `FK5_DESIGNATION_PROFILE_ID` FOREIGN KEY (`PROFILE_ID`)
        REFERENCES `security_profile` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
-- Adding designation and reporting to user in security_user table along with foreign key constraints

ALTER TABLE `security_user`
    ADD COLUMN `DESIGNATION_ID` bigint unsigned DEFAULT NULL COMMENT 'Designation ID for which this user belongs to' AFTER `ID`,
    ADD COLUMN `REPORTING_TO`   bigint unsigned DEFAULT NULL COMMENT 'Reporting to ID for which this user belongs to' AFTER `DESIGNATION_ID`;

ALTER TABLE `security_user`
    ADD CONSTRAINT `FK1_USER_DESIGNATION_ID` FOREIGN KEY (`DESIGNATION_ID`) REFERENCES `security_designation` (`ID`) ON DELETE SET NULL ON UPDATE RESTRICT,
    ADD CONSTRAINT `FK2_USER_REPORTING_TO_ID` FOREIGN KEY (`REPORTING_TO`) REFERENCES `security_user` (`ID`) ON DELETE SET NULL ON UPDATE RESTRICT;

-- App registration related tables

CREATE TABLE `security_app_reg_profile_restriction`
(
    `ID`            bigint unsigned                                                                        NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID`     bigint unsigned                                                                        NOT NULL COMMENT 'Client ID',
    `CLIENT_TYPE`   char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                               NOT NULL DEFAULT 'BUS' COMMENT 'Client type',
    `APP_ID`        bigint unsigned                                                                        NOT NULL COMMENT 'App ID',
    `LEVEL`         enum ('CLIENT','CUSTOMER','CONSUMER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CLIENT' COMMENT 'Access level',
    `BUSINESS_TYPE` char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                              NOT NULL DEFAULT 'COMMON' COMMENT 'Business type',

    `PROFILE_ID`    bigint unsigned                                                                        NOT NULL COMMENT 'Profile ID',

    `CREATED_BY`    bigint unsigned                                                                                 DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`    timestamp                                                                              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `CLIENT_ID` (`CLIENT_ID`, `APP_ID`, `PROFILE_ID`, `CLIENT_TYPE`, `LEVEL`, `BUSINESS_TYPE`),
    KEY `FK2_APP_REG_PROFILE_APP_ID` (`APP_ID`),
    KEY `FK3_APP_REG_PROFILE_PROFILE_ID` (`PROFILE_ID`),
    KEY `FK4_APP_REG_PROFILE_CLIENT_TYPE` (`CLIENT_TYPE`),
    CONSTRAINT `FK1_APP_REG_PROFILE_CLNT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_PROFILE_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK3_APP_REG_PROFILE_PROFILE_ID` FOREIGN KEY (`PROFILE_ID`) REFERENCES `security_profile` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK4_APP_REG_PROFILE_CLIENT_TYPE` FOREIGN KEY (`CLIENT_TYPE`) REFERENCES `security_client_type` (`CODE`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `security_app_reg_department`
(
    `ID`                   bigint unsigned                                                                        NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID`            bigint unsigned                                                                        NOT NULL COMMENT 'Client ID',
    `CLIENT_TYPE`          char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                               NOT NULL DEFAULT 'BUS' COMMENT 'Client type',
    `APP_ID`               bigint unsigned                                                                        NOT NULL COMMENT 'App ID',
    `LEVEL`                enum ('CLIENT','CUSTOMER','CONSUMER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CLIENT' COMMENT 'Access level',
    `BUSINESS_TYPE`        char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                              NOT NULL DEFAULT 'COMMON' COMMENT 'Business type',

    `NAME`                 varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                          NOT NULL COMMENT 'Name of the department',
    `DESCRIPTION`          text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Description of the department',
    `PARENT_DEPARTMENT_ID` bigint unsigned                                                                                 DEFAULT NULL COMMENT 'Parent department for hierarchical structure in this registration details',

    `CREATED_BY`           bigint unsigned                                                                                 DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`           timestamp                                                                              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (`ID`),
    KEY `FK1_APP_REG_DEPARTMENT_CLIENT_ID` (`CLIENT_ID`),
    KEY `FK2_APP_REG_DEPARTMENT_PARENT_ID` (`PARENT_DEPARTMENT_ID`),
    KEY `FK3_APP_REG_DEPARTMENT_APP_ID` (`APP_ID`),
    CONSTRAINT `FK1_APP_REG_DEPARTMENT_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_DEPARTMENT_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_DEPARTMENT_PARENT_ID` FOREIGN KEY (`PARENT_DEPARTMENT_ID`) REFERENCES `security_app_reg_department` (`ID`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- Security App Reg User RoleV2 Table

CREATE TABLE `security_app_reg_user_role_v2`
(
    `ID`            bigint unsigned                                                                        NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID`     bigint unsigned                                                                        NOT NULL COMMENT 'Client ID',
    `CLIENT_TYPE`   char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                               NOT NULL DEFAULT 'BUS' COMMENT 'Client type',
    `APP_ID`        bigint unsigned                                                                        NOT NULL COMMENT 'App ID',
    `LEVEL`         enum ('CLIENT','CUSTOMER','CONSUMER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CLIENT' COMMENT 'Access level',
    `BUSINESS_TYPE` char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                              NOT NULL DEFAULT 'COMMON' COMMENT 'Business type',

    `ROLE_ID`       bigint unsigned                                                                        NOT NULL COMMENT 'Role ID',

    `CREATED_BY`    bigint unsigned                                                                                 DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`    timestamp                                                                              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (`ID`),
    KEY `FK1_APP_REG_USER_ROLE_CLIENT_ID` (`CLIENT_ID`),
    KEY `FK2_APP_REG_USER_ROLE_ROLE_V2_ID` (`ROLE_ID`),
    KEY `FK3_APP_REG_USER_ROLE_APP_ID` (`APP_ID`),
    CONSTRAINT `FK1_APP_REG_USER_ROLE_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_USER_ROLE_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_USER_ROLE_ROLE_V2_ID` FOREIGN KEY (`ROLE_ID`) REFERENCES `security_v2_role` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `security_app_reg_designation`
(
    `ID`                    bigint unsigned                                                                        NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID`             bigint unsigned                                                                        NOT NULL COMMENT 'Client ID',
    `CLIENT_TYPE`           char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                               NOT NULL DEFAULT 'BUS' COMMENT 'Client type',
    `APP_ID`                bigint unsigned                                                                        NOT NULL COMMENT 'App ID',
    `LEVEL`                 enum ('CLIENT','CUSTOMER','CONSUMER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CLIENT' COMMENT 'Access level',
    `BUSINESS_TYPE`         char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                              NOT NULL DEFAULT 'COMMON' COMMENT 'Business type',

    `NAME`                  varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                          NOT NULL COMMENT 'Name of the designation',
    `DESCRIPTION`           text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Description of the designation',
    `DEPARTMENT_ID`         bigint unsigned                                                                        NOT NULL COMMENT 'Department ID for which this designation belongs to',
    `PARENT_DESIGNATION_ID` bigint unsigned                                                                                 DEFAULT NULL COMMENT 'Parent designation for hierarchy',
    `NEXT_DESIGNATION_ID`   bigint unsigned                                                                                 DEFAULT NULL COMMENT 'Next designation in the hierarchy',

    `CREATED_BY`            bigint unsigned                                                                                 DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`            timestamp                                                                              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (`ID`),
    KEY `FK1_APP_REG_DESIGNATION_CLIENT_ID` (`CLIENT_ID`),
    KEY `FK2_APP_REG_DESIGNATION_DEPARTMENT_ID` (`DEPARTMENT_ID`),
    KEY `FK3_APP_REG_DESIGNATION_PARENT_ID` (`PARENT_DESIGNATION_ID`),
    KEY `FK4_APP_REG_DESIGNATION_APP_ID` (`APP_ID`),
    CONSTRAINT `FK1_APP_REG_DESIGNATION_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_DESIGNATION_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_DESIGNATION_DEPARTMENT_ID` FOREIGN KEY (`DEPARTMENT_ID`) REFERENCES `security_app_reg_department` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK3_APP_REG_DESIGNATION_PARENT_ID` FOREIGN KEY (`PARENT_DESIGNATION_ID`) REFERENCES `security_app_reg_designation` (`ID`) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `FK4_APP_REG_DESIGNATION_NEXT_DESIGNATION_ID` FOREIGN KEY (`NEXT_DESIGNATION_ID`) REFERENCES `security_app_reg_designation` (`ID`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


CREATE TABLE `security_app_reg_user_profile`
(
    `ID`            bigint unsigned                                                                        NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID`     bigint unsigned                                                                        NOT NULL COMMENT 'Client ID',
    `CLIENT_TYPE`   char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                               NOT NULL DEFAULT 'BUS' COMMENT 'Client type',
    `APP_ID`        bigint unsigned                                                                        NOT NULL COMMENT 'App ID',
    `LEVEL`         enum ('CLIENT','CUSTOMER','CONSUMER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CLIENT' COMMENT 'Access level',
    `BUSINESS_TYPE` char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                              NOT NULL DEFAULT 'COMMON' COMMENT 'Business type',

    `PROFILE_ID`    bigint unsigned                                                                        NOT NULL COMMENT 'Profile ID',

    `CREATED_BY`    bigint unsigned                                                                                 DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`    timestamp                                                                              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `CLIENT_ID` (`CLIENT_ID`, `APP_ID`, `PROFILE_ID`, `CLIENT_TYPE`, `LEVEL`, `BUSINESS_TYPE`),
    KEY `FK2_APP_REG_USER_PROFILE_APP_ID` (`APP_ID`),
    KEY `FK3_APP_REG_USER_PROFILE_PROFILE_ID` (`PROFILE_ID`),
    KEY `FK4_APP_REG_USER_PROFILE_CLIENT_TYPE` (`CLIENT_TYPE`),
    CONSTRAINT `FK1_APP_REG_USER_PROFILE_CLNT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_USER_PROFILE_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK3_APP_REG_USER_PROFILE_PROFILE_ID` FOREIGN KEY (`PROFILE_ID`) REFERENCES `security_profile` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK4_APP_REG_USER_PROFILE_CLIENT_TYPE` FOREIGN KEY (`CLIENT_TYPE`) REFERENCES `security_client_type` (`CODE`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `security_app_reg_user_designation`
(
    `ID`             bigint unsigned                                                                        NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID`      bigint unsigned                                                                        NOT NULL COMMENT 'Client ID',
    `CLIENT_TYPE`    char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                               NOT NULL DEFAULT 'BUS' COMMENT 'Client type',
    `APP_ID`         bigint unsigned                                                                        NOT NULL COMMENT 'App ID',
    `LEVEL`          enum ('CLIENT','CUSTOMER','CONSUMER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CLIENT' COMMENT 'Access level',
    `BUSINESS_TYPE`  char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                              NOT NULL DEFAULT 'COMMON' COMMENT 'Business type',

    `DESIGNATION_ID` bigint unsigned                                                                        NOT NULL COMMENT 'Designation ID',

    `CREATED_BY`     bigint unsigned                                                                                 DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`     timestamp                                                                              NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (`ID`),
    KEY `FK1_APP_REG_USER_DESIGNATION_CLIENT_ID` (`CLIENT_ID`),
    KEY `FK2_APP_REG_USER_DESIGNATION_DESIGNATION_ID` (`DESIGNATION_ID`),
    KEY `FK3_APP_REG_USER_DESIGNATION_APP_ID` (`APP_ID`),
    CONSTRAINT `FK1_APP_REG_USER_DESIGNATION_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_USER_DESIGNATION_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_APP_REG_USER_DESIGNATION_DESIGNATION_ID` FOREIGN KEY (`DESIGNATION_ID`) REFERENCES `security_app_reg_designation` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Adding profile related permissions and roles

SELECT ID
from `security_client`
WHERE CODE = 'SYSTEM'
LIMIT 1
INTO @v_client_system;

-- DELETING any profile related permissions;

DELETE
FROM security.security_permission
where NAME like 'Profile%';

-- Inserting old roles into v2 role table

INSERT INTO `security_v2_role` (`CLIENT_ID`, `NAME`, `APP_ID`, `DESCRIPTION`)
SELECT `CLIENT_ID`, `NAME`, `APP_ID`, `DESCRIPTION`
FROM `security_role`;

-- Inserting old permissions into v2 role table to create a new role for each permission

INSERT INTO `security_v2_role` (`CLIENT_ID`, `NAME`, `SHORT_NAME`, `DESCRIPTION`)
SELECT CLIENT_ID,
       NAME,
       concat(left(short_name, 1), lower(substr(short_name, 2))) as SHORT_NAME,
       DESCRIPTION
FROM (SELECT CLIENT_ID,
             NAME,
             IF(SUBSTRING_INDEX(`NAME`, ' ', 1) = 'ASSIGN', 'ASSIGN', SUBSTRING_INDEX(`NAME`, ' ', - 1)) AS short_name,
             DESCRIPTION
      FROM security.security_permission) nt;

-- Creating the relation between newly created permission roles with existing permissions

INSERT INTO `security_v2_role_permission` (ROLE_ID, PERMISSION_ID)
select r.id role_id, p.id permission_id
from security_v2_role r
         left join security_permission p on r.name = p.name
where p.id is not null;

INSERT INTO `security_v2_role_role` (ROLE_ID, SUB_ROLE_ID)
select vr.id, vr2.id
from security_role_permission rp
         left join security_role r on r.id = rp.role_id
         left join security_v2_role vr on vr.name = r.name
         left join security_permission p on p.id = rp.PERMISSION_ID
         left join security_v2_role vr2 on vr2.name = p.name;

INSERT IGNORE INTO `security_permission` (CLIENT_ID, NAME, DESCRIPTION)
VALUES (@v_client_system, 'Profile CREATE', 'Profile create'),
       (@v_client_system, 'Profile READ', 'Profile read'),
       (@v_client_system, 'Profile UPDATE', 'Profile update'),
       (@v_client_system, 'Profile DELETE', 'Profile delete');

INSERT IGNORE INTO `security_v2_role` (CLIENT_ID, NAME, SHORT_NAME, DESCRIPTION)
VALUES (@v_client_system, 'Profile CREATE', 'Create', 'Profile create'),
       (@v_client_system, 'Profile READ', 'Read', 'Profile read'),
       (@v_client_system, 'Profile UPDATE', 'Update', 'Profile update'),
       (@v_client_system, 'Profile DELETE', 'Delete', 'Profile delete'),
       (@v_client_system, 'Profile Manager', 'Manager', 'Profile manager'),
       (@v_client_system, 'Owner', 'Owner', 'Owner');

SELECT ID
from `security_v2_role`
WHERE NAME = 'Profile Manager'
LIMIT 1
INTO @v_role_profile_manager;

SELECT ID
from `security_v2_role`
WHERE NAME = 'Profile CREATE'
LIMIT 1
INTO @v_role_profile_create;
SELECT ID
from `security_v2_role`
WHERE NAME = 'Profile READ'
LIMIT 1
INTO @v_role_profile_read;
SELECT ID
from `security_v2_role`
WHERE NAME = 'Profile UPDATE'
LIMIT 1
INTO @v_role_profile_update;
SELECT ID
from `security_v2_role`
WHERE NAME = 'Profile DELETE'
LIMIT 1
INTO @v_role_profile_delete;

INSERT IGNORE INTO `security_v2_role_permission` (ROLE_ID, PERMISSION_ID)
VALUES (@v_role_profile_create, (SELECT ID FROM `security_permission` WHERE NAME = 'Profile CREATE' LIMIT 1)),
       (@v_role_profile_read, (SELECT ID FROM `security_permission` WHERE NAME = 'Profile READ' LIMIT 1)),
       (@v_role_profile_update, (SELECT ID FROM `security_permission` WHERE NAME = 'Profile UPDATE' LIMIT 1)),
       (@v_role_profile_delete, (SELECT ID FROM `security_permission` WHERE NAME = 'Profile DELETE' LIMIT 1));

INSERT IGNORE INTO `security_v2_role_role` (ROLE_ID, SUB_ROLE_ID)
VALUES (@v_role_profile_manager, @v_role_profile_create),
       (@v_role_profile_manager, @v_role_profile_read),
       (@v_role_profile_manager, @v_role_profile_update),
       (@v_role_profile_manager, @v_role_profile_delete);

ALTER TABLE `security`.`security_sox_log`
    CHANGE COLUMN `OBJECT_NAME` `OBJECT_NAME` ENUM ('USER', 'ROLE', 'PERMISSION', 'PACKAGE', 'CLIENT', 'CLIENT_TYPE', 'APP', 'PROFILE') CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_unicode_ci' NOT NULL COMMENT 'Operation on the object';

SET SQL_SAFE_UPDATES = 0;
DELETE
FROM `security`.`security_v2_role`
where short_name = 'Assign';
SET SQL_SAFE_UPDATES = 1;

select id
from security.security_app
where app_code = 'appbuilder'
into @v_app_appbuilder;
select id
from security.security_client
where code = 'system'
into @v_client_system;

delete
from security.security_profile
where app_id = @v_app_appbuilder;

select id
from security.security_v2_role
where name = 'Actions Manager'
into @v_role_actions_manager;
select id
from security.security_v2_role
where name = 'Application Manager'
into @v_role_app_manager;
select id
from security.security_v2_role
where name = 'Client Manager'
into @v_role_client_manager;
select id
from security.security_v2_role
where name = 'Client Type Manager'
into @v_role_client_type_manager;
select id
from security.security_v2_role
where name = 'Client Update Manager'
into @v_role_client_update_manager;
select id
from security.security_v2_role
where name = 'Data Connection Manager'
into @v_role_data_connection_manager;
select id
from security.security_v2_role
where name = 'Data Manager'
into @v_role_data_manager;
select id
from security.security_v2_role
where name = 'EventAction Manager'
into @v_role_eventaction_manager;
select id
from security.security_v2_role
where name = 'EventDefinition Manager'
into @v_role_eventdefinition_manager;
select id
from security.security_v2_role
where name = 'Files Manager'
into @v_role_files_manager;
select id
from security.security_v2_role
where name = 'Function Manager'
into @v_role_function_manager;
select id
from security.security_v2_role
where name = 'Integration Manager'
into @v_role_integration_manager;
select id
from security.security_v2_role
where name = 'Owner'
into @v_role_owner;
select id
from security.security_v2_role
where name = 'Package Manager'
into @v_role_package_manager;
select id
from security.security_v2_role
where name = 'Page Manager'
into @v_role_page_manager;
select id
from security.security_v2_role
where name = 'Permission Manager'
into @v_role_permission_manager;
select id
from security.security_v2_role
where name = 'Personalization Manager'
into @v_role_personalization_manager;
select id
from security.security_v2_role
where name = 'Profile Manager'
into @v_role_profile_manager;
select id
from security.security_v2_role
where name = 'Role Manager'
into @v_role_role_manager;
select id
from security.security_v2_role
where name = 'Schema Manager'
into @v_role_schema_manager;
select id
from security.security_v2_role
where name = 'Style Manager'
into @v_role_style_manager;
select id
from security.security_v2_role
where name = 'System Application Manager'
into @v_role_system_application_manager;
select id
from security.security_v2_role
where name = 'Template Manager'
into @v_role_template_manager;
select id
from security.security_v2_role
where name = 'Theme Manager'
into @v_role_theme_manager;
select id
from security.security_v2_role
where name = 'Transport Manager'
into @v_role_transport_manager;
select id
from security.security_v2_role
where name = 'User Manager'
into @v_role_user_manager;
select id
from security.security_v2_role
where name = 'Workflow Manager'
into @v_role_workflow_manager;

insert into security.security_profile(client_id, app_id, name, description, arrangement) value
    (@v_client_system, @v_app_appbuilder, 'Appbuilder Owner', 'Owner of App Builder',
     JSON_OBJECT(
             "r1", JSON_OBJECT("roleId", @v_role_actions_manager),
             "r2", JSON_OBJECT("roleId", @v_role_app_manager),
             "r3", JSON_OBJECT("roleId", @v_role_client_manager),
             "r4", JSON_OBJECT("roleId", @v_role_client_type_manager),
             "r5", JSON_OBJECT("roleId", @v_role_client_update_manager),
             "r6", JSON_OBJECT("roleId", @v_role_data_connection_manager),
             "r7", JSON_OBJECT("roleId", @v_role_data_manager),
             "r8", JSON_OBJECT("roleId", @v_role_eventaction_manager),
             "r9", JSON_OBJECT("roleId", @v_role_eventdefinition_manager),
             "r10", JSON_OBJECT("roleId", @v_role_files_manager),
             "r11", JSON_OBJECT("roleId", @v_role_function_manager),
             "r12", JSON_OBJECT("roleId", @v_role_integration_manager),
             "r13", JSON_OBJECT("roleId", @v_role_owner),
             "r14", JSON_OBJECT("roleId", @v_role_package_manager),
             "r15", JSON_OBJECT("roleId", @v_role_page_manager),
             "r16", JSON_OBJECT("roleId", @v_role_permission_manager),
             "r17", JSON_OBJECT("roleId", @v_role_personalization_manager),
             "r18", JSON_OBJECT("roleId", @v_role_profile_manager),
             "r19", JSON_OBJECT("roleId", @v_role_role_manager),
             "r20", JSON_OBJECT("roleId", @v_role_schema_manager),
             "r21", JSON_OBJECT("roleId", @v_role_style_manager),
             "r22", JSON_OBJECT("roleId", @v_role_system_application_manager),
             "r23", JSON_OBJECT("roleId", @v_role_template_manager),
             "r24", JSON_OBJECT("roleId", @v_role_theme_manager),
             "r25", JSON_OBJECT("roleId", @v_role_transport_manager),
             "r26", JSON_OBJECT("roleId", @v_role_user_manager),
             "r27", JSON_OBJECT("roleId", @v_role_workflow_manager)
     )
        );

select id
from security.security_user
where USER_NAME = 'sysadmin'
into @v_user_sysadmin;
select id
from security.security_profile
where name = 'Appbuilder Owner'
into @v_profile_appbuilder_owner;

insert into security.security_profile_role(profile_id, role_id)
values (@v_profile_appbuilder_owner, @v_role_actions_manager),
       (@v_profile_appbuilder_owner, @v_role_app_manager),
       (@v_profile_appbuilder_owner, @v_role_client_manager),
       (@v_profile_appbuilder_owner, @v_role_client_type_manager),
       (@v_profile_appbuilder_owner, @v_role_client_update_manager),
       (@v_profile_appbuilder_owner, @v_role_data_connection_manager),
       (@v_profile_appbuilder_owner, @v_role_data_manager),
       (@v_profile_appbuilder_owner, @v_role_eventaction_manager),
       (@v_profile_appbuilder_owner, @v_role_eventdefinition_manager),
       (@v_profile_appbuilder_owner, @v_role_files_manager),
       (@v_profile_appbuilder_owner, @v_role_function_manager),
       (@v_profile_appbuilder_owner, @v_role_integration_manager),
       (@v_profile_appbuilder_owner, @v_role_owner),
       (@v_profile_appbuilder_owner, @v_role_package_manager),
       (@v_profile_appbuilder_owner, @v_role_page_manager),
       (@v_profile_appbuilder_owner, @v_role_permission_manager),
       (@v_profile_appbuilder_owner, @v_role_personalization_manager),
       (@v_profile_appbuilder_owner, @v_role_profile_manager),
       (@v_profile_appbuilder_owner, @v_role_role_manager),
       (@v_profile_appbuilder_owner, @v_role_schema_manager),
       (@v_profile_appbuilder_owner, @v_role_style_manager),
       (@v_profile_appbuilder_owner, @v_role_system_application_manager),
       (@v_profile_appbuilder_owner, @v_role_template_manager),
       (@v_profile_appbuilder_owner, @v_role_theme_manager),
       (@v_profile_appbuilder_owner, @v_role_transport_manager),
       (@v_profile_appbuilder_owner, @v_role_user_manager),
       (@v_profile_appbuilder_owner, @v_role_workflow_manager);

insert into security.security_profile_user(profile_id, user_id)
values (@v_profile_appbuilder_owner, @v_user_sysadmin);

-- V39__Security Profile Role Correction.sql


select id
from security.security_v2_role
where name = 'Data Manager'
into @v_data_man;
select id
from security.security_v2_role
where name = 'Data Connection Manager'
into @v_conn_man;

delete
from security.security_v2_role_role
where ROLE_ID in (@v_data_man, @v_conn_man);

select id
from security.security_v2_role
where name = 'Storage CREATE'
into @v_sr_sc;
select id
from security.security_v2_role
where name = 'Storage READ'
into @v_sr_sr;
select id
from security.security_v2_role
where name = 'Storage UPDATE'
into @v_sr_su;
select id
from security.security_v2_role
where name = 'Storage DELETE'
into @v_sr_sd;

insert into security.security_v2_role_role (role_id, sub_role_id)
values (@v_data_man, @v_sr_sc),
       (@v_data_man, @v_sr_sr),
       (@v_data_man, @v_sr_su),
       (@v_data_man, @v_sr_sd);

select id
from security.security_v2_role
where name = 'Connection CREATE'
into @v_sr_sc;
select id
from security.security_v2_role
where name = 'Connection READ'
into @v_sr_sr;
select id
from security.security_v2_role
where name = 'Connection UPDATE'
into @v_sr_su;
select id
from security.security_v2_role
where name = 'Connection DELETE'
into @v_sr_sd;

insert into security.security_v2_role_role (role_id, sub_role_id)
values (@v_conn_man, @v_sr_sc),
       (@v_conn_man, @v_sr_sr),
       (@v_conn_man, @v_sr_su),
       (@v_conn_man, @v_sr_sd);

select *
from security.security_v2_role_role
where ROLE_ID in (@v_data_man, @v_conn_man);

Select id
from security.security_v2_role
WHERE (`NAME` = 'STATIC Files PATH')
into @v_files_static_role;
Select id
from security.security_v2_role
WHERE (`NAME` = 'SECURED Files PATH')
into @v_files_secured_role;
UPDATE `security`.`security_v2_role`
SET `SHORT_NAME` = 'Static'
WHERE id = @v_files_static_role;
UPDATE `security`.`security_v2_role`
SET `SHORT_NAME` = 'Secured'
WHERE id = @v_files_secured_role;

-- V40__AppBuilder Owner profile updated.sql

select id
from security.security_v2_role
where name = 'Client Manager'
limit 1
into @v_r_1;
select id
from security.security_v2_role
where name = 'User Manager'
limit 1
into @v_r_2;
select id
from security.security_v2_role
where name = 'Package Manager'
limit 1
into @v_r_3;
select id
from security.security_v2_role
where name = 'Role Manager'
limit 1
into @v_r_4;
select id
from security.security_v2_role
where name = 'Permission Manager'
limit 1
into @v_r_5;
select id
from security.security_v2_role
where name = 'Client Type Manager'
limit 1
into @v_r_6;
select id
from security.security_v2_role
where name = 'Client Update Manager'
limit 1
into @v_r_7;
select id
from security.security_v2_role
where name = 'Application Manager'
limit 1
into @v_r_8;
select id
from security.security_v2_role
where name = 'System Application Manager'
limit 1
into @v_r_9;
select id
from security.security_v2_role
where name = 'Function Manager'
limit 1
into @v_r_10;
select id
from security.security_v2_role
where name = 'Page Manager'
limit 1
into @v_r_11;
select id
from security.security_v2_role
where name = 'Theme Manager'
limit 1
into @v_r_12;
select id
from security.security_v2_role
where name = 'Personalization Manager'
limit 1
into @v_r_13;
select id
from security.security_v2_role
where name = 'Files Manager'
limit 1
into @v_r_14;
select id
from security.security_v2_role
where name = 'Data Manager'
limit 1
into @v_r_15;
select id
from security.security_v2_role
where name = 'Data Connection Manager'
limit 1
into @v_r_16;
select id
from security.security_v2_role
where name = 'Schema Manager'
limit 1
into @v_r_17;
select id
from security.security_v2_role
where name = 'Workflow Manager'
limit 1
into @v_r_18;
select id
from security.security_v2_role
where name = 'Template Manager'
limit 1
into @v_r_19;
select id
from security.security_v2_role
where name = 'Actions Manager'
limit 1
into @v_r_20;
select id
from security.security_v2_role
where name = 'Style Manager'
limit 1
into @v_r_21;
select id
from security.security_v2_role
where name = 'Transport Manager'
limit 1
into @v_r_22;
select id
from security.security_v2_role
where name = 'EventDefinition Manager'
limit 1
into @v_r_23;
select id
from security.security_v2_role
where name = 'EventAction Manager'
limit 1
into @v_r_24;
select id
from security.security_v2_role
where name = 'Super Admin'
limit 1
into @v_r_25;
select id
from security.security_v2_role
where name = 'Integration Manager'
limit 1
into @v_r_26;
select id
from security.security_v2_role
where name = 'Client CREATE'
limit 1
into @v_r_32;
select id
from security.security_v2_role
where name = 'Client READ'
limit 1
into @v_r_33;
select id
from security.security_v2_role
where name = 'Client UPDATE'
limit 1
into @v_r_34;
select id
from security.security_v2_role
where name = 'Client DELETE'
limit 1
into @v_r_35;
select id
from security.security_v2_role
where name = 'User CREATE'
limit 1
into @v_r_37;
select id
from security.security_v2_role
where name = 'User READ'
limit 1
into @v_r_38;
select id
from security.security_v2_role
where name = 'User UPDATE'
limit 1
into @v_r_39;
select id
from security.security_v2_role
where name = 'User DELETE'
limit 1
into @v_r_40;
select id
from security.security_v2_role
where name = 'Package CREATE'
limit 1
into @v_r_43;
select id
from security.security_v2_role
where name = 'Package READ'
limit 1
into @v_r_44;
select id
from security.security_v2_role
where name = 'Package UPDATE'
limit 1
into @v_r_45;
select id
from security.security_v2_role
where name = 'Package DELETE'
limit 1
into @v_r_46;
select id
from security.security_v2_role
where name = 'Role CREATE'
limit 1
into @v_r_47;
select id
from security.security_v2_role
where name = 'Role READ'
limit 1
into @v_r_48;
select id
from security.security_v2_role
where name = 'Role UPDATE'
limit 1
into @v_r_49;
select id
from security.security_v2_role
where name = 'Role DELETE'
limit 1
into @v_r_50;
select id
from security.security_v2_role
where name = 'Permission CREATE'
limit 1
into @v_r_53;
select id
from security.security_v2_role
where name = 'Permission READ'
limit 1
into @v_r_54;
select id
from security.security_v2_role
where name = 'Permission UPDATE'
limit 1
into @v_r_55;
select id
from security.security_v2_role
where name = 'Permission DELETE'
limit 1
into @v_r_56;
select id
from security.security_v2_role
where name = 'Client Type CREATE'
limit 1
into @v_r_57;
select id
from security.security_v2_role
where name = 'Client Type READ'
limit 1
into @v_r_58;
select id
from security.security_v2_role
where name = 'Client Type UPDATE'
limit 1
into @v_r_59;
select id
from security.security_v2_role
where name = 'Client Type DELETE'
limit 1
into @v_r_60;
select id
from security.security_v2_role
where name = 'Application CREATE'
limit 1
into @v_r_61;
select id
from security.security_v2_role
where name = 'Application READ'
limit 1
into @v_r_62;
select id
from security.security_v2_role
where name = 'Application UPDATE'
limit 1
into @v_r_63;
select id
from security.security_v2_role
where name = 'Application DELETE'
limit 1
into @v_r_64;
select id
from security.security_v2_role
where name = 'Function CREATE'
limit 1
into @v_r_65;
select id
from security.security_v2_role
where name = 'Function READ'
limit 1
into @v_r_66;
select id
from security.security_v2_role
where name = 'Function UPDATE'
limit 1
into @v_r_67;
select id
from security.security_v2_role
where name = 'Function DELETE'
limit 1
into @v_r_68;
select id
from security.security_v2_role
where name = 'Page CREATE'
limit 1
into @v_r_69;
select id
from security.security_v2_role
where name = 'Page READ'
limit 1
into @v_r_70;
select id
from security.security_v2_role
where name = 'Page UPDATE'
limit 1
into @v_r_71;
select id
from security.security_v2_role
where name = 'Page DELETE'
limit 1
into @v_r_72;
select id
from security.security_v2_role
where name = 'Theme CREATE'
limit 1
into @v_r_73;
select id
from security.security_v2_role
where name = 'Theme READ'
limit 1
into @v_r_74;
select id
from security.security_v2_role
where name = 'Theme UPDATE'
limit 1
into @v_r_75;
select id
from security.security_v2_role
where name = 'Theme DELETE'
limit 1
into @v_r_76;
select id
from security.security_v2_role
where name = 'Personalization CLEAR'
limit 1
into @v_r_77;
select id
from security.security_v2_role
where name = 'STATIC Files PATH'
limit 1
into @v_r_78;
select id
from security.security_v2_role
where name = 'SECURED Files PATH'
limit 1
into @v_r_79;
select id
from security.security_v2_role
where name = 'Client Password Policy READ'
limit 1
into @v_r_80;
select id
from security.security_v2_role
where name = 'Client Password Policy CREATE'
limit 1
into @v_r_81;
select id
from security.security_v2_role
where name = 'Client Password Policy UPDATE'
limit 1
into @v_r_82;
select id
from security.security_v2_role
where name = 'Client Password Policy DELETE'
limit 1
into @v_r_83;
select id
from security.security_v2_role
where name = 'Storage CREATE'
limit 1
into @v_r_84;
select id
from security.security_v2_role
where name = 'Storage READ'
limit 1
into @v_r_85;
select id
from security.security_v2_role
where name = 'Storage UPDATE'
limit 1
into @v_r_86;
select id
from security.security_v2_role
where name = 'Storage DELETE'
limit 1
into @v_r_87;
select id
from security.security_v2_role
where name = 'Connection CREATE'
limit 1
into @v_r_88;
select id
from security.security_v2_role
where name = 'Connection READ'
limit 1
into @v_r_89;
select id
from security.security_v2_role
where name = 'Connection UPDATE'
limit 1
into @v_r_90;
select id
from security.security_v2_role
where name = 'Connection DELETE'
limit 1
into @v_r_91;
select id
from security.security_v2_role
where name = 'Schema CREATE'
limit 1
into @v_r_92;
select id
from security.security_v2_role
where name = 'Schema READ'
limit 1
into @v_r_93;
select id
from security.security_v2_role
where name = 'Schema UPDATE'
limit 1
into @v_r_94;
select id
from security.security_v2_role
where name = 'Schema DELETE'
limit 1
into @v_r_95;
select id
from security.security_v2_role
where name = 'Workflow CREATE'
limit 1
into @v_r_96;
select id
from security.security_v2_role
where name = 'Workflow READ'
limit 1
into @v_r_97;
select id
from security.security_v2_role
where name = 'Workflow UPDATE'
limit 1
into @v_r_98;
select id
from security.security_v2_role
where name = 'Workflow DELETE'
limit 1
into @v_r_99;
select id
from security.security_v2_role
where name = 'Template CREATE'
limit 1
into @v_r_100;
select id
from security.security_v2_role
where name = 'Template READ'
limit 1
into @v_r_101;
select id
from security.security_v2_role
where name = 'Template UPDATE'
limit 1
into @v_r_102;
select id
from security.security_v2_role
where name = 'Template DELETE'
limit 1
into @v_r_103;
select id
from security.security_v2_role
where name = 'Actions CREATE'
limit 1
into @v_r_104;
select id
from security.security_v2_role
where name = 'Actions READ'
limit 1
into @v_r_105;
select id
from security.security_v2_role
where name = 'Actions UPDATE'
limit 1
into @v_r_106;
select id
from security.security_v2_role
where name = 'Actions DELETE'
limit 1
into @v_r_107;
select id
from security.security_v2_role
where name = 'Style CREATE'
limit 1
into @v_r_108;
select id
from security.security_v2_role
where name = 'Style READ'
limit 1
into @v_r_109;
select id
from security.security_v2_role
where name = 'Style UPDATE'
limit 1
into @v_r_110;
select id
from security.security_v2_role
where name = 'Style DELETE'
limit 1
into @v_r_111;
select id
from security.security_v2_role
where name = 'Transport CREATE'
limit 1
into @v_r_112;
select id
from security.security_v2_role
where name = 'Transport READ'
limit 1
into @v_r_113;
select id
from security.security_v2_role
where name = 'Transport UPDATE'
limit 1
into @v_r_114;
select id
from security.security_v2_role
where name = 'Transport DELETE'
limit 1
into @v_r_115;
select id
from security.security_v2_role
where name = 'EventDefinition CREATE'
limit 1
into @v_r_116;
select id
from security.security_v2_role
where name = 'EventDefinition READ'
limit 1
into @v_r_117;
select id
from security.security_v2_role
where name = 'EventDefinition UPDATE'
limit 1
into @v_r_118;
select id
from security.security_v2_role
where name = 'EventDefinition DELETE'
limit 1
into @v_r_119;
select id
from security.security_v2_role
where name = 'EventAction CREATE'
limit 1
into @v_r_120;
select id
from security.security_v2_role
where name = 'EventAction READ'
limit 1
into @v_r_121;
select id
from security.security_v2_role
where name = 'EventAction UPDATE'
limit 1
into @v_r_122;
select id
from security.security_v2_role
where name = 'EventAction DELETE'
limit 1
into @v_r_123;
select id
from security.security_v2_role
where name = 'Integration CREATE'
limit 1
into @v_r_124;
select id
from security.security_v2_role
where name = 'Integration READ'
limit 1
into @v_r_125;
select id
from security.security_v2_role
where name = 'Integration UPDATE'
limit 1
into @v_r_126;
select id
from security.security_v2_role
where name = 'Integration DELETE'
limit 1
into @v_r_127;
select id
from security.security_v2_role
where name = 'Profile CREATE'
limit 1
into @v_r_159;
select id
from security.security_v2_role
where name = 'Profile READ'
limit 1
into @v_r_160;
select id
from security.security_v2_role
where name = 'Profile UPDATE'
limit 1
into @v_r_161;
select id
from security.security_v2_role
where name = 'Profile DELETE'
limit 1
into @v_r_162;
select id
from security.security_v2_role
where name = 'Profile Manager'
limit 1
into @v_r_163;
select id
from security.security_v2_role
where name = 'Owner'
limit 1
into @v_r_164;

select id
from security.security_profile
where name = 'Appbuilder Owner'
into @v_profile_appbuilder_owner;

update security.security_profile
set arrangement = JSON_OBJECT("r164", JSON_OBJECT("order", 3, "roleId", @v_r_164, "assignable", true), "g1",
                              JSON_OBJECT("name", "Security", "order", 2, "assignable", true, "subArrangements",
                                          JSON_OBJECT("r163",
                                                      JSON_OBJECT("order", 0, "roleId", @v_r_163, "assignable", true,
                                                                  "subArrangements", JSON_OBJECT("r160",
                                                                                                 JSON_OBJECT("order", 1, "roleId", @v_r_160, "assignable", true),
                                                                                                 "r162",
                                                                                                 JSON_OBJECT("order", 3, "roleId", @v_r_162, "assignable", true),
                                                                                                 "r159",
                                                                                                 JSON_OBJECT("order", 0, "roleId", @v_r_159, "assignable", true),
                                                                                                 "r161",
                                                                                                 JSON_OBJECT("order", 2, "roleId", @v_r_161, "assignable", true))),
                                                      "r2",
                                                      JSON_OBJECT("order", 1, "roleId", @v_r_2, "assignable", true,
                                                                  "subArrangements", JSON_OBJECT("r39",
                                                                                                 JSON_OBJECT("order", 2, "roleId", @v_r_39, "assignable", true),
                                                                                                 "r40",
                                                                                                 JSON_OBJECT("order", 3, "roleId", @v_r_40, "assignable", true),
                                                                                                 "r37",
                                                                                                 JSON_OBJECT("order", 0, "roleId", @v_r_37, "assignable", true),
                                                                                                 "r38",
                                                                                                 JSON_OBJECT("order", 1, "roleId", @v_r_38, "assignable", true))),
                                                      "r22",
                                                      JSON_OBJECT("order", 6, "roleId", @v_r_22, "assignable", true,
                                                                  "subArrangements", JSON_OBJECT("r114",
                                                                                                 JSON_OBJECT("order", 2, "roleId", @v_r_114, "assignable", true),
                                                                                                 "r115",
                                                                                                 JSON_OBJECT("order", 3, "roleId", @v_r_115, "assignable", true),
                                                                                                 "r112",
                                                                                                 JSON_OBJECT("order", 0, "roleId", @v_r_112, "assignable", true),
                                                                                                 "r113",
                                                                                                 JSON_OBJECT("order", 1, "roleId", @v_r_113, "assignable", true))),
                                                      "r14",
                                                      JSON_OBJECT("order", 3, "roleId", @v_r_14, "assignable", true,
                                                                  "subArrangements", JSON_OBJECT("r78",
                                                                                                 JSON_OBJECT("roleId", @v_r_78, "assignable", true),
                                                                                                 "r79",
                                                                                                 JSON_OBJECT("roleId", @v_r_79, "assignable", true))),
                                                      "r4",
                                                      JSON_OBJECT("order", 5, "roleId", @v_r_4, "assignable", true,
                                                                  "subArrangements", JSON_OBJECT("r49",
                                                                                                 JSON_OBJECT("order", 2, "roleId", @v_r_49, "assignable", true),
                                                                                                 "r50",
                                                                                                 JSON_OBJECT("order", 3, "roleId", @v_r_50, "assignable", true),
                                                                                                 "r47",
                                                                                                 JSON_OBJECT("order", 0, "roleId", @v_r_47, "assignable", true),
                                                                                                 "r48",
                                                                                                 JSON_OBJECT("order", 1, "roleId", @v_r_48, "assignable", true))),
                                                      "r1",
                                                      JSON_OBJECT("order", 2, "roleId", @v_r_1, "assignable", true,
                                                                  "subArrangements", JSON_OBJECT("r33",
                                                                                                 JSON_OBJECT("order", 1, "roleId", @v_r_33, "assignable", true),
                                                                                                 "r35",
                                                                                                 JSON_OBJECT("order", 3, "roleId", @v_r_35, "assignable", true),
                                                                                                 "r34",
                                                                                                 JSON_OBJECT("order", 2, "roleId", @v_r_34, "assignable", true),
                                                                                                 "r32",
                                                                                                 JSON_OBJECT("order", 0, "roleId", @v_r_32, "assignable", true))),
                                                      "r5",
                                                      JSON_OBJECT("order", 4, "roleId", @v_r_5, "assignable", true,
                                                                  "subArrangements", JSON_OBJECT("r54",
                                                                                                 JSON_OBJECT("order", 1, "roleId", @v_r_54, "assignable", true),
                                                                                                 "r55",
                                                                                                 JSON_OBJECT("order", 2, "roleId", @v_r_55, "assignable", true),
                                                                                                 "r56",
                                                                                                 JSON_OBJECT("order", 3, "roleId", @v_r_56, "assignable", true),
                                                                                                 "r53",
                                                                                                 JSON_OBJECT("order", 0, "roleId", @v_r_53, "assignable", true))))),
                              "g2", JSON_OBJECT("name", "Core", "order", 1, "assignable", true, "subArrangements",
                                                JSON_OBJECT("r20",
                                                            JSON_OBJECT("order", 9, "roleId", @v_r_20, "assignable",
                                                                        true, "subArrangements", JSON_OBJECT("r105",
                                                                                                             JSON_OBJECT("order", 1, "roleId", @v_r_105, "assignable", true),
                                                                                                             "r107",
                                                                                                             JSON_OBJECT("order", 3, "roleId", @v_r_107, "assignable", true),
                                                                                                             "r104",
                                                                                                             JSON_OBJECT("order", 0, "roleId", @v_r_104, "assignable", true),
                                                                                                             "r106",
                                                                                                             JSON_OBJECT("order", 2, "roleId", @v_r_106, "assignable", true))),
                                                            "r24",
                                                            JSON_OBJECT("order", 3, "roleId", @v_r_24, "assignable",
                                                                        true, "subArrangements", JSON_OBJECT("r121",
                                                                                                             JSON_OBJECT("order", 1, "roleId", @v_r_121, "assignable", true),
                                                                                                             "r120",
                                                                                                             JSON_OBJECT("order", 0, "roleId", @v_r_120, "assignable", true),
                                                                                                             "r122",
                                                                                                             JSON_OBJECT("order", 2, "roleId", @v_r_122, "assignable", true),
                                                                                                             "r123",
                                                                                                             JSON_OBJECT("order", 3, "roleId", @v_r_123, "assignable", true))),
                                                            "r16",
                                                            JSON_OBJECT("order", 1, "roleId", @v_r_16, "assignable",
                                                                        true, "subArrangements", JSON_OBJECT("r89",
                                                                                                             JSON_OBJECT("order", 1, "roleId", @v_r_89, "assignable", true),
                                                                                                             "r91",
                                                                                                             JSON_OBJECT("order", 3, "roleId", @v_r_91, "assignable", true),
                                                                                                             "r90",
                                                                                                             JSON_OBJECT("order", 2, "roleId", @v_r_90, "assignable", true),
                                                                                                             "r88",
                                                                                                             JSON_OBJECT("order", 0, "roleId", @v_r_88, "assignable", true))),
                                                            "r15",
                                                            JSON_OBJECT("order", 0, "roleId", @v_r_15, "assignable",
                                                                        true, "subArrangements", JSON_OBJECT("r87",
                                                                                                             JSON_OBJECT("order", 3, "roleId", @v_r_87, "assignable", true),
                                                                                                             "r84",
                                                                                                             JSON_OBJECT("order", 0, "roleId", @v_r_84, "assignable", true),
                                                                                                             "r85",
                                                                                                             JSON_OBJECT("order", 1, "roleId", @v_r_85, "assignable", true),
                                                                                                             "r86",
                                                                                                             JSON_OBJECT("order", 2, "roleId", @v_r_86, "assignable", true))),
                                                            "r19",
                                                            JSON_OBJECT("order", 6, "roleId", @v_r_19, "assignable",
                                                                        true, "subArrangements", JSON_OBJECT("r103",
                                                                                                             JSON_OBJECT("order", 3, "roleId", @v_r_103, "assignable", true),
                                                                                                             "r101",
                                                                                                             JSON_OBJECT("order", 1, "roleId", @v_r_101, "assignable", true),
                                                                                                             "r100",
                                                                                                             JSON_OBJECT("order", 0, "roleId", @v_r_100, "assignable", true),
                                                                                                             "r102",
                                                                                                             JSON_OBJECT("order", 2, "roleId", @v_r_102, "assignable", true))),
                                                            "r23",
                                                            JSON_OBJECT("order", 2, "roleId", @v_r_23, "assignable",
                                                                        true, "subArrangements", JSON_OBJECT("r118",
                                                                                                             JSON_OBJECT("order", 2, "roleId", @v_r_118, "assignable", true),
                                                                                                             "r119",
                                                                                                             JSON_OBJECT("order", 3, "roleId", @v_r_119, "assignable", true),
                                                                                                             "r116",
                                                                                                             JSON_OBJECT("order", 0, "roleId", @v_r_116, "assignable", true),
                                                                                                             "r117",
                                                                                                             JSON_OBJECT("order", 1, "roleId", @v_r_117, "assignable", true))),
                                                            "r17",
                                                            JSON_OBJECT("order", 4, "roleId", @v_r_17, "assignable",
                                                                        true, "subArrangements", JSON_OBJECT("r92",
                                                                                                             JSON_OBJECT("roleId", @v_r_92, "assignable", true),
                                                                                                             "r93",
                                                                                                             JSON_OBJECT("roleId", @v_r_93, "assignable", true),
                                                                                                             "r94",
                                                                                                             JSON_OBJECT("roleId", @v_r_94, "assignable", true),
                                                                                                             "r95",
                                                                                                             JSON_OBJECT("roleId", @v_r_95, "assignable", true))),
                                                            "r10",
                                                            JSON_OBJECT("order", 5, "roleId", @v_r_10, "assignable",
                                                                        true, "subArrangements", JSON_OBJECT("r67",
                                                                                                             JSON_OBJECT("order", 2, "roleId", @v_r_67, "assignable", true),
                                                                                                             "r66",
                                                                                                             JSON_OBJECT("order", 1, "roleId", @v_r_66, "assignable", true),
                                                                                                             "r68",
                                                                                                             JSON_OBJECT("order", 3, "roleId", @v_r_68, "assignable", true),
                                                                                                             "r65",
                                                                                                             JSON_OBJECT("order", 0, "roleId", @v_r_65, "assignable", true))),
                                                            "r26",
                                                            JSON_OBJECT("order", 7, "roleId", @v_r_26, "assignable",
                                                                        true, "subArrangements", JSON_OBJECT("r125",
                                                                                                             JSON_OBJECT("order", 1, "roleId", @v_r_125, "assignable", true),
                                                                                                             "r127",
                                                                                                             JSON_OBJECT("order", 3, "roleId", @v_r_127, "assignable", true),
                                                                                                             "r124",
                                                                                                             JSON_OBJECT("order", 0, "roleId", @v_r_124, "assignable", true),
                                                                                                             "r126",
                                                                                                             JSON_OBJECT("order", 2, "roleId", @v_r_126, "assignable", true))),
                                                            "r18",
                                                            JSON_OBJECT("order", 8, "roleId", @v_r_18, "assignable",
                                                                        true, "subArrangements", JSON_OBJECT("r99",
                                                                                                             JSON_OBJECT("order", 3, "roleId", @v_r_99, "assignable", true),
                                                                                                             "r96",
                                                                                                             JSON_OBJECT("order", 0, "roleId", @v_r_96, "assignable", true),
                                                                                                             "r98",
                                                                                                             JSON_OBJECT("order", 2, "roleId", @v_r_98, "assignable", true),
                                                                                                             "r97",
                                                                                                             JSON_OBJECT("order", 1, "roleId", @v_r_97, "assignable", true))))),
                              "g3", JSON_OBJECT("name", "UI", "order", 0, "assignable", true, "subArrangements",
                                                JSON_OBJECT("r21",
                                                            JSON_OBJECT("order", 3, "roleId", @v_r_21, "assignable",
                                                                        true, "subArrangements", JSON_OBJECT("r111",
                                                                                                             JSON_OBJECT("order", 3, "roleId", @v_r_111, "assignable", true),
                                                                                                             "r109",
                                                                                                             JSON_OBJECT("order", 1, "roleId", @v_r_109, "assignable", true),
                                                                                                             "r110",
                                                                                                             JSON_OBJECT("order", 2, "roleId", @v_r_110, "assignable", true),
                                                                                                             "r108",
                                                                                                             JSON_OBJECT("order", 0, "roleId", @v_r_108, "assignable", true))),
                                                            "r12",
                                                            JSON_OBJECT("order", 2, "roleId", @v_r_12, "assignable",
                                                                        true, "subArrangements", JSON_OBJECT("r73",
                                                                                                             JSON_OBJECT("order", 0, "roleId", @v_r_73, "assignable", true),
                                                                                                             "r75",
                                                                                                             JSON_OBJECT("order", 2, "roleId", @v_r_75, "assignable", true),
                                                                                                             "r74",
                                                                                                             JSON_OBJECT("order", 1, "roleId", @v_r_74, "assignable", true),
                                                                                                             "r76",
                                                                                                             JSON_OBJECT("order", 3, "roleId", @v_r_76, "assignable", true))),
                                                            "r13",
                                                            JSON_OBJECT("order", 4, "roleId", @v_r_13, "assignable",
                                                                        true, "subArrangements", JSON_OBJECT("r77",
                                                                                                             JSON_OBJECT("roleId", @v_r_77, "assignable", true))),
                                                            "r8",
                                                            JSON_OBJECT("order", 0, "roleId", @v_r_8, "assignable",
                                                                        true, "subArrangements", JSON_OBJECT("r61",
                                                                                                             JSON_OBJECT("order", 0, "roleId", @v_r_61, "assignable", true),
                                                                                                             "r63",
                                                                                                             JSON_OBJECT("order", 2, "roleId", @v_r_63, "assignable", true),
                                                                                                             "r64",
                                                                                                             JSON_OBJECT("order", 3, "roleId", @v_r_64, "assignable", true),
                                                                                                             "r62",
                                                                                                             JSON_OBJECT("order", 1, "roleId", @v_r_62, "assignable", true))),
                                                            "r11",
                                                            JSON_OBJECT("order", 1, "roleId", @v_r_11, "assignable",
                                                                        true, "subArrangements", JSON_OBJECT("r70",
                                                                                                             JSON_OBJECT("order", 1, "roleId", @v_r_70, "assignable", true),
                                                                                                             "r69",
                                                                                                             JSON_OBJECT("order", 0, "roleId", @v_r_69, "assignable", true),
                                                                                                             "r71",
                                                                                                             JSON_OBJECT("order", 2, "roleId", @v_r_71, "assignable", true),
                                                                                                             "r72",
                                                                                                             JSON_OBJECT("order", 3, "roleId", @v_r_72, "assignable", true))))))
where id = @v_profile_appbuilder_owner;

delete
from security.security_profile_role
where profile_id = @v_profile_appbuilder_owner;

insert into security.security_profile_role(profile_id, role_id)
values (@v_profile_appbuilder_owner, @v_r_1),
       (@v_profile_appbuilder_owner, @v_r_2),
       (@v_profile_appbuilder_owner, @v_r_3),
       (@v_profile_appbuilder_owner, @v_r_4),
       (@v_profile_appbuilder_owner, @v_r_5),
       (@v_profile_appbuilder_owner, @v_r_6),
       (@v_profile_appbuilder_owner, @v_r_7),
       (@v_profile_appbuilder_owner, @v_r_8),
       (@v_profile_appbuilder_owner, @v_r_9),
       (@v_profile_appbuilder_owner, @v_r_10),
       (@v_profile_appbuilder_owner, @v_r_11),
       (@v_profile_appbuilder_owner, @v_r_12),
       (@v_profile_appbuilder_owner, @v_r_13),
       (@v_profile_appbuilder_owner, @v_r_14),
       (@v_profile_appbuilder_owner, @v_r_15),
       (@v_profile_appbuilder_owner, @v_r_16),
       (@v_profile_appbuilder_owner, @v_r_17),
       (@v_profile_appbuilder_owner, @v_r_18),
       (@v_profile_appbuilder_owner, @v_r_19),
       (@v_profile_appbuilder_owner, @v_r_20),
       (@v_profile_appbuilder_owner, @v_r_21),
       (@v_profile_appbuilder_owner, @v_r_22),
       (@v_profile_appbuilder_owner, @v_r_23),
       (@v_profile_appbuilder_owner, @v_r_24),
       (@v_profile_appbuilder_owner, @v_r_25),
       (@v_profile_appbuilder_owner, @v_r_26),
       (@v_profile_appbuilder_owner, @v_r_32),
       (@v_profile_appbuilder_owner, @v_r_33),
       (@v_profile_appbuilder_owner, @v_r_34),
       (@v_profile_appbuilder_owner, @v_r_35),
       (@v_profile_appbuilder_owner, @v_r_37),
       (@v_profile_appbuilder_owner, @v_r_38),
       (@v_profile_appbuilder_owner, @v_r_39),
       (@v_profile_appbuilder_owner, @v_r_40),
       (@v_profile_appbuilder_owner, @v_r_43),
       (@v_profile_appbuilder_owner, @v_r_44),
       (@v_profile_appbuilder_owner, @v_r_45),
       (@v_profile_appbuilder_owner, @v_r_46),
       (@v_profile_appbuilder_owner, @v_r_47),
       (@v_profile_appbuilder_owner, @v_r_48),
       (@v_profile_appbuilder_owner, @v_r_49),
       (@v_profile_appbuilder_owner, @v_r_50),
       (@v_profile_appbuilder_owner, @v_r_53),
       (@v_profile_appbuilder_owner, @v_r_54),
       (@v_profile_appbuilder_owner, @v_r_55),
       (@v_profile_appbuilder_owner, @v_r_56),
       (@v_profile_appbuilder_owner, @v_r_57),
       (@v_profile_appbuilder_owner, @v_r_58),
       (@v_profile_appbuilder_owner, @v_r_59),
       (@v_profile_appbuilder_owner, @v_r_60),
       (@v_profile_appbuilder_owner, @v_r_61),
       (@v_profile_appbuilder_owner, @v_r_62),
       (@v_profile_appbuilder_owner, @v_r_63),
       (@v_profile_appbuilder_owner, @v_r_64),
       (@v_profile_appbuilder_owner, @v_r_65),
       (@v_profile_appbuilder_owner, @v_r_66),
       (@v_profile_appbuilder_owner, @v_r_67),
       (@v_profile_appbuilder_owner, @v_r_68),
       (@v_profile_appbuilder_owner, @v_r_69),
       (@v_profile_appbuilder_owner, @v_r_70),
       (@v_profile_appbuilder_owner, @v_r_71),
       (@v_profile_appbuilder_owner, @v_r_72),
       (@v_profile_appbuilder_owner, @v_r_73),
       (@v_profile_appbuilder_owner, @v_r_74),
       (@v_profile_appbuilder_owner, @v_r_75),
       (@v_profile_appbuilder_owner, @v_r_76),
       (@v_profile_appbuilder_owner, @v_r_77),
       (@v_profile_appbuilder_owner, @v_r_78),
       (@v_profile_appbuilder_owner, @v_r_79),
       (@v_profile_appbuilder_owner, @v_r_80),
       (@v_profile_appbuilder_owner, @v_r_81),
       (@v_profile_appbuilder_owner, @v_r_82),
       (@v_profile_appbuilder_owner, @v_r_83),
       (@v_profile_appbuilder_owner, @v_r_84),
       (@v_profile_appbuilder_owner, @v_r_85),
       (@v_profile_appbuilder_owner, @v_r_86),
       (@v_profile_appbuilder_owner, @v_r_87),
       (@v_profile_appbuilder_owner, @v_r_88),
       (@v_profile_appbuilder_owner, @v_r_89),
       (@v_profile_appbuilder_owner, @v_r_90),
       (@v_profile_appbuilder_owner, @v_r_91),
       (@v_profile_appbuilder_owner, @v_r_92),
       (@v_profile_appbuilder_owner, @v_r_93),
       (@v_profile_appbuilder_owner, @v_r_94),
       (@v_profile_appbuilder_owner, @v_r_95),
       (@v_profile_appbuilder_owner, @v_r_96),
       (@v_profile_appbuilder_owner, @v_r_97),
       (@v_profile_appbuilder_owner, @v_r_98),
       (@v_profile_appbuilder_owner, @v_r_99),
       (@v_profile_appbuilder_owner, @v_r_100),
       (@v_profile_appbuilder_owner, @v_r_101),
       (@v_profile_appbuilder_owner, @v_r_102),
       (@v_profile_appbuilder_owner, @v_r_103),
       (@v_profile_appbuilder_owner, @v_r_104),
       (@v_profile_appbuilder_owner, @v_r_105),
       (@v_profile_appbuilder_owner, @v_r_106),
       (@v_profile_appbuilder_owner, @v_r_107),
       (@v_profile_appbuilder_owner, @v_r_108),
       (@v_profile_appbuilder_owner, @v_r_109),
       (@v_profile_appbuilder_owner, @v_r_110),
       (@v_profile_appbuilder_owner, @v_r_111),
       (@v_profile_appbuilder_owner, @v_r_112),
       (@v_profile_appbuilder_owner, @v_r_113),
       (@v_profile_appbuilder_owner, @v_r_114),
       (@v_profile_appbuilder_owner, @v_r_115),
       (@v_profile_appbuilder_owner, @v_r_116),
       (@v_profile_appbuilder_owner, @v_r_117),
       (@v_profile_appbuilder_owner, @v_r_118),
       (@v_profile_appbuilder_owner, @v_r_119),
       (@v_profile_appbuilder_owner, @v_r_120),
       (@v_profile_appbuilder_owner, @v_r_121),
       (@v_profile_appbuilder_owner, @v_r_122),
       (@v_profile_appbuilder_owner, @v_r_123),
       (@v_profile_appbuilder_owner, @v_r_124),
       (@v_profile_appbuilder_owner, @v_r_125),
       (@v_profile_appbuilder_owner, @v_r_126),
       (@v_profile_appbuilder_owner, @v_r_127),
       (@v_profile_appbuilder_owner, @v_r_159),
       (@v_profile_appbuilder_owner, @v_r_160),
       (@v_profile_appbuilder_owner, @v_r_161),
       (@v_profile_appbuilder_owner, @v_r_162),
       (@v_profile_appbuilder_owner, @v_r_163),
       (@v_profile_appbuilder_owner, @v_r_164);

-- V41__Security OneTimeToken script.sql
use security;

DROP TABLE IF EXISTS `security_one_time_token`;

CREATE TABLE `security_one_time_token`
(
    `ID`         bigint unsigned                                              NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `USER_ID`    bigint unsigned                                              NOT NULL COMMENT 'User id',
    `TOKEN`      char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci    NOT NULL COMMENT 'One Time Token',
    `IP_ADDRESS` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'User IP from where he logged in',
    `CREATED_AT` timestamp                                                    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (`ID`),
    KEY `FK1_ONE_TIME_TOKEN_USER_ID` (`USER_ID`),
    UNIQUE KEY `FK3_ONE_TIME_TOKEN` (`TOKEN`),
    CONSTRAINT `FK2_ONE_TIME_TOKEN_USER_ID` FOREIGN KEY (`USER_ID`) REFERENCES `security_user` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;


-- V42__Security Alter Department Designation Add UniqueKey.sql

USE `security`;

ALTER TABLE `security`.`security_department`
    ADD CONSTRAINT `UK1_SECURITY_DEPARTMENT_CLIENT_ID_NAME` UNIQUE (`CLIENT_ID`, `NAME`);

ALTER TABLE `security`.`security_designation`
    ADD CONSTRAINT `UK1_SECURITY_DESIGNATION_CLIENT_ID_NAME` UNIQUE (`CLIENT_ID`, `NAME`);


-- V43__Invite Users.sql (security)
use security;

DROP TABLE IF EXISTS `security`.`security_user_invite`;

CREATE TABLE `security`.`security_user_invite` (
                                                   `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

                                                   `CLIENT_ID` bigint UNSIGNED NOT NULL COMMENT 'Client id for the user to be created in',
                                                   `USER_NAME` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'User Name to login',
                                                   `EMAIL_ID` varchar(320) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Email ID to login',
                                                   `PHONE_NUMBER` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Phone Number to login',
                                                   `FIRST_NAME` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'First name',
                                                   `LAST_NAME` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Last name',

                                                   `INVITE_CODE` char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Unique Invitation Code',
                                                   `PROFILE_ID` bigint UNSIGNED DEFAULT NULL COMMENT 'Profile Id to assign by default',
                                                   `DESIGNATION_ID` bigint UNSIGNED DEFAULT NULL COMMENT 'Designation Id to assign by default',

                                                   `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
                                                   `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

                                                   PRIMARY KEY (`ID`),
                                                   UNIQUE KEY (`INVITE_CODE`),
                                                   UNIQUE KEY (`CLIENT_ID`, `EMAIL_ID`),
                                                   UNIQUE KEY (`CLIENT_ID`, `PHONE_NUMBER`),

                                                   CONSTRAINT `fk_security_user_invite_client` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security`.`security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
                                                   CONSTRAINT `fk_security_user_invite_profile` FOREIGN KEY (`PROFILE_ID`) REFERENCES `security`.`security_profile` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
                                                   CONSTRAINT `fk_security_user_invite_designation` FOREIGN KEY (`DESIGNATION_ID`) REFERENCES `security`.`security_designation` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- V44__Social Login SSO.sql (security)
USE security;

ALTER TABLE `security_app_reg_integration_tokens`
    ADD COLUMN `REQUEST_PARAM` JSON DEFAULT NULL COMMENT 'app metadata from request url' AFTER `STATE`;


-- V45__Alter App Reg Integration Add Signup URI.sql (security)
USE `security`;

ALTER TABLE `security`.`security_app_reg_integration`
    ADD COLUMN `SIGNUP_URI` VARCHAR(2083) DEFAULT NULL COMMENT 'URI for signup' AFTER `LOGIN_URI`;

UPDATE `security`.`security_app_reg_integration`
SET `security_app_reg_integration`.`SIGNUP_URI` = `security_app_reg_integration`.`LOGIN_URI`
WHERE security_app_reg_integration.`SIGNUP_URI` IS NULL;

ALTER TABLE `security`.`security_app_reg_integration`
    MODIFY COLUMN `SIGNUP_URI` VARCHAR(2083) NOT NULL COMMENT 'URI for signup' AFTER `LOGIN_URI`;


-- V5__Alter Core Tokens Unique Constraint.sql (core)
USE `core`;

ALTER TABLE `core`.`core_tokens`
    DROP CONSTRAINT `UK_CORE_TOKEN_STATE_TOKEN_TYPE`;


-- V46__Alter Client Add Company Details.sql (security)
USE `security`;

ALTER TABLE `security`.`security_client`
    ADD COLUMN `BUSINESS_SIZE` VARCHAR(128) DEFAULT NULL COMMENT 'client business size input',
    ADD COLUMN `INDUSTRY`      VARCHAR(128) DEFAULT NULL COMMENT 'client business industry';


-- Add scripts from the project above this line and seed data below this line.

-- Seed data....
use security;

-- security_client
INSERT INTO `security`.`security_client` (`CODE`, `NAME`, `TYPE_CODE`, `TOKEN_VALIDITY_MINUTES`, `LOCALE_CODE`)
VALUES ('CLIA', 'Client A', 'BUS', '20', 'en-US'),
       ('CLIB', 'Client B', 'BUS', '25', 'en-US'),
       ('CLIC', 'Client C', 'BUS', '25', 'en-US'),
       ('CLIA1', 'Client A1', 'BUS', '15', 'en-US'),
       ('CLIA2', 'Client A2', 'BUS', '17', 'en-US');

SELECT ID
FROM `security`.`security_client`
where NAME = 'Client A'
limit 1
into @v_clienta;
SELECT ID
FROM `security`.`security_client`
where NAME = 'Client B'
limit 1
into @v_clientb;
SELECT ID
FROM `security`.`security_client`
where NAME = 'Client C'
limit 1
into @v_clientc;
SELECT ID
FROM `security`.`security_client`
where NAME = 'Client A1'
limit 1
into @v_clienta1;
SELECT ID
FROM `security`.`security_client`
where NAME = 'Client A2'
limit 1
into @v_clienta2;

-- security user
INSERT INTO `security`.`security_user` (`CLIENT_ID`, `USER_NAME`, `EMAIL_ID`, `PHONE_NUMBER`, `FIRST_NAME`, `LAST_NAME`,
                                        `LOCALE_CODE`, `PASSWORD`, `PASSWORD_HASHED`, `ACCOUNT_NON_EXPIRED`,
                                        `ACCOUNT_NON_LOCKED`, `CREDENTIALS_NON_EXPIRED`, `NO_FAILED_ATTEMPT`,
                                        `STATUS_CODE`)
VALUES (@v_clienta, 'userA001', 'NONE', 'NONE', 'User A', '001', 'en-US', 'fincity', '0', '1', '1', '1', '0', 'ACTIVE'),
       (@v_clienta, 'userA002', 'NONE', 'NONE', 'User A', '002', 'en-US', 'fincity', '0', '1', '1', '1', '0', 'ACTIVE'),
       (@v_clientb, 'userB001', 'NONE', 'NONE', 'User B', '001', 'en-US', 'fincity', '0', '1', '1', '1', '0', 'ACTIVE'),
       (@v_clientb, 'userB002', 'NONE', 'NONE', 'User B', '002', 'en-US', 'fincity', '0', '1', '1', '1', '0', 'ACTIVE'),
       (@v_clientc, 'userC001', 'NONE', 'NONE', 'User C', '001', 'en-US', 'fincity', '0', '1', '1', '1', '0',
        'INACTIVE'),
       (@v_clientc, 'userC002', 'NONE', 'NONE', 'User C', '002', 'en-US', 'fincity', '0', '1', '1', '1', '0', 'ACTIVE'),
       (@v_clienta1, 'userA1001', 'NONE', 'NONE', 'User A1001', 'A1001', 'en-US', 'fincity', '0', '1', '1', '1', '0',
        'INACTIVE'),
       (@v_clienta1, 'userA1002', 'NONE', 'NONE', 'User A1002', 'A1002', 'en-US', 'fincity', '0', '1', '1', '1', '0',
        'ACTIVE'),
       (@v_clienta2, 'userA2001', 'NONE', 'NONE', 'User A2001', 'A2001', 'en-US', 'fincity', '0', '1', '1', '1', '0',
        'ACTIVE'),
       (@v_clienta2, 'userA2002', 'NONE', 'NONE', 'User A2002', 'A2002', 'en-US', 'fincity', '0', '1', '1', '1', '0',
        'INACTIVE');

-- client manage
INSERT INTO security.security_client_manage (`CLIENT_ID`, `MANAGE_CLIENT_ID`)
VALUES (@v_clienta1, @v_clienta),
       (@v_clienta2, @v_clienta);


-- adding apps
INSERT INTO `security`.`security_app` (`CLIENT_ID`, `APP_NAME`, `APP_CODE`, `APP_TYPE`)
VALUES (@v_clienta, 'CA Application', 'caapp', 'APP'),
       (@v_clientb, 'CB Application', 'cbapp', 'APP');

select id
from security.security_app
where app_code = 'caapp'
limit 1
into @v_app_caapp;

INSERT INTO `security`.`security_app_access` (`CLIENT_ID`, `APP_ID`, `EDIT_ACCESS`)
VALUES (@v_clienta1, @v_app_caapp, '0'),
       (@v_clienta2, @v_app_caapp, '1'),
       (@v_clienta, @v_app_appbuilder, '1');

-- adding package to client

insert into security.security_client_package (client_id, package_id)
select @v_clienta, ID
from security.security_package
where base = 0
  and code not in ('CLITYP', 'APPSYS');
insert into security.security_client_package (client_id, package_id)
select @v_clientb, ID
from security.security_package
where base = 0
  and code not in ('CLITYP', 'APPSYS', 'CLIENT');
insert into security.security_client_package (client_id, package_id)
select @v_clientc, ID
from security.security_package
where base = 0
  and code not in ('CLITYP', 'APPSYS', 'CLIENT');

select id
from security.security_user
where user_name = 'userA001'
limit 1
into @v_user_usera001;
select id
from security.security_user
where user_name = 'userA002'
limit 1
into @v_user_usera002;
select id
from security.security_user
where user_name = 'userB001'
limit 1
into @v_user_userb001;
select id
from security.security_user
where user_name = 'userC002'
limit 1
into @v_user_userc002;
select id
from security.security_user
where user_name = 'userA1002'
limit 1
into @v_user_usera1002;
select id
from security.security_user
where user_name = 'userA2001'
limit 1
into @v_user_usera2001;

insert into security.security_user_role_permission(user_id, role_id)
select @v_user_usera001,
       role_id
from security.security_package_role
where package_id
          in (select package_id from security.security_client_package where client_id = @v_clienta);

insert into security.security_user_role_permission(user_id, role_id)
select @v_user_userb001,
       role_id
from security.security_package_role
where package_id
          in (select package_id from security.security_client_package where client_id = @v_clientb);

insert into security.security_user_role_permission(user_id, role_id)
select @v_user_userc002,
       role_id
from security.security_package_role
where package_id
          in (select package_id from security.security_client_package where client_id = @v_clientc);

-- Adding logins for the users
-- Used the following query to genereate the values...
-- SELECT concat('@v_user_',lower(u.user_name)), t.token, t.part_token, t.expires_at, '0.0.0.0' FROM security.security_user_token t left join security.security_user u on u.id = t.user_id;

INSERT INTO `security`.`security_user_token` (`USER_ID`, `TOKEN`, `PART_TOKEN`, `EXPIRES_AT`, `IP_ADDRESS`)
VALUES (@v_user_sysadmin,
        'eyJhbGciOiJIUzUxMiJ9.eyJob3N0TmFtZSI6ImxvY2FsaG9zdDo4MDgwIiwicG9ydCI6IjgwODAiLCJsb2dnZWRJbkNsaWVudElkIjoxLCJsb2dnZWRJbkNsaWVudENvZGUiOiJTWVNURU0iLCJ1c2VySWQiOjEsImlhdCI6MTY3MDU5ODY3NywiZXhwIjoxNzAyMTM0Njc3fQ.qI44rXQe6FjhhWHVStwmwhP7p6XqMk-9CZ05jLDwUcyrXhJ6-05BwUS3TmoZi8_VtS1JA7ra_Yw8LTNXpMofgw',
        'jLDwUcyrXhJ6-05BwUS3TmoZi8_VtS1JA7ra_Yw8LTNXpMofgw', '2023-12-09 15:11:18', '0.0.0.0'),
       (@v_user_usera001,
        'eyJhbGciOiJIUzUxMiJ9.eyJob3N0TmFtZSI6ImxvY2FsaG9zdDo4MDgwIiwicG9ydCI6IjgwODAiLCJsb2dnZWRJbkNsaWVudElkIjoyLCJsb2dnZWRJbkNsaWVudENvZGUiOiJDTElBIiwidXNlcklkIjoyLCJpYXQiOjE2NzA1OTg2ODgsImV4cCI6MTcwMjEzNDY4OH0.EXvrcfmm1Rad-I4HOkYV7Ynhp8oe1yj72fL_j7BXDC5_2z3kkfpUqcOW-fQkyn0i9055Q_4BXul214HL-JdNNg',
        'j7BXDC5_2z3kkfpUqcOW-fQkyn0i9055Q_4BXul214HL-JdNNg', '2023-12-09 15:11:28', '0.0.0.0'),
       (@v_user_usera002,
        'eyJhbGciOiJIUzUxMiJ9.eyJob3N0TmFtZSI6ImxvY2FsaG9zdDo4MDgwIiwicG9ydCI6IjgwODAiLCJsb2dnZWRJbkNsaWVudElkIjoyLCJsb2dnZWRJbkNsaWVudENvZGUiOiJDTElBIiwidXNlcklkIjozLCJpYXQiOjE2NzA1OTg2OTIsImV4cCI6MTcwMjEzNDY5Mn0.rHb4nkhYHjz5lQ46_QOVKDtmeH6MdXvk5ug-0NEt5_V46o4_zJ4kwd9c5a1205qm4hMtx5n_E5N35wRMAAGOjg',
        '0NEt5_V46o4_zJ4kwd9c5a1205qm4hMtx5n_E5N35wRMAAGOjg', '2023-12-09 15:11:33', '0.0.0.0'),
       (@v_user_usera1002,
        'eyJhbGciOiJIUzUxMiJ9.eyJob3N0TmFtZSI6ImxvY2FsaG9zdDo4MDgwIiwicG9ydCI6IjgwODAiLCJsb2dnZWRJbkNsaWVudElkIjoyLCJsb2dnZWRJbkNsaWVudENvZGUiOiJDTElBIiwidXNlcklkIjo5LCJpYXQiOjE2NzA1OTg2OTYsImV4cCI6MTcwMjEzNDY5Nn0.GT7Hkd0CsKhZkOFmYQI3bMLBZG8RMkgFJdvg8NcxPDzttXpTZW_BVne-2oM4K3E5-nQ08jiV4C5A1zcaqOBpOg',
        '8NcxPDzttXpTZW_BVne-2oM4K3E5-nQ08jiV4C5A1zcaqOBpOg', '2023-12-09 15:11:37', '0.0.0.0'),
       (@v_user_usera2001,
        'eyJhbGciOiJIUzUxMiJ9.eyJob3N0TmFtZSI6ImxvY2FsaG9zdDo4MDgwIiwicG9ydCI6IjgwODAiLCJsb2dnZWRJbkNsaWVudElkIjoyLCJsb2dnZWRJbkNsaWVudENvZGUiOiJDTElBIiwidXNlcklkIjoxMCwiaWF0IjoxNjcwNTk4NzAwLCJleHAiOjE3MDIxMzQ3MDB9.bTk3ohOGS1X9yXnWyDYY33op5fPqzTaC-Rkw7CJpf2SuwVjSvoe1Ls-oZb-_ra6jSynX5KAIRn2_wU-09S8uww',
        '7CJpf2SuwVjSvoe1Ls-oZb-_ra6jSynX5KAIRn2_wU-09S8uww', '2023-12-09 15:11:40', '0.0.0.0'),
       (@v_user_userb001,
        'eyJhbGciOiJIUzUxMiJ9.eyJob3N0TmFtZSI6ImxvY2FsaG9zdDo4MDgwIiwicG9ydCI6IjgwODAiLCJsb2dnZWRJbkNsaWVudElkIjozLCJsb2dnZWRJbkNsaWVudENvZGUiOiJDTElCIiwidXNlcklkIjo0LCJpYXQiOjE2NzA1OTg3MTIsImV4cCI6MTcwMjEzNDcxMn0.52d1ecS7bjzItZJLjVWTAQd3lDFgzefg2vwzQChTMZyTKH4ztaCblS2nQbUKP4YCJ0b4gYM409SxrhSC6Bj7Xw',
        'QChTMZyTKH4ztaCblS2nQbUKP4YCJ0b4gYM409SxrhSC6Bj7Xw', '2023-12-09 15:11:52', '0.0.0.0'),
       (@v_user_userc002,
        'eyJhbGciOiJIUzUxMiJ9.eyJob3N0TmFtZSI6ImxvY2FsaG9zdDo4MDgwIiwicG9ydCI6IjgwODAiLCJsb2dnZWRJbkNsaWVudElkIjo0LCJsb2dnZWRJbkNsaWVudENvZGUiOiJDTElDIiwidXNlcklkIjo3LCJpYXQiOjE2NzA1OTg3MjQsImV4cCI6MTcwMjEzNDcyNH0.zIaTJDFuPNc4OhoRi87LcbXkx5YAwoWY5w55VvBGNhgxo5E3VrSZR3AqsuSIUaN3M4e57hbCahIL_FXK6vfV7g',
        'VvBGNhgxo5E3VrSZR3AqsuSIUaN3M4e57hbCahIL_FXK6vfV7g', '2023-12-09 15:12:04', '0.0.0.0');

INSERT INTO `security`.`security_client_url` (`CLIENT_ID`, `URL_PATTERN`, `APP_CODE`)
VALUES ('1', 'http://apps.localxyz.ai:8080', 'appbuilder');

INSERT INTO `security`.`security_client` (`CODE`, `NAME`, `TYPE_CODE`, `TOKEN_VALIDITY_MINUTES`)
VALUES ('fin1', 'Fincity1', 'BUS', '30'),
       ('fin2', 'Fincity2', 'BUS', '30');

INSERT INTO security.security_client_package (package_id, client_id)
    (SELECT id, (SELECT id FROM security.security_client where code = 'fin1')
     FROM security.security_package
     WHERE code NOT IN ('CLITYP', 'APPSYS'));

INSERT INTO security.security_client_package (package_id, client_id)
    (SELECT id, (SELECT id FROM security.security_client where code = 'fin2')
     FROM security.security_package
     WHERE code NOT IN ('CLITYP', 'APPSYS'));

SELECT ID
FROM `security`.`security_client`
where NAME = 'Fincity1'
limit 1
into @v_client_fin1;
SELECT ID
FROM `security`.`security_client`
where NAME = 'Fincity2'
limit 1
into @v_client_fin2;

INSERT INTO `security`.`security_user` (`CLIENT_ID`, `USER_NAME`, `EMAIL_ID`, `PHONE_NUMBER`, `FIRST_NAME`, `LAST_NAME`,
                                        `LOCALE_CODE`, `PASSWORD`, `PASSWORD_HASHED`, `ACCOUNT_NON_EXPIRED`,
                                        `ACCOUNT_NON_LOCKED`, `CREDENTIALS_NON_EXPIRED`, `NO_FAILED_ATTEMPT`,
                                        `STATUS_CODE`)
VALUES (@v_client_fin1, 'user@fincity1.com', 'user@fincity1.com', 'NONE', 'Fincity 1', 'User', 'en-US', 'fincity', '0',
        '1', '1', '1', '0', 'ACTIVE'),
       (@v_client_fin2, 'user@fincity2.com', 'user@fincity2.com', 'NONE', 'Fincity 2', 'User', 'en-US', 'fincity', '0',
        '1', '1', '1', '0', 'ACTIVE');

SELECT ID
FROM `security`.`security_user`
where USER_NAME = 'user@fincity1.com'
limit 1
into @v_user_fin1;
SELECT ID
FROM `security`.`security_user`
where USER_NAME = 'user@fincity2.com'
limit 1
into @v_user_fin2;


INSERT INTO `security`.`security_user_role_permission` (`USER_ID`, `ROLE_ID`)
    (SELECT @v_user_fin1 as user_id, pr.role_id as role_id
     FROM security.security_package_role pr
              LEFT JOIN security.security_client_package cp ON cp.package_id = pr.package_id
     where cp.client_id = @v_client_fin1);

INSERT INTO `security`.`security_user_role_permission` (`USER_ID`, `ROLE_ID`)
    (SELECT @v_user_fin2 as user_id, pr.role_id as role_id
     FROM security.security_package_role pr
              LEFT JOIN security.security_client_package cp ON cp.package_id = pr.package_id
     where cp.client_id = @v_client_fin2);

SELECT ID
FROM security.security_app
where APP_CODE = 'appbuilder'
limit 1
into @v_app_appbuilder;

INSERT INTO `security`.`security_app_access` (client_id, app_id, EDIT_ACCESS)
VALUES (@v_client_fin1, @v_app_appbuilder, 0),
       (@v_client_fin2, @v_app_appbuilder, 0);

INSERT INTO `security`.`security_user_role_permission` (`USER_ID`, `ROLE_ID`)
VALUES (@v_client_system, @v_role_schema);

-- INSERT INTO security.security_app_package (client_id, app_id, package_id)
--	select 1, 6, id from security.security_package WHERE code NOT IN ('CLITYP', 'APPSYS', 'CLIENT', 'TRANSP');

-- INSERT INTO security.security_app_package (client_id, app_id, package_id)
--	select 8, 6, id from security.security_package WHERE code NOT IN ('CLITYP', 'APPSYS', 'CLIENT', 'TRANSP');

-- INSERT INTO security.security_app_user_role (client_id, app_id, role_id)
-- SELECT 1, 6, role_id FROM security.security_package_role pr 
-- 	 join security.security_app_package ap on ap.PACKAGE_ID = pr.PACKAGE_ID
--      where ap.client_id = 1;

-- INSERT INTO security.security_app_package (client_id, app_id, package_id)
-- 	select 1, 6, id from security.security_package WHERE code NOT IN ('CLITYP', 'APPSYS', 'CLIENT', 'TRANSP');

-- insert into security.security_app_user_role(client_id, app_id, role_id)
-- select 1, 6, role_id from security.security_app_package ap
-- 	left join security.security_package_role rp on rp.package_id = ap.package_id
--     where role_id is not null;

-- Testing 
