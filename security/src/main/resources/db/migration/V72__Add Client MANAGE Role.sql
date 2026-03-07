SELECT `ID`
  FROM `security`.`security_client`
 WHERE `CODE` = 'SYSTEM'
  INTO @`v_system_client_id`;

INSERT INTO `security`.`security_v2_role` (`CLIENT_ID`, `NAME`, `SHORT_NAME`, `DESCRIPTION`)
VALUES (@`v_system_client_id`, 'Client MANAGE', 'Client MANAGE', 'Role for managing client managers parallel to Owner.');