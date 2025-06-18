ALTER TABLE `entity_processor`.`entity_processor_tickets`
    MODIFY COLUMN `ASSIGNED_USER_ID` BIGINT UNSIGNED NULL COMMENT 'User which added this ticket or user who is assigned to this ticket.';
