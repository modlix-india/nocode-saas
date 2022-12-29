USE security;

SELECT ID from `security`.`security_client` WHERE CODE = 'SYSTEM' LIMIT 1 INTO @v_client_system;

INSERT INTO `security`.`security_role` (CLIENT_ID, NAME, DESCRIPTION) VALUES
	(@v_client_system, 'Workflow Manager', 'Role to hold Workflow operations permissions'),
	(@v_client_system, 'Template Manager', 'Role to hold Template operations permissions');
	
SELECT ID from `security`.`security_role` WHERE NAME = 'Workflow Manager' LIMIT 1 INTO @v_role_workflow;
SELECT ID from `security`.`security_role` WHERE NAME = 'Template Manager' LIMIT 1 INTO @v_role_template;

INSERT INTO `security`.`security_permission` (CLIENT_ID, NAME, DESCRIPTION) VALUES
	(@v_client_system, 'Workflow CREATE', 'Workflow create'),
	(@v_client_system, 'Workflow READ', 'Workflow read'),
	(@v_client_system, 'Workflow UPDATE', 'Workflow update'),
	(@v_client_system, 'Workflow DELETE', 'Workflow delete'),
	(@v_client_system, 'Template CREATE', 'Template create'),
	(@v_client_system, 'Template READ', 'Template read'),
	(@v_client_system, 'Template UPDATE', 'Template update'),
	(@v_client_system, 'Template DELETE', 'Template delete');
	
INSERT IGNORE INTO `security`.`security_role_permission` (ROLE_ID, PERMISSION_ID) VALUES
	(@v_role_workflow, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Workflow CREATE' LIMIT 1)),
	(@v_role_workflow, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Workflow READ' LIMIT 1)),
	(@v_role_workflow, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Workflow UPDATE' LIMIT 1)),
	(@v_role_workflow, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Workflow DELETE' LIMIT 1)),
	(@v_role_template, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Template CREATE' LIMIT 1)),
	(@v_role_template, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Template READ' LIMIT 1)),
	(@v_role_template, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Template UPDATE' LIMIT 1)),
	(@v_role_template, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Template DELETE' LIMIT 1));
	
SELECT ID from `security`.`security_app` WHERE app_code = 'appbuilder' LIMIT 1 INTO @v_app_appbuilder;

INSERT INTO `security`.`security_role` (CLIENT_ID, NAME, APP_ID, DESCRIPTION) VALUES
	(@v_client_system, 'Actions Manager', @v_app_appbuilder, 'Role to hold Actions operations permissions');
	
SELECT ID from `security`.`security_role` WHERE NAME = 'Actions Manager' LIMIT 1 INTO @v_role_actions;
	
INSERT INTO `security`.`security_permission` (CLIENT_ID, NAME, APP_ID, DESCRIPTION) VALUES
	(@v_client_system, 'Actions CREATE', @v_app_appbuilder, 'Actions create'),
	(@v_client_system, 'Actions READ', @v_app_appbuilder, 'Actions read'),
	(@v_client_system, 'Actions UPDATE', @v_app_appbuilder, 'Actions update'),
	(@v_client_system, 'Actions DELETE', @v_app_appbuilder, 'Actions delete');
	
INSERT IGNORE INTO `security`.`security_role_permission` (ROLE_ID, PERMISSION_ID) VALUES
	(@v_role_actions, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Actions CREATE' LIMIT 1)),
	(@v_role_actions, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Actions READ' LIMIT 1)),
	(@v_role_actions, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Actions UPDATE' LIMIT 1)),
	(@v_role_actions, (SELECT ID FROM `security`.`security_permission` WHERE NAME = 'Actions DELETE' LIMIT 1));

-- Forgot to create packages for data and files so added here	

INSERT IGNORE INTO `security_package` (CLIENT_ID, CODE, NAME, DESCRIPTION, BASE) VALUES
	(@v_client_system, 'FILES', 'Files Management', 'Files management roles and permissions will be part of this package', FALSE),
	(@v_client_system, 'DATA', 'Data Management', 'Data management roles and permissions will be part of this package', FALSE),
	(@v_client_system, 'WRKFL', 'Workflow Management', 'Workflow management roles and permissions will be part of this package', FALSE),
	(@v_client_system, 'TEMPLT', 'Template Management', 'Template management roles and permissions will be part of this package', FALSE);

SELECT ID from `security`.`security_role` WHERE NAME = 'Files Manager' LIMIT 1 INTO @v_role_files;
SELECT ID from `security`.`security_role` WHERE NAME = 'Data Manager' LIMIT 1 INTO @v_role_data;
SELECT ID from `security`.`security_role` WHERE NAME = 'Data Connection Manager' LIMIT 1 INTO @v_role_connection;

SELECT ID from `security_package` WHERE CODE = 'FILES' LIMIT 1 INTO @v_package_files;
SELECT ID from `security_package` WHERE CODE = 'DATA' LIMIT 1 INTO @v_package_data;
SELECT ID from `security_package` WHERE CODE = 'WRKFL' LIMIT 1 INTO @v_package_workflow;
SELECT ID from `security_package` WHERE CODE = 'TEMPLT' LIMIT 1 INTO @v_package_templates;

INSERT IGNORE INTO `security_package_role` (ROLE_ID, PACKAGE_ID) VALUES
	(@v_role_files,  @v_package_files),
	(@v_role_data, @v_package_data),
	(@v_role_connection, @v_package_data),
	(@v_role_workflow, @v_package_workflow),
	(@v_role_actions, @v_package_workflow),
	(@v_role_template, @v_package_templates);

INSERT IGNORE INTO `security_client_package` (CLIENT_ID, PACKAGE_ID) VALUES
	(@v_client_system, @v_package_files),
	(@v_client_system, @v_package_data),
	(@v_client_system, @v_package_workflow),
	(@v_client_system, @v_package_templates);
	
SELECT ID FROM `security`.`security_user` WHERE USER_NAME = 'sysadmin' LIMIT 1 INTO @v_user_sysadmin;

INSERT IGNORE INTO `security`.`security_user_role_permission` (USER_ID, ROLE_ID) VALUES
	(@v_user_sysadmin, @v_role_actions),
	(@v_user_sysadmin, @v_role_template),
	(@v_user_sysadmin, @v_role_workflow);
