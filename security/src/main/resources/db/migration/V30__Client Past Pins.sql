DROP TABLE IF EXISTS `security`.`security_past_pins`;

CREATE TABLE IF NOT EXISTS `security`.`security_past_pins`
(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each Past PIN entry',
    `USER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'User ID',
    `PIN` VARCHAR(512) DEFAULT NULL COMMENT 'Pin message digested string',
    `PIN_HASHED` TINYINT DEFAULT 1 COMMENT 'Pin stored is hashed or not',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (`ID`),
    CONSTRAINT `FK1_PAST_PIN_USER_ID` FOREIGN KEY (`USER_ID`) REFERENCES `security`.`security_user` (`ID`) ON DELETE RESTRICT ON UPDATE RESTRICT
)
    ENGINE = INNODB
    CHARACTER SET `utf8mb4`
    COLLATE `utf8mb4_unicode_ci`;
