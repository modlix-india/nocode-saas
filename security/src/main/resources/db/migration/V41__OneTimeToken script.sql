use security;

DROP TABLE IF EXISTS `security_one_time_token`;

CREATE TABLE `security_one_time_token`
(
    `ID`         bigint unsigned                                              NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `USER_ID`    bigint unsigned                                              NOT NULL COMMENT 'User id',
    `TOKEN`      char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci    NOT NULL COMMENT 'One Time Token',
    `IP_ADDRESS` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'User IP from where he logged in',
    `CREATED_AT` timestamp                                                    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (`ID`),
    KEY `FK1_ONE_TIME_TOKEN_USER_ID` (`USER_ID`),
    UNIQUE KEY `FK3_ONE_TIME_TOKEN` (`TOKEN`),
    CONSTRAINT `FK2_ONE_TIME_TOKEN_USER_ID` FOREIGN KEY (`USER_ID`) REFERENCES `security_user` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;
