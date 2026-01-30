ALTER TABLE `entity_processor`.`entity_processor_activities`
    DROP INDEX `IDX3_ACTIVITIES_ACTION_DATE_STAGE_ID`;

ALTER TABLE `entity_processor`.`entity_processor_activities`
    ADD INDEX `IDX3_ACTIVITIES_ACTION_STAGE_TICKET_DATE` (ACTIVITY_ACTION, STAGE_ID, TICKET_ID, ACTIVITY_DATE);

DROP VIEW IF EXISTS `entity_processor`.`entity_processor_view_ticket_stage_dates`;
