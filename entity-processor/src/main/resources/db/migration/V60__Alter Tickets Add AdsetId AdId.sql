USE `entity_processor`;

ALTER TABLE `entity_processor`.`entity_processor_tickets`
    ADD COLUMN `ADSET_ID` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Adset Id related to this ticket.' AFTER `CAMPAIGN_ID`,
    ADD COLUMN `AD_ID` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Ad Id related to this ticket.' AFTER `ADSET_ID`;
