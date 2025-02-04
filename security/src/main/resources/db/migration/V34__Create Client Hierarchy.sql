DROP TABLE IF EXISTS `security`.`security_client_hierarchy`;

CREATE TABLE `security`.`security_client_hierarchy`
(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
    `MANAGE_CLIENT_LEVEL_0` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Client ID that manages CLIENT_ID',
    `MANAGE_CLIENT_LEVEL_1` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Client ID that manages MANAGE_CLIENT_LEVEL_0',
    `MANAGE_CLIENT_LEVEL_2` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Client ID that manages MANAGE_CLIENT_LEVEL_1',
    `MANAGE_CLIENT_LEVEL_3` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Client ID that manages MANAGE_CLIENT_LEVEL_2',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who last updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is last updated',

    PRIMARY KEY (`ID`),

    UNIQUE KEY `UK1_SECURITY_CLIENT_HIERARCHY` (`CLIENT_ID`),
    CONSTRAINT `FK1_CLIENT_HIERARCHY_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security`.`security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK1_CLIENT_HIERARCHY_LEVEL_0` FOREIGN KEY (`MANAGE_CLIENT_LEVEL_0`) REFERENCES `security`.`security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK1_CLIENT_HIERARCHY_LEVEL_1` FOREIGN KEY (`MANAGE_CLIENT_LEVEL_1`) REFERENCES `security`.`security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK1_CLIENT_HIERARCHY_LEVEL_2` FOREIGN KEY (`MANAGE_CLIENT_LEVEL_2`) REFERENCES `security`.`security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `FK1_CLIENT_HIERARCHY_LEVEL_3` FOREIGN KEY (`MANAGE_CLIENT_LEVEL_3`) REFERENCES `security`.`security_client` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT

) ENGINE = InnoDB
  DEFAULT CHARSET = `UTF8MB4`
  COLLATE = `UTF8MB4_UNICODE_CI`;

-- Adding SYSTEM to security.security_client_hierarchy
INSERT IGNORE INTO security.security_client_manage (CLIENT_ID, MANAGE_CLIENT_ID)
    (SELECT id, 1 FROM `security`.security_client WHERE id NOT IN (SELECT client_id FROM security.security_client_manage) AND CODE <> 'SYSTEM');

INSERT INTO `security`.`security_client_hierarchy` (`CLIENT_ID`,
                                                    `MANAGE_CLIENT_LEVEL_0`,
                                                    `MANAGE_CLIENT_LEVEL_1`,
                                                    `MANAGE_CLIENT_LEVEL_2`,
                                                    `MANAGE_CLIENT_LEVEL_3`)
    (SELECT `ID` AS `CLIENT_ID`, NULL, NULL, NULL, NULL
     FROM `security`.`security_client`
     WHERE `CODE` = 'SYSTEM');

-- Adding security.security_client_hierarchy from security.security_client_manage
INSERT INTO `security`.`security_client_hierarchy` (`CLIENT_ID`,
                                                    `MANAGE_CLIENT_LEVEL_0`,
                                                    `MANAGE_CLIENT_LEVEL_1`,
                                                    `MANAGE_CLIENT_LEVEL_2`,
                                                    `MANAGE_CLIENT_LEVEL_3`)
    (SELECT `scm1`.`CLIENT_ID` AS `CLIENT_ID`,
            `scm1`.`MANAGE_CLIENT_ID` AS `LEVEL_0`,
            `scm2`.`MANAGE_CLIENT_ID` AS `LEVEL_1`,
            `scm3`.`MANAGE_CLIENT_ID` AS `LEVEL_2`,
            `scm4`.`MANAGE_CLIENT_ID` AS `LEVEL_3`
     FROM `security`.`security_client_manage` `scm1`
              LEFT JOIN `security`.`security_client_manage` `scm2`
                        ON `scm1`.`MANAGE_CLIENT_ID` = `scm2`.`CLIENT_ID`
              LEFT JOIN `security`.`security_client_manage` `scm3`
                        ON `scm2`.`MANAGE_CLIENT_ID` = `scm3`.`CLIENT_ID`
              LEFT JOIN `security`.`security_client_manage` `scm4`
                        ON `scm3`.`MANAGE_CLIENT_ID` = `scm4`.`CLIENT_ID`
     ORDER BY `scm1`.`CLIENT_ID`);

