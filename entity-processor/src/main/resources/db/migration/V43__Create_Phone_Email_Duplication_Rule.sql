DROP TABLE IF EXISTS `entity_processor`.`entity_processor_ticket_pe_duplication_rules`;

CREATE TABLE `entity_processor`.`entity_processor_ticket_pe_duplication_rules`
(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Rule was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this Rule.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(64) NOT NULL COMMENT 'Name of the Rule.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Rule.',
    `PHONE_NUMBER_AND_EMAIL_TYPE` ENUM ('PHONE_NUMBER_ONLY', 'EMAIL_ONLY', 'PHONE_NUMBER_AND_EMAIL', 'PHONE_NUMBER_OR_EMAIL') NOT NULL DEFAULT 'PHONE_NUMBER_OR_EMAIL' COMMENT 'Phone Number and email type for this client.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this Rule.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this Rule is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PTCR_CODE` (`CODE`),
    UNIQUE KEY `UK2_AC_CC_PNET` (`APP_CODE`, `CLIENT_CODE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `UTF8MB4`
  COLLATE = `UTF8MB4_UNICODE_CI`;
