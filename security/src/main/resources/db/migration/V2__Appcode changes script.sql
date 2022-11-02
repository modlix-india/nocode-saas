ALTER TABLE `security`.`security_client_url` 
ADD COLUMN `APP_CODE` VARCHAR(256) NOT NULL AFTER `URL_PATTERN`,
CHANGE COLUMN `URL_PATTERN` `URL_PATTERN` VARCHAR(512) NOT NULL COMMENT 'URL Pattern to identify user\'s Client ID';

ALTER TABLE `security`.`security_client_url` 
CHANGE COLUMN `APP_CODE` `APP_CODE` CHAR(64) NOT NULL;

CREATE TABLE `security`.`security_app` (
  `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
  `APP_NAME`  VARCHAR(512) NOT NULL COMMENT 'Name of the application',
  `APP_CODE` CHAR(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Code of the application',
  `APP_TYPE` ENUM('APP','SITE','POSTER') NOT NULL DEFAULT 'APP' COMMENT 'Application type',
  `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
  `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
  `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row',
  `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK1_APPCODE` (`APP_CODE`),
  KEY `FK1_APP_CLIENT_ID` (`CLIENT_ID`),
  CONSTRAINT `FK1_APP_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `security`.`security_client_url` 
ADD CONSTRAINT `FK1_CLIENT_URL_APP_CODE`
  FOREIGN KEY (APP_CODE)
  REFERENCES `security`.`security_app` (APP_CODE)
  ON DELETE RESTRICT ON UPDATE RESTRICT;

INSERT INTO `security`.`security_app` (`CLIENT_ID`, `APP_NAME`, `APP_CODE`, `APP_TYPE`) VALUES ('1', 'Under Construction', 'nothing', 'APP');
INSERT INTO `security`.`security_app` (`CLIENT_ID`, `APP_NAME`, `APP_CODE`, `APP_TYPE`) VALUES ('1', 'App Builder', 'appbuilder', 'APP');

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
CHANGE COLUMN `OBJECT_NAME` `OBJECT_NAME` ENUM('USER', 'ROLE', 'PERMISSION', 'PACKAGE', 'CLIENT', 'CLIENT_TYPE', 'APP') NOT NULL COMMENT 'Operation on the object' ;

update security.security_permission set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1) where name like 'Application %';
update security.security_permission set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1) where name like 'Function %';
update security.security_permission set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1) where name like 'Page %';
update security.security_permission set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1) where name like 'Theme %';

update security.security_role set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1) where name like 'Application %';
update security.security_role set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1) where name like 'System %';
update security.security_role set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1) where name like 'Function %';
update security.security_role set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1) where name like 'Page %';
update security.security_role set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1) where name like 'Theme %';

select id from security.security_user limit 1 into @V_SYS_USER_ID;

insert into security.security_user_role_permission (user_id, role_id) 
	select @V_SYS_USER_ID, id from security.security_role where name like 'Theme %' or name like 'Page %' or name like 'Function %' or name like 'Application %';
