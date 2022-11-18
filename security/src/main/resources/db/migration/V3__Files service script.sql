USE security;

SELECT ID from `security`.`security_client` WHERE CODE = 'SYSTEM' LIMIT 1 INTO @v_client_system;

SELECT ID from `security`.`security_app` WHERE app_code = 'appbuilder' LIMIT 1 INTO @v_app_appbuilder;

INSERT INTO `security`.`security_role` (CLIENT_ID, NAME, APP_ID, DESCRIPTION) VALUES
	(@v_client_system, 'Files Manager', @v_app_appbuilder, 'Role to hold static files operations permissions');
	
INSERT INTO `security`.`security_permission` (CLIENT_ID, NAME, APP_ID, DESCRIPTION) VALUES
	(@v_client_system, 'STATIC Files PATH', @v_app_appbuilder, 'Static files path management'),
	(@v_client_system, 'SECURED Files PATH', @v_app_appbuilder, 'Secured files path management');
	
SELECT ID from `security`.`security_role` WHERE NAME = 'Files Manager' LIMIT 1 INTO @v_role_files;

INSERT IGNORE INTO `security`.`security_role_permission` (ROLE_ID, PERMISSION_ID) VALUES
	(@v_role_files, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Static Files PATH' LIMIT 1)),
	(@v_role_files, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Secured Files PATH' LIMIT 1));
	
SELECT ID FROM `security`.`security_user` WHERE USER_NAME = 'sysadmin' LIMIT 1 INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID) VALUES
	(@v_user_sysadmin, @v_role_files);