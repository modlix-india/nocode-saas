use security;

SELECT ID from `security_client` WHERE CODE = 'SYSTEM' LIMIT 1 INTO @v_client_system;

INSERT IGNORE INTO `security_permission` (CLIENT_ID, NAME, DESCRIPTION) VALUES
    (@v_client_system, 'Style CREATE', 'Style create'),
	(@v_client_system, 'Style READ', 'Style read'),
	(@v_client_system, 'Style UPDATE', 'Style update'),
	(@v_client_system, 'Style DELETE', 'Style delete');

update security.security_permission set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1) where name like 'Style %';
	
INSERT IGNORE INTO `security_role` (CLIENT_ID, NAME, DESCRIPTION) VALUES
	(@v_client_system, 'Style Manager', 'Role to hold style operations permissions');

update security.security_role set app_id = (select id from security.security_app where app_code = 'appbuilder' limit 1) where name like 'Style %';

SELECT ID from `security_role` WHERE NAME = 'Style Manager' LIMIT 1 INTO @v_role_style;

INSERT IGNORE INTO `security_role_permission` (ROLE_ID, PERMISSION_ID) VALUES
	(@v_role_style, (SELECT ID FROM `security_permission` WHERE NAME = 'Style CREATE' LIMIT 1)),
    (@v_role_style, (SELECT ID FROM `security_permission` WHERE NAME = 'Style READ' LIMIT 1)),
	(@v_role_style, (SELECT ID FROM `security_permission` WHERE NAME = 'Style UPDATE' LIMIT 1)),
	(@v_role_style, (SELECT ID FROM `security_permission` WHERE NAME = 'Style DELETE' LIMIT 1));

INSERT IGNORE INTO `security_package` (CLIENT_ID, CODE, NAME, DESCRIPTION, BASE) VALUES
	(@v_client_system, 'STYLE', 'Style Management', 'Style management roles and permissions will be part of this package', FALSE);

SELECT ID FROM `security`.`security_user` WHERE USER_NAME = 'sysadmin' LIMIT 1 INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID) VALUES
	(@v_user_sysadmin, @v_role_style);

SELECT ID from `security_package` WHERE CODE = 'STYLE' LIMIT 1 INTO @v_package_style;
	
INSERT IGNORE INTO `security_client_package` (CLIENT_ID, PACKAGE_ID) VALUES
	(@v_client_system, @v_package_style);