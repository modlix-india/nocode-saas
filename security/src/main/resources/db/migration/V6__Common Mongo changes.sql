use security;

select id from `security`.`security_role` where name = 'Application Manager' limit 1 into @v_role_app_manager;
select id from `security`.`security_permission` where name = 'Application CREATE' limit 1 into @v_permision_app_create;
INSERT INTO `security`.`security_role_permission` (role_id, permission_id) values (@v_role_app_manager, @v_permision_app_create);

SELECT ID from `security`.`security_client` WHERE CODE = 'SYSTEM' LIMIT 1 INTO @v_client_system;

SELECT ID from `security`.`security_app` WHERE app_code = 'appbuilder' LIMIT 1 INTO @v_app_appbuilder;

INSERT INTO `security`.`security_role` (CLIENT_ID, NAME, APP_ID, DESCRIPTION) VALUES
	(@v_client_system, 'Schema Manager', @v_app_appbuilder, 'Role to hold Schema operations permissions');
	
INSERT INTO `security`.`security_permission` (CLIENT_ID, NAME, APP_ID, DESCRIPTION) VALUES
	(@v_client_system, 'Schema CREATE', @v_app_appbuilder, 'Schema create'),
	(@v_client_system, 'Schema READ', @v_app_appbuilder, 'Schema read'),
	(@v_client_system, 'Schema UPDATE', @v_app_appbuilder, 'Schema update'),
	(@v_client_system, 'Schema DELETE', @v_app_appbuilder, 'Schema delete');
	
SELECT ID from `security`.`security_role` WHERE NAME = 'Schema Manager' LIMIT 1 INTO @v_role_schema;

INSERT IGNORE INTO `security`.`security_role_permission` (ROLE_ID, PERMISSION_ID) VALUES
	(@v_role_schema, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Schema CREATE' LIMIT 1)),
	(@v_role_schema, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Schema READ' LIMIT 1)),
	(@v_role_schema, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Schema UPDATE' LIMIT 1)),
	(@v_role_schema, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Schema DELETE' LIMIT 1));
	
SELECT ID FROM `security`.`security_user` WHERE USER_NAME = 'sysadmin' LIMIT 1 INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID) VALUES
	(@v_user_sysadmin, @v_role_data);