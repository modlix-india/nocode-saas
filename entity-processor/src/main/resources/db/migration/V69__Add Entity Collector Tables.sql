-- Migrate entity-collector tables into entity-processor database

-- Integration configuration (webhook targets, source mapping)
CREATE TABLE `entity_processor`.`entity_processor_integrations`
(
    `ID`                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`               CHAR(64)     NOT NULL COMMENT 'App Code.',
    `CLIENT_CODE`            CHAR(8)      NOT NULL COMMENT 'Client Code.',
    `CODE`                   CHAR(22)     NOT NULL COMMENT 'Unique Code to identify this row.',
    `IN_APP_CODE`            CHAR(64)     NULL COMMENT 'Source application code.',
    `OUT_APP_CODE`           CHAR(64)     NULL COMMENT 'Destination application code.',
    `PRIMARY_TARGET`         VARCHAR(255) NULL COMMENT 'Primary webhook URL for forwarding leads.',
    `SECONDARY_TARGET`       VARCHAR(255) NULL COMMENT 'Secondary backup webhook URL.',
    `IN_SOURCE`              VARCHAR(255) NULL COMMENT 'Source identifier (form ID, website domain, etc.).',
    `IN_SOURCE_TYPE`         ENUM('FACEBOOK_FORM','GOOGLE_FORM','WEBSITE','TIKTOK_FORM','MICROSOFT_FORM','AMAZON_FORM','PINTEREST_FORM','REDDIT_FORM','SNAPCHAT_FORM','QUORA_FORM','YAHOO_FORM','YANDEX_FORM','DUCKDUCKGO_FORM','MASTODON_FORM','DISCORD_FORM') NOT NULL COMMENT 'Type of integration source.',
    `PRIMARY_VERIFY_TOKEN`   VARCHAR(255) NULL COMMENT 'Token for primary target verification.',
    `SECONDARY_VERIFY_TOKEN` VARCHAR(255) NULL COMMENT 'Token for secondary target verification.',
    `STATUS`                 ENUM('ACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE' COMMENT 'Integration status.',
    `CREATED_BY`             BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`             BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_INTEGRATIONS_CODE` (`CODE`),
    UNIQUE KEY `UK2_INTEGRATIONS_CC_SOURCE` (`CLIENT_CODE`, `IN_SOURCE`),
    INDEX `IDX1_INTEGRATIONS_AC_CC` (`APP_CODE`, `CLIENT_CODE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

-- Audit log for webhook/form submissions
CREATE TABLE `entity_processor`.`entity_processor_collector_log`
(
    `ID`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `ENTITY_INTEGRATION_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Reference to entity_processor_integrations.',
    `INCOMING_ENTITY_DATA`  JSON         NULL COMMENT 'Raw incoming webhook payload.',
    `IP_ADDRESS`            VARCHAR(45)  NULL COMMENT 'Client IP address.',
    `OUTGOING_ENTITY_DATA`  JSON         NULL COMMENT 'Normalized/processed entity data sent to target.',
    `STATUS`                ENUM('IN_PROGRESS','REJECTED','SUCCESS','WITH_ERRORS','RESPONSE_CREATED') DEFAULT 'IN_PROGRESS' COMMENT 'Processing status.',
    `STATUS_MESSAGE`        TEXT         NULL COMMENT 'Status description or error message.',
    `CREATED_AT`            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_AT`            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    INDEX `IDX1_COLLECTOR_LOG_INTEGRATION` (`ENTITY_INTEGRATION_ID`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

-- Add platform account fields to campaigns for metrics sync
ALTER TABLE `entity_processor`.`entity_processor_campaigns`
    ADD COLUMN `PLATFORM_ACCOUNT_ID` VARCHAR(64) DEFAULT NULL COMMENT 'Ad account ID (Google customer ID, Meta ad account ID).' AFTER `CAMPAIGN_PLATFORM`,
    ADD COLUMN `PLATFORM_LOGIN_ID` VARCHAR(64) DEFAULT NULL COMMENT 'Google login-customer-id (MCC account). NULL for non-Google platforms.' AFTER `PLATFORM_ACCOUNT_ID`;

-- Expand campaign platform enum to support all platforms
ALTER TABLE `entity_processor`.`entity_processor_campaigns`
    MODIFY COLUMN `CAMPAIGN_PLATFORM` ENUM('GOOGLE','FACEBOOK','LINKEDIN','X','TIKTOK','MICROSOFT','AMAZON','PINTEREST','REDDIT','SNAPCHAT','QUORA','YAHOO','YANDEX','DUCKDUCKGO','MASTODON','DISCORD') NULL;
