SELECT `ID`
FROM `security`.`security_client`
WHERE `CODE` = 'SYSTEM'
LIMIT 1
INTO @`v_client_system`;

INSERT IGNORE INTO `security`.`security_role` (`CLIENT_ID`, `NAME`, `DESCRIPTION`)
VALUES (@`v_client_system`, 'Client Password Policy Manager',
        'Role to hold Client Password Policy operations permissions');

SELECT `ID`
FROM `security`.`security_role`
WHERE `NAME` = 'Client Password Policy Manager'
LIMIT 1
INTO @`v_role_client_pass_policy`;

INSERT IGNORE INTO `security`.`security_role_permission` (`ROLE_ID`, `PERMISSION_ID`)
VALUES (@`v_role_client_pass_policy`, (SELECT `ID`
                                       FROM `security`.`security_permission`
                                       WHERE `NAME` = 'Client Password Policy CREATE'
                                       LIMIT 1)),
       (@`v_role_client_pass_policy`, (SELECT `ID`
                                       FROM `security`.`security_permission`
                                       WHERE `NAME` = 'Client Password Policy READ'
                                       LIMIT 1)),
       (@`v_role_client_pass_policy`, (SELECT `ID`
                                       FROM `security`.`security_permission`
                                       WHERE `NAME` = 'Client Password Policy UPDATE'
                                       LIMIT 1)),
       (@`v_role_client_pass_policy`, (SELECT `ID`
                                       FROM `security`.`security_permission`
                                       WHERE `NAME` = 'Client Password Policy DELETE'
                                       LIMIT 1));

-- adding integration package and role to the system client and system user

SELECT `ID`
FROM `security`.`security_user`
WHERE `USER_NAME` = 'sysadmin'
LIMIT 1
INTO @`v_user_sysadmin`;

INSERT IGNORE INTO `security`.`security_user_role_permission` (`USER_ID`, `ROLE_ID`)
VALUES (@`v_user_sysadmin`, @`v_role_client_pass_policy`);
