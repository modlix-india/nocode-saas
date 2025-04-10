USE `entity_collector`;

CREATE TABLE IF NOT EXISTS entity_integrations
(
    `id`               BIGINT UNSIGNED                                  NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each Entity Integration',
    `client_code`      CHAR(8)                                          NOT NULL COMMENT 'Client Code',
    `app_code`         CHAR(8)                                          NOT NULL COMMENT 'App Code',
    `target`           VARCHAR(255)                                     NOT NULL COMMENT 'Target',
    `secondary_target` VARCHAR(255) COMMENT 'Secondary target',
    `in_source`        VARCHAR(255) COMMENT 'Source',
    `in_source_type`   ENUM ('FACEBOOK_FORM', 'GOOGLE_FORM', 'WEBSITE') NOT NULL COMMENT 'Type of source that integration is generated',
    `created_by`       BIGINT UNSIGNED                                           DEFAULT NULL COMMENT 'ID of the user who created this row',
    `created_at`       TIMESTAMP                                        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `updated_by`       BIGINT UNSIGNED                                           DEFAULT NULL COMMENT 'ID of the user who updated this row',
    `updated_at`       TIMESTAMP                                        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated',
    PRIMARY KEY (`id`)
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
