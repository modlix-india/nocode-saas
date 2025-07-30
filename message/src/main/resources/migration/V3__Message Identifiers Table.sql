DROP TABLE IF EXISTS `message`.`message_provider_identifiers`;

CREATE TABLE `message`.`message_provider_identifiers` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code related to this message.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code related to this message.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',

    `CONNECTION_TYPE` ENUM ('APP_DATA', 'MAIL', 'REST_API', 'CALL', 'TEXT_MESSAGE') NOT NULL COMMENT 'Connection type for this Identifier',
    `CONNECTION_SUB_TYPE` ENUM ('MONGO', 'OFFICE365', 'SENDGRID', 'REST_API_OAUTH2', 'REST_API_BASIC','REST_API_AUTH', 'SMTP', 'EXOTEL', 'WHATSAPP') NOT NULL COMMENT 'Connection Sub type for this Identifier',
    `IDENTIFIER` CHAR(128) NOT NULL COMMENT 'Identifier for this connection and connection sub type for the client.',
    `IS_DEFAULT` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this message is active or not.',

    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this message is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this record was created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this record was last updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PROVIDER_IDENTIFIER_CODE` (`CODE`),
    UNIQUE KEY `UK2_PROVIDER_IDENTIFIER_APP_CODE_CLIENT_CODE_IDENTIFIER` (`APP_CODE`, `CLIENT_CODE`, `CONNECTION_TYPE`,
                                                                          `CONNECTION_SUB_TYPE`, `IDENTIFIER`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
