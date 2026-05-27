ALTER TABLE `entity_processor`.`entity_processor_stages`
    ADD COLUMN `FUNNEL_STAGE` ENUM ('LEAD','MQL','SQL','WON','LOST','CUSTOM') NULL
        COMMENT 'Semantic funnel position of this stage. Industry-agnostic. Drives default conversion-event firing to ad platforms (Meta, Google, etc.).'
        AFTER `STAGE_TYPE`;
