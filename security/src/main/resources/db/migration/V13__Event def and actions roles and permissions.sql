use security;

SELECT ID from `security_client` WHERE CODE = 'SYSTEM' LIMIT 1 INTO @v_client_system;

INSERT IGNORE INTO `security_permission` (CLIENT_ID, NAME, DESCRIPTION) VALUES
    (@v_client_system, 'EventDefinition CREATE', 'EventDefinition create'),
	(@v_client_system, 'EventDefinition READ', 'EventDefinition read'),
	(@v_client_system, 'EventDefinition UPDATE', 'EventDefinition update'),
	(@v_client_system, 'EventDefinition DELETE', 'EventDefinition delete');
	
INSERT IGNORE INTO `security_role` (CLIENT_ID, NAME, DESCRIPTION) VALUES
	(@v_client_system, 'EventDefinition Manager', 'Role to hold Event Definition operations permissions');

SELECT ID from `security_role` WHERE NAME = 'EventDefinition Manager' LIMIT 1 INTO @v_role_evedef;

INSERT IGNORE INTO `security_role_permission` (ROLE_ID, PERMISSION_ID) VALUES
	(@v_role_evedef, (SELECT ID FROM `security_permission` WHERE NAME = 'EventDefinition CREATE' LIMIT 1)),
    (@v_role_evedef, (SELECT ID FROM `security_permission` WHERE NAME = 'EventDefinition READ' LIMIT 1)),
	(@v_role_evedef, (SELECT ID FROM `security_permission` WHERE NAME = 'EventDefinition UPDATE' LIMIT 1)),
	(@v_role_evedef, (SELECT ID FROM `security_permission` WHERE NAME = 'EventDefinition DELETE' LIMIT 1));

INSERT IGNORE INTO `security_package` (CLIENT_ID, CODE, NAME, DESCRIPTION, BASE) VALUES
	(@v_client_system, 'EVEDE', 'EventDefinition Management', 'Event Definition management roles and permissions will be part of this package', FALSE);

SELECT ID FROM `security`.`security_user` WHERE USER_NAME = 'sysadmin' LIMIT 1 INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID) VALUES
	(@v_user_sysadmin, @v_role_evedef);

SELECT ID from `security_package` WHERE CODE = 'EVEDE' LIMIT 1 INTO @v_package_evedef;
	
INSERT IGNORE INTO `security_client_package` (CLIENT_ID, PACKAGE_ID) VALUES
	(@v_client_system, @v_package_evedef);

SELECT ID FROM `security`.`security_user` WHERE USER_NAME = 'sysadmin' LIMIT 1 INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID) VALUES
	(@v_user_sysadmin, @v_role_evedef);
	

INSERT IGNORE INTO `security_permission` (CLIENT_ID, NAME, DESCRIPTION) VALUES
    (@v_client_system, 'EventAction CREATE', 'EventAction create'),
	(@v_client_system, 'EventAction READ', 'EventAction read'),
	(@v_client_system, 'EventAction UPDATE', 'EventAction update'),
	(@v_client_system, 'EventAction DELETE', 'EventAction delete');
	
INSERT IGNORE INTO `security_role` (CLIENT_ID, NAME, DESCRIPTION) VALUES
	(@v_client_system, 'EventAction Manager', 'Role to hold Event Action operations permissions');

SELECT ID from `security_role` WHERE NAME = 'EventAction Manager' LIMIT 1 INTO @v_role_eveact;

INSERT IGNORE INTO `security_role_permission` (ROLE_ID, PERMISSION_ID) VALUES
	(@v_role_eveact, (SELECT ID FROM `security_permission` WHERE NAME = 'EventAction CREATE' LIMIT 1)),
    (@v_role_eveact, (SELECT ID FROM `security_permission` WHERE NAME = 'EventAction READ' LIMIT 1)),
	(@v_role_eveact, (SELECT ID FROM `security_permission` WHERE NAME = 'EventAction UPDATE' LIMIT 1)),
	(@v_role_eveact, (SELECT ID FROM `security_permission` WHERE NAME = 'EventAction DELETE' LIMIT 1));

INSERT IGNORE INTO `security_package` (CLIENT_ID, CODE, NAME, DESCRIPTION, BASE) VALUES
	(@v_client_system, 'EVEAC', 'EventAction Management', 'Event Action management roles and permissions will be part of this package', FALSE);

SELECT ID FROM `security`.`security_user` WHERE USER_NAME = 'sysadmin' LIMIT 1 INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID) VALUES
	(@v_user_sysadmin, @v_role_eveact);

SELECT ID from `security_package` WHERE CODE = 'EVEAC' LIMIT 1 INTO @v_package_eveact;
	
INSERT IGNORE INTO `security_client_package` (CLIENT_ID, PACKAGE_ID) VALUES
	(@v_client_system, @v_package_eveact);

SELECT ID FROM `security`.`security_user` WHERE USER_NAME = 'sysadmin' LIMIT 1 INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID) VALUES
	(@v_user_sysadmin, @v_role_eveact);