USE `entity_processor`;

ALTER TABLE `entity_processor`.`entity_processor_tickets`
    ADD COLUMN `CAMPAIGN_ID` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Campaign Id related to this ticket.' AFTER `SUB_SOURCE`;