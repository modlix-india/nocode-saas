USE security;

SELECT ID from `security`.`security_client` WHERE CODE = 'SYSTEM' LIMIT 1 INTO @v_client_system;

SELECT ID from `security`.`security_app` WHERE app_code = 'appbuilder' LIMIT 1 INTO @v_app_appbuilder;

INSERT INTO `security`.`security_role` (CLIENT_ID, NAME, APP_ID, DESCRIPTION) VALUES
	(@v_client_system, 'Data Manager', @v_app_appbuilder, 'Role to hold Data operations permissions');
	
INSERT INTO `security`.`security_permission` (CLIENT_ID, NAME, APP_ID, DESCRIPTION) VALUES
	(@v_client_system, 'Storage CREATE', @v_app_appbuilder, 'Storage create'),
	(@v_client_system, 'Storage READ', @v_app_appbuilder, 'Storage read'),
	(@v_client_system, 'Storage UPDATE', @v_app_appbuilder, 'Storage update'),
	(@v_client_system, 'Storage DELETE', @v_app_appbuilder, 'Storage delete');
	
SELECT ID from `security`.`security_role` WHERE NAME = 'Data Manager' LIMIT 1 INTO @v_role_data;

INSERT IGNORE INTO `security`.`security_role_permission` (ROLE_ID, PERMISSION_ID) VALUES
	(@v_role_data, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Storage CREATE' LIMIT 1)),
	(@v_role_data, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Storage READ' LIMIT 1)),
	(@v_role_data, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Storage UPDATE' LIMIT 1)),
	(@v_role_data, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Storage DELETE' LIMIT 1));
	
SELECT ID FROM `security`.`security_user` WHERE USER_NAME = 'sysadmin' LIMIT 1 INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID) VALUES
	(@v_user_sysadmin, @v_role_data);
	
INSERT INTO `security`.`security_role` (CLIENT_ID, NAME, APP_ID, DESCRIPTION) VALUES
	(@v_client_system, 'Data Connection Manager', @v_app_appbuilder, 'Role to hold Data connection operations permissions');
	
INSERT INTO `security`.`security_permission` (CLIENT_ID, NAME, APP_ID, DESCRIPTION) VALUES
	(@v_client_system, 'Connection CREATE', @v_app_appbuilder, 'Connection create'),
	(@v_client_system, 'Connection READ', @v_app_appbuilder, 'Connection read'),
	(@v_client_system, 'Connection UPDATE', @v_app_appbuilder, 'Connection update'),
	(@v_client_system, 'Connection DELETE', @v_app_appbuilder, 'Connection delete');
	
SELECT ID from `security`.`security_role` WHERE NAME = 'Data Connection Manager' LIMIT 1 INTO @v_role_connection;

INSERT IGNORE INTO `security`.`security_role_permission` (ROLE_ID, PERMISSION_ID) VALUES
	(@v_role_data, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Connection CREATE' LIMIT 1)),
	(@v_role_data, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Connection READ' LIMIT 1)),
	(@v_role_data, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Connection UPDATE' LIMIT 1)),
	(@v_role_data, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Connection DELETE' LIMIT 1));
	
SELECT ID FROM `security`.`security_user` WHERE USER_NAME = 'sysadmin' LIMIT 1 INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID) VALUES
	(@v_user_sysadmin, @v_role_connection);