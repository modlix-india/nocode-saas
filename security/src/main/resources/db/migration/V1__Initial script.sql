/* Creating Database */

-- DROP DATABASE IF EXISTS `security`;

CREATE DATABASE IF NOT EXISTS `security` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `security`;

/* Client */
CREATE TABLE IF NOT EXISTS `security_client_type`(
	ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
	CODE CHAR(4) NOT NULL COMMENT 'Code',
	TYPE VARCHAR(256) NOT NULL COMMENT 'Type',
    DESCRIPTION TEXT DEFAULT NULL COMMENT 'Description of the client type',
	CREATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
	CREATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
	UPDATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
	UPDATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
	PRIMARY KEY (ID),
    UNIQUE KEY UK1_CLIENT_TYPE_CODE (CODE)
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_client_type` (`CODE`, `TYPE`, `DESCRIPTION`) VALUES 
	('SYS', 'System', 'System client to manage system. Primarily only one client of this type will be created to manage the entire system.'),
    ('BUS', 'Business', 'Business client is for the clients who wants to create a business, may it be a business partner (marketing agency) or an industry player (real estate developer, bank or any business).');
    
CREATE TABLE IF NOT EXISTS `security_client` (
	ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
	CODE CHAR(8) NOT NULL COMMENT 'Client code',
	NAME VARCHAR(256) NOT NULL COMMENT 'Name of the client',
	TYPE_CODE CHAR(4) NOT NULL COMMENT 'Type of client',
    TOKEN_VALIDITY_MINUTES INT UNSIGNED NOT NULL DEFAULT 30 COMMENT 'Token validity in minutes',
    LOCALE_CODE VARCHAR(10) DEFAULT 'en-US' COMMENT 'Client default locale',
	STATUS_CODE ENUM('ACTIVE','INACTIVE','DELETED','LOCKED') DEFAULT 'ACTIVE' COMMENT 'Status of the client',
	CREATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
	CREATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
	UPDATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
	UPDATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
	PRIMARY KEY (ID),
    UNIQUE KEY UK1_CLIENT_CODE (CODE),
	UNIQUE KEY UK2_CLIENT_NAME (NAME),
	CONSTRAINT FK1_CLIENT_CLIENT_TYPE_CODE FOREIGN KEY (TYPE_CODE) REFERENCES `security_client_type` (CODE) ON DELETE RESTRICT ON UPDATE RESTRICT
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_client` (`CODE`, `NAME`, `TYPE_CODE`) VALUES
	('SYSTEM', 'System Internal', 'SYS');
    
SELECT ID from `security_client` WHERE CODE = 'SYSTEM' LIMIT 1 INTO @v_client_system;

CREATE TABLE IF NOT EXISTS `security_client_url` (
	ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
	CLIENT_ID BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
    URL_PATTERN VARCHAR(256) NOT NULL COMMENT 'URL Pattern to identify user\'s Client ID',
    
    CREATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
	CREATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
	UPDATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
	UPDATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (ID),
    UNIQUE KEY UK1_URL_PATTERN (URL_PATTERN),
    CONSTRAINT FK1_CLIENT_URL_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES `security_client` (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
    
CREATE TABLE IF NOT EXISTS `security_client_password_policy` (
	ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
	CLIENT_ID BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',

	ATLEAST_ONE_UPPERCASE BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Atleast one uppercase letter',
    ATLEAST_ONE_LOWERCASE BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Atleast one lowercase letter',
    ATLEAST_ONE_DIGIT BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Atleast one digit',
    ATLEAST_ONE_SPECIAL_CHAR BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Atleast one special characters',
    SPACES_ALLOWED BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Spaces are allowed',
    REGEX VARCHAR(512) DEFAULT NULL COMMENT 'Matching regular expression',
    PERCENTAGE_NAME_MATCH SMALLINT UNSIGNED DEFAULT NULL COMMENT 'Percent that first and last name matching',
	PASS_EXPIRY_IN_DAYS SMALLINT UNSIGNED DEFAULT NULL COMMENT 'Expiry of password in days',
	PASS_EXPIRY_WARN_IN_DAYS SMALLINT UNSIGNED DEFAULT NULL COMMENT 'Password expiration warning in days',
	PASS_MIN_LENGTH SMALLINT UNSIGNED DEFAULT NULL COMMENT 'Minimum Length for the password',
	PASS_MAX_LENGTH SMALLINT UNSIGNED DEFAULT NULL COMMENT 'Maximum Length for the password', 
	NO_FAILED_ATTEMPTS SMALLINT UNSIGNED DEFAULT NULL COMMENT 'No of continuous attempts of authentication with wrong password',
	PASS_HISTORY_COUNT SMALLINT UNSIGNED DEFAULT NULL COMMENT 'Remember how many passwords',

	CREATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
	CREATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
	UPDATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
	UPDATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
	PRIMARY KEY (ID),
	UNIQUE KEY UK1_CLIENT_PWD_POL_ID (CLIENT_ID),
	CONSTRAINT FK1_CLIENT_PWD_POL_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES `security_client` (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `security_client_manage` (

	ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
	CLIENT_ID BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
    MANAGE_CLIENT_ID BIGINT UNSIGNED NOT NULL COMMENT 'Client ID that manages this client',
    
    PRIMARY KEY (ID),
    CONSTRAINT FK1_CLIENT_MANAGE_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES `security_client` (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK1_CLIENT_MANAGE_MNG_CLIENT_ID FOREIGN KEY (MANAGE_CLIENT_ID) REFERENCES `security_client` (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

/* User */

CREATE TABLE IF NOT EXISTS `security_user` (
	ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    CLIENT_ID BIGINT UNSIGNED NOT NULL COMMENT 'Client ID for which this user belongs to',
    USER_NAME CHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'User Name to login',
    EMAIL_ID VARCHAR(320) NOT NULL DEFAULT 'NONE' COMMENT 'Email ID to login',
    PHONE_NUMBER CHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'Phone Number to login',
    FIRST_NAME VARCHAR(128) DEFAULT NULL COMMENT 'First name',
    LAST_NAME VARCHAR(128) DEFAULT NULL COMMENT 'Last name',
    MIDDLE_NAME VARCHAR(128) DEFAULT NULL COMMENT 'Middle name',
    LOCALE_CODE VARCHAR(10) DEFAULT 'en-US' COMMENT 'User\'s Locale',
	PASSWORD VARCHAR(512) DEFAULT NULL COMMENT 'Password message digested string',
    PASSWORD_HASHED BOOLEAN DEFAULT 1 COMMENT 'Password stored is hashed or not',
    
	ACCOUNT_NON_EXPIRED BOOLEAN NOT NULL DEFAULT 1 COMMENT 'If false, means user is expired',
	ACCOUNT_NON_LOCKED BOOLEAN NOT NULL DEFAULT 1 COMMENT 'If false, means user is locked', 
	CREDENTIALS_NON_EXPIRED BOOLEAN NOT NULL DEFAULT 1 COMMENT 'If flase, password is expired', 
	
    NO_FAILED_ATTEMPT SMALLINT DEFAULT 0 COMMENT 'No of failed attempts', 
	STATUS_CODE ENUM('ACTIVE','INACTIVE','DELETED','LOCKED', 'PASSWORD_EXPIRED') DEFAULT 'ACTIVE' COMMENT 'Status of the user',
    
	CREATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
	CREATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
	UPDATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
	UPDATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    
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

INSERT IGNORE INTO `security_user` (CLIENT_ID, USER_NAME, FIRST_NAME, LAST_NAME, PASSWORD, PASSWORD_HASHED) values
	(@v_client_system, 'sysadmin', 'ADMIN', 'System', 'fincity@123', false);
    
SELECT ID FROM `security_user` WHERE USER_NAME = 'sysadmin' LIMIT 1 INTO @v_user_sysadmin;

CREATE TABLE IF NOT EXISTS `security_past_passwords` (
	ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
	USER_ID BIGINT UNSIGNED NOT NULL COMMENT 'User ID',
	PASSWORD VARCHAR(512) DEFAULT NULL COMMENT 'Password message digested string', 
    PASSWORD_HASHED BOOLEAN DEFAULT 1 COMMENT 'Password stored is hashed or not',
	CREATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
	CREATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
	
	PRIMARY KEY (ID),
	CONSTRAINT FK1_PAST_PASSWORD_USER_ID FOREIGN KEY (USER_ID) REFERENCES `security_user` (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_past_passwords` (USER_ID, PASSWORD, PASSWORD_HASHED) VALUES
	(@v_user_sysadmin, 'fincity@123', false);
    
/* Role */

CREATE TABLE IF NOT EXISTS `security_role` (
	ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
	
    CLIENT_ID BIGINT UNSIGNED NOT NULL COMMENT 'Client ID for which this role belongs to',
	NAME VARCHAR(256) NOT NULL COMMENT 'Name of the role',
    DESCRIPTION TEXT DEFAULT NULL COMMENT 'Description of the role',
	
	CREATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
	CREATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
	UPDATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
	UPDATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
	
	PRIMARY KEY (ID),
    UNIQUE KEY UK1_ROLE_NAME (NAME),
    CONSTRAINT FK1_ROLE_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES security_client (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_role` (CLIENT_ID, NAME, DESCRIPTION) VALUES
	(@v_client_system, 'Client Manager', 'Role to hold client operations permissions'),
    (@v_client_system, 'User Manager', 'Role to hold user operations permissions'),
    (@v_client_system, 'Package Manager', 'Role to hold package operations permissions'),
    (@v_client_system, 'Role Manager', 'Role to hold role operations permissions'),
    (@v_client_system, 'Permission Manager', 'Role to hold permission operations permissions'),
    (@v_client_system, 'Client Type Manager', 'Role to hold client type operations permissions'),
    (@v_client_system, 'Client Update Manager', 'Role to hold client data update operations permissions');
    
SELECT ID from `security_role` WHERE NAME = 'Client Manager' LIMIT 1 INTO @v_role_client;
SELECT ID from `security_role` WHERE NAME = 'User Manager' LIMIT 1 INTO @v_role_user;
SELECT ID from `security_role` WHERE NAME = 'Package Manager' LIMIT 1 INTO @v_role_package;
SELECT ID from `security_role` WHERE NAME = 'Role Manager' LIMIT 1 INTO @v_role_role;
SELECT ID from `security_role` WHERE NAME = 'Permission Manager' LIMIT 1 INTO @v_role_permission;
SELECT ID from `security_role` WHERE NAME = 'Client Type Manager' LIMIT 1 INTO @v_role_client_type;
SELECT ID from `security_role` WHERE NAME = 'Client Update Manager' LIMIT 1 INTO @v_role_client_update;

/* Permission */

CREATE TABLE IF NOT EXISTS `security_permission` (
	ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
	
    CLIENT_ID BIGINT UNSIGNED NOT NULL COMMENT 'Client ID for which this permission belongs to',
	NAME VARCHAR(256) NOT NULL COMMENT 'Name of the permission',
    DESCRIPTION TEXT DEFAULT NULL COMMENT 'Description of the permission',
	
	CREATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
	CREATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
	UPDATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
	UPDATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
	
	PRIMARY KEY (ID),
    UNIQUE KEY UK1_PERMISSION_NAME (NAME),
    CONSTRAINT FK1_PERMISSION_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES security_client (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_permission` (CLIENT_ID, NAME, DESCRIPTION) VALUES
	(@v_client_system, 'Client CREATE', 'Client create'),
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
    (@v_client_system, 'ASSIGN Permission To Role', 'Assign permission to user'),
	(@v_client_system, 'Permission CREATE', 'Permission create'),
	(@v_client_system, 'Permission READ', 'Permission read'),
	(@v_client_system, 'Permission UPDATE', 'Permission update'),
	(@v_client_system, 'Permission DELETE', 'Permission delete'),
    (@v_client_system, 'Client Type CREATE', 'Client type create'),
	(@v_client_system, 'Client Type READ', 'Client type read'),
	(@v_client_system, 'Client Type UPDATE', 'Client type update'),
	(@v_client_system, 'Client Type DELETE', 'Client type delete');

CREATE TABLE IF NOT EXISTS `security_role_permission` (
	ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
	
    ROLE_ID BIGINT UNSIGNED NOT NULL COMMENT 'Role ID',
	PERMISSION_ID BIGINT UNSIGNED NOT NULL COMMENT 'Premission ID',
	
	PRIMARY KEY (ID),
    UNIQUE KEY UK1_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID),
    CONSTRAINT FK1_ROLE_PERM_ROLE_ID FOREIGN KEY (ROLE_ID) REFERENCES security_role (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK2_ROLE_PERM_PERMISSION_ID FOREIGN KEY (PERMISSION_ID) REFERENCES security_permission (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_role_permission` (ROLE_ID, PERMISSION_ID) VALUES
	(@v_role_client, (SELECT ID FROM `security_permission` WHERE NAME = 'Client CREATE' LIMIT 1)),
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
    
CREATE TABLE IF NOT EXISTS `security_user_role_permission` (
	ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
	
    USER_ID BIGINT UNSIGNED NOT NULL COMMENT 'User ID',
    ROLE_ID BIGINT UNSIGNED DEFAULT NULL COMMENT 'Role ID',
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

INSERT IGNORE INTO `security_user_role_permission` (USER_ID, ROLE_ID) VALUES
	(@v_user_sysadmin, @v_role_client),
    (@v_user_sysadmin, @v_role_client_type),
    (@v_user_sysadmin, @v_role_user),
    (@v_user_sysadmin, @v_role_permission),
    (@v_user_sysadmin, @v_role_role),
    (@v_user_sysadmin, @v_role_package);

/* Package */

CREATE TABLE IF NOT EXISTS `security_package` (
	ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
	
    CODE CHAR(8) NOT NULL COMMENT 'Package code',
	NAME VARCHAR(256) NOT NULL COMMENT 'Name of the package',
    DESCRIPTION TEXT DEFAULT NULL COMMENT 'Description of the package',
    BASE BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Indicator if this package is for every client',
	
	CREATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
	CREATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
	UPDATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
	UPDATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
	
	PRIMARY KEY (ID),
    UNIQUE KEY UK1_PACKAGE_CODE (CODE),
    UNIQUE KEY UK2_PACKAGE_NAME (NAME)
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_package` (CODE, NAME, DESCRIPTION, BASE) VALUES
	('CLIENT', 'Client Management', 'Client management roles and permissions will be part of this package', FALSE),
    ('CLIUPD', 'Client Update', 'Client management roles and permissions will be part of this package', TRUE),
    ('CLITYP', 'Client Type Management', 'Client management roles and permissions will be part of this package', FALSE),
    ('USER', 'User Management', 'User management roles and permissions will be part of this package', TRUE),
    ('PACKAGE', 'Package Management', 'Package management roles and permissions will be part of this package', FALSE),
    ('ROLE', 'Role Management', 'Role management roles and permissions will be part of this package', TRUE),
    ('PERMISS', 'Permission Management', 'Permission management roles and permissions will be part of this package', FALSE);

SELECT ID from `security_package` WHERE CODE = 'CLIENT' LIMIT 1 INTO @v_package_client;
SELECT ID from `security_package` WHERE CODE = 'CLIUPD' LIMIT 1 INTO @v_package_client_update;
SELECT ID from `security_package` WHERE CODE = 'CLITYP' LIMIT 1 INTO @v_package_client_type;
SELECT ID from `security_package` WHERE CODE = 'USER' LIMIT 1 INTO @v_package_user;
SELECT ID from `security_package` WHERE CODE = 'PACKAGE' LIMIT 1 INTO @v_package_package;
SELECT ID from `security_package` WHERE CODE = 'ROLE' LIMIT 1 INTO @v_package_role;
SELECT ID from `security_package` WHERE CODE = 'PERMISS' LIMIT 1 INTO @v_package_permission;

CREATE TABLE IF NOT EXISTS `security_package_role` (
	ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
	
    PACKAGE_ID BIGINT UNSIGNED NOT NULL COMMENT 'Package ID',
    ROLE_ID BIGINT UNSIGNED NOT NULL COMMENT 'Role ID',
	
	PRIMARY KEY (ID),
    UNIQUE KEY UK1_PACKAGE_ROLE (ROLE_ID, PACKAGE_ID),
    CONSTRAINT FK1_PACKAGE_ROLE_ROLE_ID FOREIGN KEY (ROLE_ID) REFERENCES security_role (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK2_PACKAGE_ROLE_PACKAGE_ID FOREIGN KEY (PACKAGE_ID) REFERENCES security_package (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_package_role` (ROLE_ID, PACKAGE_ID) VALUES
	(@v_role_client,  @v_package_client),
    (@v_role_client_type,  @v_package_client_type),
    (@v_role_client_update,  @v_package_client_update),
    (@v_role_user, @v_package_user),
    (@v_role_package, @v_package_package),
    (@v_role_role, @v_package_role),
    (@v_role_permission, @v_package_permission);


CREATE TABLE IF NOT EXISTS `security_client_package` (
	ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
	
    CLIENT_ID BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
    PACKAGE_ID BIGINT UNSIGNED NOT NULL COMMENT 'Package ID',
    
	PRIMARY KEY (ID),
    UNIQUE KEY UK1_CLIENT_PACKAGE (CLIENT_ID, PACKAGE_ID),
    CONSTRAINT FK1_CLIENT_PACKAGE_CLIENT_ID FOREIGN KEY (CLIENT_ID) REFERENCES security_client (ID) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT FK2_CLIENT_PACKAGE_PACKAGE_ID FOREIGN KEY (PACKAGE_ID) REFERENCES security_package (ID) ON DELETE RESTRICT ON UPDATE RESTRICT
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO `security_client_package` (CLIENT_ID, PACKAGE_ID) VALUES
	(@v_client_system, @v_package_client),
    (@v_client_system, @v_package_client_type),
    (@v_client_system, @v_package_user),
    (@v_client_system, @v_package_package),
    (@v_client_system, @v_package_role),
    (@v_client_system, @v_package_permission);

CREATE TABLE IF NOT EXISTS `security_user_token` (
	ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
	USER_ID BIGINT UNSIGNED NOT NULL COMMENT 'User id',	
	TOKEN VARCHAR(512) NOT NULL COMMENT 'JWT token',
    PART_TOKEN CHAR(50) NOT NULL COMMENT 'Part of token to index',
	EXPIRES_AT TIMESTAMP NOT NULL DEFAULT '2022-01-01 01:01:01.000001' COMMENT 'When the token expires',
	IP_ADDRESS VARCHAR(50) NOT NULL COMMENT 'User IP from where he logged in',

	CREATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
	CREATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

	PRIMARY KEY (ID),
    INDEX (PART_TOKEN),
	CONSTRAINT FK1_USER_TOKEN_USER_ID FOREIGN KEY (USER_ID) REFERENCES `security_user` (ID) ON DELETE RESTRICT ON UPDATE RESTRICT	
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `security_sox_log`(

	ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    
    OBJECT_ID BIGINT UNSIGNED NOT NULL COMMENT 'ID of the object where the change is happening',
    OBJECT_NAME ENUM('USER', 'ROLE', 'PERMISSION', 'PACKAGE', 'CLIENT', 'CLIENT_TYPE') NOT NULL COMMENT 'Operation on the object',
    ACTION_NAME ENUM('CREATE', 'UPDATE', 'DELETE', 'READ', 'ASSIGN', 'UNASSIGN', 'OTHER', 'LOGIN') NOT NULL COMMENT 'Log action name',
    DESCRIPTION VARCHAR(1048) NOT NULL COMMENT 'Log description',	

	CREATED_BY BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
	CREATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    
    PRIMARY KEY (ID),
    INDEX (CREATED_AT DESC),
    INDEX (OBJECT_NAME, ACTION_NAME)
)
ENGINE = INNODB
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;