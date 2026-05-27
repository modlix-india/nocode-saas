ALTER TABLE `entity_processor`.`entity_processor_campaigns`
    ADD COLUMN `PLATFORM_DATASET_ID` VARCHAR(64) NULL
        COMMENT 'Meta Pixel/Dataset ID used as the {DATASET_ID} in CAPI events URL (https://graph.facebook.com/v24.0/{DATASET_ID}/events). For Google, UploadClickConversions does not need this and the field is left NULL.'
        AFTER `PLATFORM_LOGIN_ID`;
