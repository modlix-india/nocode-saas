USE `entity_collector`;

ALTER TABLE `entity_collector`.`entity_integrations`
    CHANGE COLUMN `APP_CODE` `IN_APP_CODE` CHAR(64) NOT NULL COMMENT 'in-app code';

ALTER TABLE `entity_collector`.`entity_integrations`
    ADD COLUMN `OUT_APP_CODE` CHAR(64) NOT NULL COMMENT 'out-app Code' AFTER IN_APP_CODE;