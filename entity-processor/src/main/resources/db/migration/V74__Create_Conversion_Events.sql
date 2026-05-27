USE `entity_processor`;

DROP TABLE IF EXISTS `entity_processor`.`entity_processor_conversion_events`;

CREATE TABLE entity_processor_conversion_events
(
    `ID`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `CODE`                CHAR(22)        NOT NULL COMMENT 'Unique Code to identify this row.',
    `APP_CODE`            CHAR(64)        NOT NULL COMMENT 'AppCode this event belongs to.',
    `CLIENT_CODE`         CHAR(8)         NOT NULL COMMENT 'ClientCode this event belongs to.',
    `TICKET_ID`           BIGINT UNSIGNED NOT NULL COMMENT 'Ticket whose stage transition produced this event.',
    `MAPPING_ID`          BIGINT UNSIGNED NOT NULL COMMENT 'conversion_action_mapping row that triggered this event.',
    `EVENT_ID`            VARCHAR(64)     NOT NULL COMMENT 'Platform-side dedupe key. Deterministic from ticket + mapping so retries hit the same id.',
    `EVENT_NAME`          VARCHAR(64)     NOT NULL COMMENT 'Snapshot of mapping.event_name at enqueue time.',
    `CAMPAIGN_PLATFORM`   ENUM ('GOOGLE','FACEBOOK','LINKEDIN','X') NOT NULL COMMENT 'Snapshot of mapping.campaign_platform at enqueue time.',
    `ACTION_SOURCE`       ENUM ('WEBSITE','SYSTEM_GENERATED') NOT NULL COMMENT 'Derived from ticket origin. website=fbp/fbc match; system_generated=lead_id match.',
    `PAYLOAD_SNAPSHOT`    JSON            NOT NULL COMMENT 'Frozen payload snapshot (hashed user_data + custom_data + event_id + value/currency).',
    `STATUS`              ENUM ('PENDING','SENT','FAILED','SKIPPED') NOT NULL DEFAULT 'PENDING' COMMENT 'Lifecycle state of this outbox row.',
    `PLATFORM_RESPONSE`   JSON                     DEFAULT NULL COMMENT 'Full HTTP response body from the platform on the most recent attempt.',
    `STATUS_MESSAGE`      TEXT                     DEFAULT NULL COMMENT 'Short error/info note for the most recent attempt.',
    `ATTEMPT_COUNT`       INT             NOT NULL DEFAULT 0 COMMENT 'How many delivery attempts so far.',
    `NEXT_ATTEMPT_AT`     TIMESTAMP                DEFAULT NULL COMMENT 'When the dispatcher should retry. NULL while not yet eligible.',
    `SENT_AT`             TIMESTAMP                DEFAULT NULL COMMENT 'First successful send timestamp; NULL until SENT.',
    `TEMP_ACTIVE`         TINYINT         NOT NULL DEFAULT 0 COMMENT 'Temporary active flag.',
    `IS_ACTIVE`           TINYINT         NOT NULL DEFAULT 1 COMMENT 'Soft-delete flag.',
    `CREATED_BY`          BIGINT UNSIGNED          DEFAULT NULL COMMENT 'User who created this row (NULL for system-enqueued events).',
    `CREATED_AT`          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Enqueue time.',
    `UPDATED_BY`          BIGINT UNSIGNED          DEFAULT NULL COMMENT 'User who last updated this row.',
    `UPDATED_AT`          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update time.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_CONVERSION_EVENTS_CODE` (`CODE`),
    UNIQUE KEY `UK2_CONVERSION_EVENTS_TICKET_MAPPING` (`TICKET_ID`, `MAPPING_ID`),
    INDEX `IDX1_CE_AC_CC` (`APP_CODE`, `CLIENT_CODE`),
    INDEX `IDX2_CE_PENDING_DISPATCH` (`STATUS`, `NEXT_ATTEMPT_AT`),
    INDEX `IDX3_CE_TICKET` (`TICKET_ID`),
    CONSTRAINT `FK1_CE_TICKET_ID` FOREIGN KEY (`TICKET_ID`)
        REFERENCES `entity_processor`.`entity_processor_tickets` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT `FK2_CE_MAPPING_ID` FOREIGN KEY (`MAPPING_ID`)
        REFERENCES `entity_processor`.`entity_processor_conversion_action_mapping` (`ID`)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
