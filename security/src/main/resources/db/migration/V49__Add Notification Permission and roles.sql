START TRANSACTION;

-- Adding Notification package, role and permissions
SELECT `ID`
  FROM `security_client`
 WHERE `CODE` = 'SYSTEM'
 LIMIT 1
  INTO @`v_client_system`;

INSERT IGNORE INTO `security_permission` (`CLIENT_ID`, `NAME`, `DESCRIPTION`)
VALUES (@`v_client_system`, 'Notification CREATE', 'Notification create'),
       (@`v_client_system`, 'Notification READ', 'Notification read'),
       (@`v_client_system`, 'Notification UPDATE', 'Notification update'),
       (@`v_client_system`, 'Notification DELETE', 'Notification delete');

INSERT IGNORE INTO `security_role` (`CLIENT_ID`, `NAME`, `DESCRIPTION`)
VALUES (@`v_client_system`, 'Notification Manager', 'Role to hold Notification operations permissions');

SELECT `ID`
  FROM `security_role`
 WHERE `NAME` = 'Notification Manager'
 LIMIT 1
  INTO @`v_role_notification`;

INSERT IGNORE INTO `security_role_permission` (`ROLE_ID`, `PERMISSION_ID`)
VALUES (@`v_role_notification`, (SELECT `ID` FROM `security_permission` WHERE `NAME` = 'Notification CREATE' LIMIT 1)),
       (@`v_role_notification`, (SELECT `ID` FROM `security_permission` WHERE `NAME` = 'Notification READ' LIMIT 1)),
       (@`v_role_notification`, (SELECT `ID` FROM `security_permission` WHERE `NAME` = 'Notification UPDATE' LIMIT 1)),
       (@`v_role_notification`, (SELECT `ID` FROM `security_permission` WHERE `NAME` = 'Notification DELETE' LIMIT 1));

INSERT IGNORE INTO `security_package` (`CLIENT_ID`, `CODE`, `NAME`, `DESCRIPTION`, `BASE`)
VALUES (@`v_client_system`, 'NOTI', 'Notifications Management',
        'Notifications management roles and permissions will be part of this package', FALSE);

SELECT `ID`
  FROM `security_package`
 WHERE `CODE` = 'NOTI'
 LIMIT 1
  INTO @`v_package_notification`;

INSERT IGNORE INTO `security_package_role` (`ROLE_ID`, `PACKAGE_ID`)
VALUES (@`v_role_notification`, @`v_package_notification`);

COMMIT;

-- Adding Notification package and role to the system client and system user
START TRANSACTION;

INSERT IGNORE INTO `security_client_package` (`CLIENT_ID`, `PACKAGE_ID`)
VALUES (@`v_client_system`, @`v_package_notification`);

SELECT `ID`
  FROM `security`.`security_user`
 WHERE `USER_NAME` = 'sysadmin'
 LIMIT 1
  INTO @`v_user_sysadmin`;

INSERT IGNORE INTO `security`.`security_user_role_permission` (`USER_ID`, `ROLE_ID`)
VALUES (@`v_user_sysadmin`, @`v_role_notification`);

COMMIT;
