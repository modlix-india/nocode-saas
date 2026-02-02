SELECT `ID`
  FROM `security`.`security_client`
 WHERE `CODE` = 'SYSTEM'
 LIMIT 1
  INTO @`v_client_system`;

INSERT IGNORE INTO `security`.`security_v2_role` (`CLIENT_ID`, `NAME`, `DESCRIPTION`)
VALUES (@`v_client_system`, 'Notification Manager', 'Role to hold Notification operations permissions');

INSERT IGNORE INTO `security`.`security_v2_role` (`CLIENT_ID`, `NAME`, `SHORT_NAME`, `DESCRIPTION`)
VALUES (@`v_client_system`, 'Notification CREATE', 'Create', 'Notification create'),
       (@`v_client_system`, 'Notification READ', 'Read', 'Notification read'),
       (@`v_client_system`, 'Notification UPDATE', 'Update', 'Notification update'),
       (@`v_client_system`, 'Notification DELETE', 'Delete', 'Notification delete');

SELECT `ID`
  FROM `security`.`security_v2_role`
 WHERE `NAME` = 'Notification Manager'
  INTO @`v_v2_role_notification_manager`;

SELECT `ID`
  FROM `security`.`security_v2_role`
 WHERE `NAME` = 'Notification CREATE'
  INTO @`v_v2_role_notification_create`;
SELECT `ID`
  FROM `security`.`security_v2_role`
 WHERE `NAME` = 'Notification READ'
  INTO @`v_v2_role_notification_read`;
SELECT `ID`
  FROM `security`.`security_v2_role`
 WHERE `NAME` = 'Notification UPDATE'
  INTO @`v_v2_role_notification_update`;
SELECT `ID`
  FROM `security`.`security_v2_role`
 WHERE `NAME` = 'Notification DELETE'
  INTO @`v_v2_role_notification_delete`;

SELECT `ID`
  FROM `security`.`security_permission`
 WHERE `NAME` = 'Notification CREATE'
  INTO @`v_permission_notification_create`;
SELECT `ID`
  FROM `security`.`security_permission`
 WHERE `NAME` = 'Notification READ'
  INTO @`v_permission_notification_read`;
SELECT `ID`
  FROM `security`.`security_permission`
 WHERE `NAME` = 'Notification UPDATE'
  INTO @`v_permission_notification_update`;
SELECT `ID`
  FROM `security`.`security_permission`
 WHERE `NAME` = 'Notification DELETE'
  INTO @`v_permission_notification_delete`;

INSERT IGNORE INTO `security`.`security_v2_role_role` (`ROLE_ID`, `SUB_ROLE_ID`)
VALUES (@`v_v2_role_notification_manager`, @`v_v2_role_notification_create`),
       (@`v_v2_role_notification_manager`, @`v_v2_role_notification_read`),
       (@`v_v2_role_notification_manager`, @`v_v2_role_notification_update`),
       (@`v_v2_role_notification_manager`, @`v_v2_role_notification_delete`);

INSERT IGNORE INTO `security`.`security_v2_role_permission` (`ROLE_ID`, `PERMISSION_ID`)
VALUES (@`v_v2_role_notification_manager`, @`v_permission_notification_create`),
       (@`v_v2_role_notification_manager`, @`v_permission_notification_read`),
       (@`v_v2_role_notification_manager`, @`v_permission_notification_update`),
       (@`v_v2_role_notification_manager`, @`v_permission_notification_delete`);

