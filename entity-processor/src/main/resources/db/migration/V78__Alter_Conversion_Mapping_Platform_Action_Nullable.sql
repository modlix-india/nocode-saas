USE `entity_processor`;

-- PLATFORM_ACTION_ID is optional for Meta (Conversions API keys on EVENT_NAME +
-- the pixel/dataset from the connection). Only Google requires it (the
-- conversionAction resource), enforced at dispatch time. Relax the NOT NULL so a
-- Meta mapping (and the funnel-apply flow) can omit it.
ALTER TABLE `entity_processor`.`entity_processor_conversion_action_mapping`
    MODIFY `PLATFORM_ACTION_ID` VARCHAR(128) NULL
        COMMENT 'Platform-side identifier: Meta custom_conversion_id (optional); Google conversion action resource name (required for Google dispatch).';
