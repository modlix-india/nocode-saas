-- Daily ad-level metrics from ad platforms (Google, Meta, etc.)
-- One row per ad per day. CTR/CPM/CPC/CPL computed at query time.

CREATE TABLE `entity_processor`.`entity_processor_campaign_metrics`
(
    `ID`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `CODE`          CHAR(22)       NOT NULL COMMENT 'Unique Code to identify this row.',
    `APP_CODE`      CHAR(64)       NOT NULL COMMENT 'App Code.',
    `CLIENT_CODE`   CHAR(8)        NOT NULL COMMENT 'Client Code.',
    `CAMPAIGN_ID`   BIGINT UNSIGNED NOT NULL COMMENT 'FK to entity_processor_campaigns.',
    `ADSET_ID`      BIGINT UNSIGNED DEFAULT NULL COMMENT 'FK to entity_processor_adsets. NULL for campaign-level.',
    `AD_ID`         BIGINT UNSIGNED DEFAULT NULL COMMENT 'FK to entity_processor_ads. NULL for adset/campaign-level.',
    `METRIC_DATE`   DATE           NOT NULL COMMENT 'The day these metrics are for.',
    `IMPRESSIONS`   BIGINT         NOT NULL DEFAULT 0 COMMENT 'Number of impressions.',
    `CLICKS`        BIGINT         NOT NULL DEFAULT 0 COMMENT 'Number of clicks.',
    `SPEND`         DECIMAL(14, 4) NOT NULL DEFAULT 0.0000 COMMENT 'Spend in platform currency.',
    `PLATFORM_WL`   BIGINT         NOT NULL DEFAULT 0 COMMENT 'Website Leads count.',
    `PLATFORM_FL`   BIGINT         NOT NULL DEFAULT 0 COMMENT 'Form Leads count.',
    `CURRENCY`      CHAR(3)        DEFAULT 'INR' COMMENT 'Currency code.',
    `PLATFORM`      ENUM('GOOGLE','FACEBOOK','LINKEDIN','X','TIKTOK','MICROSOFT','AMAZON','PINTEREST','REDDIT','SNAPCHAT','QUORA','YAHOO','YANDEX','DUCKDUCKGO','MASTODON','DISCORD') NOT NULL COMMENT 'Ad platform.',
    `CREATED_AT`    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_AT`    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_METRICS_CODE` (`CODE`),
    UNIQUE KEY `UK2_METRICS_COMPOSITE` (`APP_CODE`, `CLIENT_CODE`, `CAMPAIGN_ID`, `ADSET_ID`, `AD_ID`, `METRIC_DATE`, `PLATFORM`),
    INDEX `IDX1_METRICS_DATE` (`APP_CODE`, `CLIENT_CODE`, `METRIC_DATE`),
    INDEX `IDX2_METRICS_CAMPAIGN` (`CAMPAIGN_ID`),
    CONSTRAINT `FK1_METRICS_CAMPAIGN` FOREIGN KEY (`CAMPAIGN_ID`)
        REFERENCES `entity_processor_campaigns` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
