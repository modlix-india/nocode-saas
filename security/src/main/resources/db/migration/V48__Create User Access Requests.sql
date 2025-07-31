USE `security`;

DROP TABLE IF EXISTS `security`.`security_user_request`;

CREATE TABLE `security`.`security_user_request`
(
    `ID`         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',

    `REQUEST_ID` varchar(20)     NOT NULL COMMENT 'Request id for the user request',
    `CLIENT_ID`  bigint UNSIGNED NOT NULL COMMENT 'Client id for the user request',
    `USER_ID`    bigint UNSIGNED NOT NULL COMMENT 'User id for the user request',
    `APP_ID`     bigint UNSIGNED NOT NULL COMMENT 'App id for the user request',
    `STATUS`     ENUM ('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING' COMMENT 'Status of the user request',

    `CREATED_BY` BIGINT UNSIGNED                          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP       NOT NULL                 DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED                          DEFAULT NULL COMMENT 'ID of the user who last updated this row',
    `UPDATED_AT` TIMESTAMP       NOT NULL                 DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is last updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY (`REQUEST_ID`),

    CONSTRAINT `fk_security_user_request_client` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security`.`security_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_security_user_request_user` FOREIGN KEY (`USER_ID`) REFERENCES `security`.`security_user` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_security_user_request_app` FOREIGN KEY (`APP_ID`) REFERENCES `security`.`security_app` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;