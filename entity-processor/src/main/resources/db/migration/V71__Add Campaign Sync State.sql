-- Tracks incremental sync state per campaign per platform.
-- First sync fetches all historical data; subsequent syncs are incremental.

CREATE TABLE `entity_processor`.`entity_processor_campaign_sync_state`
(
    `ID`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`        CHAR(64)  NOT NULL COMMENT 'App Code.',
    `CLIENT_CODE`     CHAR(8)   NOT NULL COMMENT 'Client Code.',
    `CAMPAIGN_ID`     BIGINT UNSIGNED NOT NULL COMMENT 'FK to entity_processor_campaigns.',
    `PLATFORM`        ENUM('GOOGLE','FACEBOOK','LINKEDIN','X','TIKTOK','MICROSOFT','AMAZON','PINTEREST','REDDIT','SNAPCHAT','QUORA','YAHOO','YANDEX','DUCKDUCKGO','MASTODON','DISCORD') NOT NULL COMMENT 'Ad platform.',
    `LAST_SYNC_AT`    TIMESTAMP NULL COMMENT 'When the last sync completed.',
    `LAST_SYNCED_TO`  DATE      NULL COMMENT 'Data fetched up to this date.',
    `SYNC_START_DATE` DATE      NOT NULL COMMENT 'Campaign data available from this date (historical start).',
    `SYNC_STATUS`     ENUM('IDLE','IN_PROGRESS','FAILED') DEFAULT 'IDLE' COMMENT 'Current sync status.',
    `ERROR_MESSAGE`   TEXT      NULL COMMENT 'Error details if sync failed.',
    `CREATED_AT`      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_AT`      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_SYNC_COMPOSITE` (`APP_CODE`, `CLIENT_CODE`, `CAMPAIGN_ID`, `PLATFORM`),
    INDEX `IDX1_SYNC_STATUS` (`SYNC_STATUS`),
    CONSTRAINT `FK1_SYNC_CAMPAIGN` FOREIGN KEY (`CAMPAIGN_ID`)
        REFERENCES `entity_processor_campaigns` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
