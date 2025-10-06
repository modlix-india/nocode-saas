DROP TABLE IF EXISTS `message`.`message_whatsapp_business_account`;

CREATE TABLE `message`.`message_whatsapp_business_account` (
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code related to this message.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code related to this message.',
    `USER_ID` BIGINT UNSIGNED NULL COMMENT 'ID of the user associated with this message.',
    `CODE` CHAR(22) NOT NULL COMMENT 'Unique Code to identify this row.',

    `WHATSAPP_BUSINESS_ACCOUNT_ID` VARCHAR(255) NOT NULL COMMENT 'WhatsApp Business Account ID.',
    `NAME` VARCHAR(255) NOT NULL COMMENT 'WhatsApp Business Account Name',
    `CURRENCY` VARCHAR(255) NULL COMMENT 'WhatsApp Business Account Currency',
    `TIMEZONE_ID` VARCHAR(255) NULL COMMENT 'WhatsApp Business Account Timezone ID',
    `MESSAGE_TEMPLATE_NAMESPACE` VARCHAR(255) NOT NULL COMMENT 'WhatsApp Business Account Message Template Namespace',
    `SUBSCRIBED_APP` JSON NULL COMMENT 'WhatsApp Business Account Subscribed App',

    `IS_ACTIVE` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to check if this message is active or not.',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this record was created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this record was last updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_MESSAGES_CODE` (`CODE`),
    UNIQUE KEY `UK2_WHATSAPP_BUSINESS_ACCOUNT_AC_CC_BAID` (`APP_CODE`, `CLIENT_CODE`, `WHATSAPP_BUSINESS_ACCOUNT_ID`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
