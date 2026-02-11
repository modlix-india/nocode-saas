ALTER TABLE `entity_processor`.`entity_processor_tickets`
ADD COLUMN META_DATA JSON NULL COMMENT 'Metadata information related to ticket' AFTER `CLIENT_ID`;
