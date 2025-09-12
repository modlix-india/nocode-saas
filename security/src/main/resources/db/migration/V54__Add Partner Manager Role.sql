SELECT `ID`
  FROM `security`.`security_client`
 WHERE `CODE` = 'SYSTEM'
  INTO @`v_system_client_id`;

SELECT `ID`
  FROM `security`.`security_app`
 WHERE `APP_CODE` = 'leadzump'
  INTO @`v_leadzump_app_id`;

INSERT INTO `security`.`security_v2_role` (`CLIENT_ID`, `NAME`, `SHORT_NAME`, `DESCRIPTION`, `APP_ID`)
VALUES (@`v_system_client_id`, 'Partner Manager', 'Partner Manager', 'Role for Managing Partners in Leadzump.',
        @`v_leadzump_app_id`);
