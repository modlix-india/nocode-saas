ALTER TABLE `entity_processor`.`entity_processor_activities`
    ADD COLUMN `STAGE_ID` BIGINT UNSIGNED NULL COMMENT 'Stage related to this Activity.' AFTER `NOTE_ID`,
    ADD COLUMN `STATUS_ID` BIGINT UNSIGNED NULL COMMENT 'Status related to this Activity.' AFTER `STAGE_ID`,
    ADD CONSTRAINT `FK4_ACTIVITIES_STAGE_ID` FOREIGN KEY (`STAGE_ID`)
        REFERENCES `entity_processor`.`entity_processor_stages` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    ADD CONSTRAINT `FK5_ACTIVITIES_STATUS_ID` FOREIGN KEY (`STATUS_ID`)
        REFERENCES `entity_processor`.`entity_processor_stages` (`ID`)
        ON DELETE RESTRICT
        ON UPDATE CASCADE;

UPDATE `entity_processor`.`entity_processor_activities`
SET `STATUS_ID` = CAST(JSON_UNQUOTE(JSON_EXTRACT(`OBJECT_DATA`, '$.status.id')) AS UNSIGNED)
WHERE `ACTIVITY_ACTION` = 'STATUS_CREATE'
  AND `OBJECT_DATA` IS NOT NULL
  AND JSON_EXTRACT(`OBJECT_DATA`, '$.status.id') IS NOT NULL;

UPDATE `entity_processor`.`entity_processor_activities`
SET `STAGE_ID` = CAST(JSON_UNQUOTE(JSON_EXTRACT(`OBJECT_DATA`, '$.stage.id')) AS UNSIGNED)
WHERE `ACTIVITY_ACTION` = 'STAGE_UPDATE'
  AND `OBJECT_DATA` IS NOT NULL
  AND JSON_EXTRACT(`OBJECT_DATA`, '$.stage.id') IS NOT NULL;
