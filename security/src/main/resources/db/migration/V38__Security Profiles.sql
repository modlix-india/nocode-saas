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

CREATE TABLE `security_profile` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client ID for which this profile belongs to',
  `NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Name of the profile',
  `APP_ID` bigint unsigned NOT NULL,
  `DESCRIPTION` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Description of the profile',
  `ROOT_PROFILE_ID` bigint unsigned DEFAULT NULL COMMENT 'Profile ID to which the user is assigned',
  `ARRANGEMENT` json DEFAULT NULL COMMENT 'Arrangement of the profile',

  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_PROFILE_NAME_APP_ID` (`NAME`,`APP_ID`),
  KEY `FK1_PROFILE_CLIENT_ID` (`CLIENT_ID`),
  KEY `FK2_PROFILE_APP_ID` (`APP_ID`),
  CONSTRAINT `FK1_PROFILE_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_PROFILE_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK3_PROFILE_ROOT_PROFILE_ID` FOREIGN KEY (`ROOT_PROFILE_ID`) REFERENCES `security_profile` (`ID`) ON DELETE RESTRICT ON UPDATE CASCADE
) 
ENGINE=InnoDB 
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci;

-- Security V2 Role Table

CREATE TABLE `security_v2_role` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client ID for which this role belongs to',
  `NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Name of the role',
  `SHORT_NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Short name of the role',
  `DESCRIPTION` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Description of the role',
  `APP_ID` bigint unsigned DEFAULT NULL COMMENT 'App ID for which this role belongs to',

  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  KEY `FK1_V2_ROLE_CLIENT_ID` (`CLIENT_ID`),
  KEY `FK2_V2_ROLE_APP_ID` (`APP_ID`),
  CONSTRAINT `FK1_V2_ROLE_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_V2_ROLE_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Security Profile Role Table

CREATE TABLE `security_profile_role` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `PROFILE_ID` bigint unsigned NOT NULL COMMENT 'Profile ID for which this role belongs to',
  `ROLE_ID` bigint unsigned NOT NULL COMMENT 'Role ID for which this role belongs to',
  `EXCLUDE` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Flag to indicate if this role is excluded',
  
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_PROFILE_ROLE_APP_ID` (`PROFILE_ID`,`ROLE_ID`),
  KEY `FK1_PROFILE_ROLE_PROFILE_ID` (`PROFILE_ID`),
  KEY `FK2_PROFILE_ROLE_ROLE_ID` (`ROLE_ID`),
  CONSTRAINT `FK1_PROFILE_ROLE_PROFILE_ID` FOREIGN KEY (`PROFILE_ID`) REFERENCES `security_profile` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `FK2_PROFILE_ROLE_ROLE_ID` FOREIGN KEY (`ROLE_ID`) REFERENCES `security_v2_role` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Security Profile Client restriction Table

CREATE TABLE `security_profile_client_restriction` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `PROFILE_ID` bigint unsigned NOT NULL COMMENT 'Profile ID for which this restriction belongs to',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client ID for which this restriction belongs to',
  
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_PROFILE_CLIENT_APP_ID` (`PROFILE_ID`,`CLIENT_ID`),
  KEY `FK1_PROFILE_CLIENT_RESTRICTION_PROFILE_ID` (`PROFILE_ID`),
  CONSTRAINT `FK1_PROFILE_CLIENT_RESTRICTION_PROFILE_ID` FOREIGN KEY (`PROFILE_ID`) REFERENCES `security_profile` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `FK3_PROFILE_CLIENT_RESTRICTION_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT
)
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Security Profile User Table

