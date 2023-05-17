use security;

SELECT ID from `security_client` WHERE CODE = 'SYSTEM' LIMIT 1 INTO @v_client_system;

INSERT IGNORE INTO `security_permission` (CLIENT_ID, NAME, DESCRIPTION) VALUES
    (@v_client_system, 'Transport CREATE', 'Transport create'),
	(@v_client_system, 'Transport READ', 'Transport read'),
	(@v_client_system, 'Transport UPDATE', 'Transport update'),
	(@v_client_system, 'Transport DELETE', 'Transport delete');
	
INSERT IGNORE INTO `security_role` (CLIENT_ID, NAME, DESCRIPTION) VALUES
	(@v_client_system, 'Transport Manager', 'Role to hold Transport operations permissions');

SELECT ID from `security_role` WHERE NAME = 'Transport Manager' LIMIT 1 INTO @v_role_transport;

INSERT IGNORE INTO `security_role_permission` (ROLE_ID, PERMISSION_ID) VALUES
	(@v_role_transport, (SELECT ID FROM `security_permission` WHERE NAME = 'Transport CREATE' LIMIT 1)),
    (@v_role_transport, (SELECT ID FROM `security_permission` WHERE NAME = 'Transport READ' LIMIT 1)),
	(@v_role_transport, (SELECT ID FROM `security_permission` WHERE NAME = 'Transport UPDATE' LIMIT 1)),
	(@v_role_transport, (SELECT ID FROM `security_permission` WHERE NAME = 'Transport DELETE' LIMIT 1));

INSERT IGNORE INTO `security_package` (CLIENT_ID, CODE, NAME, DESCRIPTION, BASE) VALUES
	(@v_client_system, 'TRANSP', 'Transport Management', 'Transport management roles and permissions will be part of this package', FALSE);

SELECT ID FROM `security`.`security_user` WHERE USER_NAME = 'sysadmin' LIMIT 1 INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID) VALUES
	(@v_user_sysadmin, @v_role_transport);

SELECT ID from `security_package` WHERE CODE = 'TRANSP' LIMIT 1 INTO @v_package_transport;
	
INSERT IGNORE INTO `security_client_package` (CLIENT_ID, PACKAGE_ID) VALUES
	(@v_client_system, @v_package_transport);

SELECT ID FROM `security`.`security_user` WHERE USER_NAME = 'sysadmin' LIMIT 1 INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID) VALUES
	(@v_user_sysadmin, @v_role_transport);