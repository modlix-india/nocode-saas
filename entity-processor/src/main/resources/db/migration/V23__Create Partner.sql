DROP TABLE IF EXISTS `entity_processor`.`entity_processor_partners`;

CREATE TABLE `entity_processor`.`entity_processor_partners` (

    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this Partner was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who added this partner.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',
    `NAME` VARCHAR(512) NOT NULL COMMENT 'Name of the Partner.',
    `DESCRIPTION` TEXT NULL COMMENT 'Description for the Partner.',
    `CLIENT_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Partner client Id.',
    `MANAGER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Partner manager user Id.',
    `PARTNER_VERIFICATION_STATUS` ENUM (
        'INVITATION_SENT',
        'REQUEST_REVOKED',
        'APPROVAL_PENDING',
        'REQUEST_CORRECTION',
        'REJECTED',
        'VERIFIED') NOT NULL COMMENT 'Partner verification status.',
    `DNC` TINYINT NOT NULL DEFAULT 0 COMMENT 'Do Not Call flag for this partner.',
    `TEMP_ACTIVE` TINYINT NOT NULL DEFAULT 0 COMMENT 'Temporary active flag for this partner.',
    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this partner is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PARTNERS_CODE` (`CODE`),
    UNIQUE KEY `UK2_PARTNER_APP_CODE_CLIENT_CODE_CLIENT_ID` (`APP_CODE`, `CLIENT_CODE`, `CLIENT_ID`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