CREATE TABLE `security_profile_user` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `PROFILE_ID` bigint unsigned NOT NULL COMMENT 'Profile ID for which this user belongs to',
  `USER_ID` bigint unsigned NOT NULL COMMENT 'User ID for which this profile belongs to',

  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_PROFILE_USER_PROFILE_ID_USER_ID` (`PROFILE_ID`,`USER_ID`),
  KEY `FK1_PROFILE_USER_PROFILE_ID` (`PROFILE_ID`),
  KEY `FK2_PROFILE_USER_USER_ID` (`USER_ID`),
  CONSTRAINT `FK1_PROFILE_USER_PROFILE_ID` FOREIGN KEY (`PROFILE_ID`) REFERENCES `security_profile` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `FK2_PROFILE_USER_USER_ID` FOREIGN KEY (`USER_ID`) REFERENCES `security_user` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Security Role Role Table

CREATE TABLE `security_v2_role_role`     (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `ROLE_ID` bigint unsigned NOT NULL COMMENT 'Role ID in which the sub role is nested',
  `SUB_ROLE_ID` bigint unsigned NOT NULL COMMENT 'Sub Role ID for which this role belongs to',

  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_ROLE_ROLE_ROLE_ID_SUB_ROLE_ID` (`ROLE_ID`,`SUB_ROLE_ID`),
  KEY `FK1_ROLE_ROLE_ROLE_ID` (`ROLE_ID`),
  KEY `FK2_ROLE_ROLE_SUB_ROLE_ID` (`SUB_ROLE_ID`),
  CONSTRAINT `FK1_ROLE_ROLE_ROLE_ID` FOREIGN KEY (`ROLE_ID`) REFERENCES `security_v2_role` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `FK2_ROLE_ROLE_SUB_ROLE_ID` FOREIGN KEY (`SUB_ROLE_ID`) REFERENCES `security_v2_role` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Security Role Permission Table

CREATE TABLE `security_v2_role_permission` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `ROLE_ID` bigint unsigned NOT NULL COMMENT 'Role ID for the permissions contained',
  `PERMISSION_ID` bigint unsigned NOT NULL COMMENT 'Permission ID for which this role has permission',

  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_ROLE_PERMISSION_ROLE_ID_PERMISSION_ID` (`ROLE_ID`,`PERMISSION_ID`),
  KEY `FK1_ROLE_PERMISSION_ROLE_ID` (`ROLE_ID`),
  KEY `FK2_ROLE_PERMISSION_PERMISSION_ID` (`PERMISSION_ID`),
  CONSTRAINT `FK1_ROLE_PERMISSION_ROLE_ID` FOREIGN KEY (`ROLE_ID`) REFERENCES `security_v2_role` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `FK2_ROLE_PERMISSION_PERMISSION_ID` FOREIGN KEY (`PERMISSION_ID`) REFERENCES `security_permission` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Security V2 User Role Table

CREATE TABLE `security_v2_user_role` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `USER_ID` bigint unsigned NOT NULL COMMENT 'User ID to which this role is assigned',
  `ROLE_ID` bigint unsigned NOT NULL COMMENT 'Role ID assigned to the user',

  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_USER_ROLE_USER_ID_ROLE_ID` (`USER_ID`,`ROLE_ID`),
  KEY `FK1_USER_ROLE_V2_USER_ID` (`USER_ID`),
  KEY `FK2_USER_ROLE_V2_ROLE_ID` (`ROLE_ID`),
  CONSTRAINT `FK1_USER_ROLE_V2_USER_ID` FOREIGN KEY (`USER_ID`) REFERENCES `security_user` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `FK2_USER_ROLE_V2_ROLE_ID` FOREIGN KEY (`ROLE_ID`) REFERENCES `security_v2_role` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Security Department Table
-- Security Department Table with Parent Department Reference
CREATE TABLE `security_department` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client ID for which this department belongs to',
  `NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Name of the department',
  `DESCRIPTION` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Description of the department',
  `PARENT_DEPARTMENT_ID` bigint unsigned DEFAULT NULL COMMENT 'Parent department for hierarchical structure',
  
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  
  PRIMARY KEY (`ID`),
  KEY `FK1_DEPARTMENT_CLIENT_ID` (`CLIENT_ID`),
  KEY `FK2_DEPARTMENT_PARENT_ID` (`PARENT_DEPARTMENT_ID`),
  
  CONSTRAINT `FK1_DEPARTMENT_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) 
    REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK2_DEPARTMENT_PARENT_ID` FOREIGN KEY (`PARENT_DEPARTMENT_ID`) 
    REFERENCES `security_department` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Security Designation Table with Reporting & Hierarchy
CREATE TABLE `security_designation` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client ID for which this designation belongs to',
  `NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Name of the designation',
  `DESCRIPTION` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Description of the designation',
  `DEPARTMENT_ID` bigint unsigned DEFAULT NULL COMMENT 'Department ID for which this designation belongs to',
  `PARENT_DESIGNATION_ID` bigint unsigned DEFAULT NULL COMMENT 'Parent designation for hierarchy',
  `NEXT_DESIGNATION_ID` bigint unsigned DEFAULT NULL COMMENT 'Next designation in the hierarchy',
  `PROFILE_ID` bigint unsigned DEFAULT NULL COMMENT 'Profile ID for which this designation belongs to',

  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Adding designation and reporting to user in security_user table along with foreign key constraints

