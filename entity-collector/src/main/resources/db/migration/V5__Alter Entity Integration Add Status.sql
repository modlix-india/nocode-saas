USE `entity_collector`;

ALTER TABLE `entity_collector`.`entity_integrations`
    ADD COLUMN `STATUS` ENUM ('ACTIVE', 'DELETED') NOT NULL DEFAULT 'ACTIVE' COMMENT 'Integration status' AFTER `SECONDARY_VERIFY_TOKEN`;