ALTER TABLE `security_user`
ADD COLUMN `DESIGNATION_ID` bigint unsigned DEFAULT NULL COMMENT 'Designation ID for which this user belongs to' AFTER `ID`,
ADD COLUMN `REPORTING_TO` bigint unsigned DEFAULT NULL COMMENT 'Reporting to ID for which this user belongs to' AFTER `DESIGNATION_ID`;

ALTER TABLE `security_user`
ADD CONSTRAINT `FK1_USER_DESIGNATION_ID` FOREIGN KEY (`DESIGNATION_ID`) REFERENCES `security_designation` (`ID`) ON DELETE SET NULL ON UPDATE RESTRICT,
ADD CONSTRAINT `FK2_USER_REPORTING_TO_ID` FOREIGN KEY (`REPORTING_TO`) REFERENCES `security_user` (`ID`) ON DELETE SET NULL ON UPDATE RESTRICT;

-- App registration related tables

CREATE TABLE `security_app_reg_profile_restriction` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client ID',
  `CLIENT_TYPE` char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BUS' COMMENT 'Client type',
  `APP_ID` bigint unsigned NOT NULL COMMENT 'App ID',
  `LEVEL` enum('CLIENT','CUSTOMER','CONSUMER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CLIENT' COMMENT 'Access level',
  `BUSINESS_TYPE` char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMMON' COMMENT 'Business type',

  `PROFILE_ID` bigint unsigned NOT NULL COMMENT 'Profile ID',
  
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  
  PRIMARY KEY (`ID`),
  UNIQUE KEY `CLIENT_ID` (`CLIENT_ID`,`APP_ID`,`PROFILE_ID`,`CLIENT_TYPE`,`LEVEL`,`BUSINESS_TYPE`),
  KEY `FK2_APP_REG_PROFILE_APP_ID` (`APP_ID`),
  KEY `FK3_APP_REG_PROFILE_PROFILE_ID` (`PROFILE_ID`),
  KEY `FK4_APP_REG_PROFILE_CLIENT_TYPE` (`CLIENT_TYPE`),
  CONSTRAINT `FK1_APP_REG_PROFILE_CLNT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `FK2_APP_REG_PROFILE_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `FK3_APP_REG_PROFILE_PROFILE_ID` FOREIGN KEY (`PROFILE_ID`) REFERENCES `security_profile` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `FK4_APP_REG_PROFILE_CLIENT_TYPE` FOREIGN KEY (`CLIENT_TYPE`) REFERENCES `security_client_type` (`CODE`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `security_app_reg_department` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client ID',
  `CLIENT_TYPE` char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BUS' COMMENT 'Client type',
  `APP_ID` bigint unsigned NOT NULL COMMENT 'App ID',
  `LEVEL` enum('CLIENT','CUSTOMER','CONSUMER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CLIENT' COMMENT 'Access level',
  `BUSINESS_TYPE` char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMMON' COMMENT 'Business type',

  `NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Name of the department',
  `DESCRIPTION` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Description of the department',
  `PARENT_DEPARTMENT_ID` bigint unsigned DEFAULT NULL COMMENT 'Parent department for hierarchical structure in this registration details',
  
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

  PRIMARY KEY (`ID`),
  KEY `FK1_APP_REG_DEPARTMENT_CLIENT_ID` (`CLIENT_ID`),
  KEY `FK2_APP_REG_DEPARTMENT_PARENT_ID` (`PARENT_DEPARTMENT_ID`),
  KEY `FK3_APP_REG_DEPARTMENT_APP_ID` (`APP_ID`),
  CONSTRAINT `FK1_APP_REG_DEPARTMENT_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `FK2_APP_REG_DEPARTMENT_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `FK2_APP_REG_DEPARTMENT_PARENT_ID` FOREIGN KEY (`PARENT_DEPARTMENT_ID`) REFERENCES `security_app_reg_department` (`ID`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Security App Reg User RoleV2 Table

CREATE TABLE `security_app_reg_user_role_v2` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client ID',
  `CLIENT_TYPE` char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BUS' COMMENT 'Client type',
  `APP_ID` bigint unsigned NOT NULL COMMENT 'App ID',
  `LEVEL` enum('CLIENT','CUSTOMER','CONSUMER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CLIENT' COMMENT 'Access level',
  `BUSINESS_TYPE` char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMMON' COMMENT 'Business type',

  `ROLE_ID` bigint unsigned NOT NULL COMMENT 'Role ID',

  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

  PRIMARY KEY (`ID`),
  KEY `FK1_APP_REG_USER_ROLE_CLIENT_ID` (`CLIENT_ID`),
  KEY `FK2_APP_REG_USER_ROLE_ROLE_V2_ID` (`ROLE_ID`),
  KEY `FK3_APP_REG_USER_ROLE_APP_ID` (`APP_ID`),
  CONSTRAINT `FK1_APP_REG_USER_ROLE_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `FK2_APP_REG_USER_ROLE_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `FK2_APP_REG_USER_ROLE_ROLE_V2_ID` FOREIGN KEY (`ROLE_ID`) REFERENCES `security_v2_role` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `security_app_reg_designation` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client ID',
  `CLIENT_TYPE` char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BUS' COMMENT 'Client type',
  `APP_ID` bigint unsigned NOT NULL COMMENT 'App ID',
  `LEVEL` enum('CLIENT','CUSTOMER','CONSUMER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CLIENT' COMMENT 'Access level',
  `BUSINESS_TYPE` char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMMON' COMMENT 'Business type',

  `NAME` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Name of the designation',
  `DESCRIPTION` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Description of the designation',
  `DEPARTMENT_ID` bigint unsigned NOT NULL COMMENT 'Department ID for which this designation belongs to',
  `PARENT_DESIGNATION_ID` bigint unsigned DEFAULT NULL COMMENT 'Parent designation for hierarchy',
  `NEXT_DESIGNATION_ID` bigint unsigned DEFAULT NULL COMMENT 'Next designation in the hierarchy',

  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `security_app_reg_user_profile` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client ID',
  `CLIENT_TYPE` char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BUS' COMMENT 'Client type',
  `APP_ID` bigint unsigned NOT NULL COMMENT 'App ID',
  `LEVEL` enum('CLIENT','CUSTOMER','CONSUMER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CLIENT' COMMENT 'Access level',
  `BUSINESS_TYPE` char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMMON' COMMENT 'Business type',

  `PROFILE_ID` bigint unsigned NOT NULL COMMENT 'Profile ID',
  
  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  
  PRIMARY KEY (`ID`),
  UNIQUE KEY `CLIENT_ID` (`CLIENT_ID`,`APP_ID`,`PROFILE_ID`,`CLIENT_TYPE`,`LEVEL`,`BUSINESS_TYPE`),
  KEY `FK2_APP_REG_USER_PROFILE_APP_ID` (`APP_ID`),
  KEY `FK3_APP_REG_USER_PROFILE_PROFILE_ID` (`PROFILE_ID`),
  KEY `FK4_APP_REG_USER_PROFILE_CLIENT_TYPE` (`CLIENT_TYPE`),
  CONSTRAINT `FK1_APP_REG_USER_PROFILE_CLNT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `FK2_APP_REG_USER_PROFILE_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `FK3_APP_REG_USER_PROFILE_PROFILE_ID` FOREIGN KEY (`PROFILE_ID`) REFERENCES `security_profile` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `FK4_APP_REG_USER_PROFILE_CLIENT_TYPE` FOREIGN KEY (`CLIENT_TYPE`) REFERENCES `security_client_type` (`CODE`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `security_app_reg_user_designation` (
  `ID` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` bigint unsigned NOT NULL COMMENT 'Client ID',
  `CLIENT_TYPE` char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BUS' COMMENT 'Client type',
  `APP_ID` bigint unsigned NOT NULL COMMENT 'App ID',
  `LEVEL` enum('CLIENT','CUSTOMER','CONSUMER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CLIENT' COMMENT 'Access level',
  `BUSINESS_TYPE` char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMMON' COMMENT 'Business type',

  `DESIGNATION_ID` bigint unsigned NOT NULL COMMENT 'Designation ID',

  `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

  PRIMARY KEY (`ID`),
  KEY `FK1_APP_REG_USER_DESIGNATION_CLIENT_ID` (`CLIENT_ID`),
  KEY `FK2_APP_REG_USER_DESIGNATION_DESIGNATION_ID` (`DESIGNATION_ID`),
  KEY `FK3_APP_REG_USER_DESIGNATION_APP_ID` (`APP_ID`),
  CONSTRAINT `FK1_APP_REG_USER_DESIGNATION_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `FK2_APP_REG_USER_DESIGNATION_APP_ID` FOREIGN KEY (`APP_ID`) REFERENCES `security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `FK2_APP_REG_USER_DESIGNATION_DESIGNATION_ID` FOREIGN KEY (`DESIGNATION_ID`) REFERENCES `security_app_reg_designation` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Adding profile related permissions and roles

SELECT ID from `security_client` WHERE CODE = 'SYSTEM' LIMIT 1 INTO @v_client_system;

-- DELETING any profile related permissions;

DELETE FROM security.security_permission where NAME like 'Profile%';

-- Inserting old roles into v2 role table

INSERT INTO `security_v2_role` (`CLIENT_ID`, `NAME`, `APP_ID`, `DESCRIPTION`)
  SELECT `CLIENT_ID`, `NAME`, `APP_ID`, `DESCRIPTION` FROM `security_role`;

-- Inserting old permissions into v2 role table to create a new role for each permission

INSERT INTO `security_v2_role` (`CLIENT_ID`, `NAME`, `SHORT_NAME`, `DESCRIPTION`)
  SELECT 
    CLIENT_ID, NAME, concat(left(short_name,1), lower(substr(short_name, 2))) as SHORT_NAME, DESCRIPTION
FROM
    (SELECT CLIENT_ID, NAME,
          IF(SUBSTRING_INDEX(`NAME`, ' ', 1) = 'ASSIGN', 'ASSIGN', SUBSTRING_INDEX(`NAME`, ' ', - 1)) AS short_name,
          DESCRIPTION FROM security.security_permission) nt;

-- Creating the relation between newly created permission roles with existing permissions

INSERT INTO `security_v2_role_permission` (ROLE_ID, PERMISSION_ID)
    select r.id role_id, p.id permission_id from security_v2_role r
	  left join security_permission p on r.name = p.name where p.id is not null;

INSERT INTO `security_v2_role_role` (ROLE_ID, SUB_ROLE_ID)
    select vr.id, vr2.id from security_role_permission rp 
    left join security_role r on r.id = rp.role_id
    left join security_v2_role vr on vr.name = r.name
    left join security_permission p on p.id = rp.PERMISSION_ID
    left join security_v2_role vr2 on vr2.name = p.name;

INSERT IGNORE INTO `security_permission` (CLIENT_ID, NAME, DESCRIPTION) VALUES
  (@v_client_system, 'Profile CREATE', 'Profile create'),
	(@v_client_system, 'Profile READ', 'Profile read'),
	(@v_client_system, 'Profile UPDATE', 'Profile update'),
	(@v_client_system, 'Profile DELETE', 'Profile delete');

INSERT IGNORE INTO `security_v2_role` (CLIENT_ID, NAME, SHORT_NAME, DESCRIPTION) VALUES
  (@v_client_system, 'Profile CREATE', 'Create', 'Profile create'),
	(@v_client_system, 'Profile READ', 'Read', 'Profile read'),
	(@v_client_system, 'Profile UPDATE', 'Update', 'Profile update'),
	(@v_client_system, 'Profile DELETE', 'Delete', 'Profile delete'),
  (@v_client_system, 'Profile Manager', 'Manager', 'Profile manager'),
  (@v_client_system, 'Owner', 'Owner', 'Owner');

SELECT ID from `security_v2_role` WHERE NAME = 'Profile Manager' LIMIT 1 INTO @v_role_profile_manager;

SELECT ID from `security_v2_role` WHERE NAME = 'Profile CREATE' LIMIT 1 INTO @v_role_profile_create;
SELECT ID from `security_v2_role` WHERE NAME = 'Profile READ' LIMIT 1 INTO @v_role_profile_read;
SELECT ID from `security_v2_role` WHERE NAME = 'Profile UPDATE' LIMIT 1 INTO @v_role_profile_update;
SELECT ID from `security_v2_role` WHERE NAME = 'Profile DELETE' LIMIT 1 INTO @v_role_profile_delete;

INSERT IGNORE INTO `security_v2_role_permission` (ROLE_ID, PERMISSION_ID) VALUES
  (@v_role_profile_create, (SELECT ID FROM `security_permission` WHERE NAME = 'Profile CREATE' LIMIT 1)),
  (@v_role_profile_read, (SELECT ID FROM `security_permission` WHERE NAME = 'Profile READ' LIMIT 1)),
  (@v_role_profile_update, (SELECT ID FROM `security_permission` WHERE NAME = 'Profile UPDATE' LIMIT 1)),
  (@v_role_profile_delete, (SELECT ID FROM `security_permission` WHERE NAME = 'Profile DELETE' LIMIT 1));

INSERT IGNORE INTO `security_v2_role_role` (ROLE_ID, SUB_ROLE_ID) VALUES
  (@v_role_profile_manager, @v_role_profile_create),
  (@v_role_profile_manager, @v_role_profile_read),
  (@v_role_profile_manager, @v_role_profile_update),
  (@v_role_profile_manager, @v_role_profile_delete);

ALTER TABLE `security`.`security_sox_log` 
CHANGE COLUMN `OBJECT_NAME` `OBJECT_NAME` ENUM('USER', 'ROLE', 'PERMISSION', 'PACKAGE', 'CLIENT', 'CLIENT_TYPE', 'APP', 'PROFILE') CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_unicode_ci' NOT NULL COMMENT 'Operation on the object' ;

SET SQL_SAFE_UPDATES = 0;
DELETE FROM `security`.`security_v2_role` where short_name = 'Assign';
SET SQL_SAFE_UPDATES = 1;

select id from security.security_app where app_code = 'appbuilder' into @v_app_appbuilder;
select id from security.security_client where code = 'system' into @v_client_system;

delete from security.security_profile where app_id = @v_app_appbuilder;

select id from security.security_v2_role where name = 'Actions Manager' into @v_role_actions_manager;
select id from security.security_v2_role where name = 'Application Manager' into @v_role_app_manager;
select id from security.security_v2_role where name = 'Client Manager' into @v_role_client_manager;
select id from security.security_v2_role where name = 'Client Type Manager' into @v_role_client_type_manager;
select id from security.security_v2_role where name = 'Client Update Manager' into @v_role_client_update_manager;
select id from security.security_v2_role where name = 'Data Connection Manager' into @v_role_data_connection_manager;
select id from security.security_v2_role where name = 'Data Manager' into @v_role_data_manager;
select id from security.security_v2_role where name = 'EventAction Manager' into @v_role_eventaction_manager;
select id from security.security_v2_role where name = 'EventDefinition Manager' into @v_role_eventdefinition_manager;
select id from security.security_v2_role where name = 'Files Manager' into @v_role_files_manager;
select id from security.security_v2_role where name = 'Function Manager' into @v_role_function_manager;
select id from security.security_v2_role where name = 'Integration Manager' into @v_role_integration_manager;
select id from security.security_v2_role where name = 'Owner' into @v_role_owner;
select id from security.security_v2_role where name = 'Package Manager' into @v_role_package_manager;
select id from security.security_v2_role where name = 'Page Manager' into @v_role_page_manager;
select id from security.security_v2_role where name = 'Permission Manager' into @v_role_permission_manager;
select id from security.security_v2_role where name = 'Personalization Manager' into @v_role_personalization_manager;
select id from security.security_v2_role where name = 'Profile Manager' into @v_role_profile_manager;
select id from security.security_v2_role where name = 'Role Manager' into @v_role_role_manager;
select id from security.security_v2_role where name = 'Schema Manager' into @v_role_schema_manager;
select id from security.security_v2_role where name = 'Style Manager' into @v_role_style_manager;
select id from security.security_v2_role where name = 'System Application Manager' into @v_role_system_application_manager;
select id from security.security_v2_role where name = 'Template Manager' into @v_role_template_manager;
select id from security.security_v2_role where name = 'Theme Manager' into @v_role_theme_manager;
select id from security.security_v2_role where name = 'Transport Manager' into @v_role_transport_manager;
select id from security.security_v2_role where name = 'User Manager' into @v_role_user_manager;
select id from security.security_v2_role where name = 'Workflow Manager' into @v_role_workflow_manager;

insert into security.security_profile(client_id, app_id, name, description, arrangement) value
(@v_client_system, @v_app_appbuilder, 'Appbuilder Owner', 'Owner of App Builder', 

JSON_OBJECT(
  
  "r1", JSON_OBJECT( "roleId", @v_role_actions_manager),
  "r2", JSON_OBJECT( "roleId", @v_role_app_manager),
  "r3", JSON_OBJECT( "roleId", @v_role_client_manager),
  "r4", JSON_OBJECT( "roleId", @v_role_client_type_manager),
  "r5", JSON_OBJECT( "roleId", @v_role_client_update_manager),
  "r6", JSON_OBJECT( "roleId", @v_role_data_connection_manager),
  "r7", JSON_OBJECT( "roleId", @v_role_data_manager),
  "r8", JSON_OBJECT( "roleId", @v_role_eventaction_manager),
  "r9", JSON_OBJECT( "roleId", @v_role_eventdefinition_manager),
  "r10", JSON_OBJECT( "roleId", @v_role_files_manager),
  "r11", JSON_OBJECT( "roleId", @v_role_function_manager),
  "r12", JSON_OBJECT( "roleId", @v_role_integration_manager),
  "r13", JSON_OBJECT( "roleId", @v_role_owner),
  "r14", JSON_OBJECT( "roleId", @v_role_package_manager),
  "r15", JSON_OBJECT( "roleId", @v_role_page_manager),
  "r16", JSON_OBJECT( "roleId", @v_role_permission_manager),
  "r17", JSON_OBJECT( "roleId", @v_role_personalization_manager),
  "r18", JSON_OBJECT( "roleId", @v_role_profile_manager),
  "r19", JSON_OBJECT( "roleId", @v_role_role_manager),
  "r20", JSON_OBJECT( "roleId", @v_role_schema_manager),
  "r21", JSON_OBJECT( "roleId", @v_role_style_manager),
  "r22", JSON_OBJECT( "roleId", @v_role_system_application_manager),
  "r23", JSON_OBJECT( "roleId", @v_role_template_manager),
  "r24", JSON_OBJECT( "roleId", @v_role_theme_manager),
  "r25", JSON_OBJECT( "roleId", @v_role_transport_manager),
  "r26", JSON_OBJECT( "roleId", @v_role_user_manager),
  "r27", JSON_OBJECT( "roleId", @v_role_workflow_manager)

)

);

select id from security.security_user where USER_NAME = 'sysadmin' into @v_user_sysadmin;
select id from security.security_profile where name = 'Appbuilder Owner' into @v_profile_appbuilder_owner;

insert into security.security_profile_role(profile_id, role_id) values
(@v_profile_appbuilder_owner, @v_role_actions_manager),
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

insert into security.security_profile_user(profile_id, user_id) values
(@v_profile_appbuilder_owner, @v_user_sysadmin);