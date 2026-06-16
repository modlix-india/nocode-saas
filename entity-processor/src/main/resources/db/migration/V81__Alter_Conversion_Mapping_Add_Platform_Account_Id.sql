USE `entity_processor`;

-- Google Ads conversion actions live inside a specific customer (sub-account
-- under an MCC). When the same MCC wraps multiple customers (e.g. one MCC
-- 4679708549 owning Cityville 4220436668, Purva 9564942562, Earthen 5561308396,
-- Keya 9183148972, ...), each customer has its own set of conversionAction
-- resources. A single mapping row per (productTemplate × stage × platform)
-- therefore can't be right for every customer's tickets -- the
-- platform_action_id (customers/{X}/conversionActions/{Y}) is customer-scoped.
--
-- Add PLATFORM_ACCOUNT_ID to the mapping. For Google this is the customer id
-- the conversionAction belongs to; rows are matched at dispatch against the
-- campaign's own platformAccountId. For Meta this stays NULL (Meta routes via
-- the campaign's pixel/dataset and event_name is shared across customers).
ALTER TABLE `entity_processor`.`entity_processor_conversion_action_mapping`
    ADD COLUMN `PLATFORM_ACCOUNT_ID` VARCHAR(32) DEFAULT NULL
        COMMENT 'Google: customer id (sub-account under MCC) the conversion action belongs to. NULL for Meta and legacy rows.'
        AFTER `CAMPAIGN_PLATFORM`;

-- Existing unique key is (app, client, template, platform, stage, status) and
-- prevents two rows per stage. Widen it to include PLATFORM_ACCOUNT_ID so
-- different customers under the same MCC each get their own mapping row.
ALTER TABLE `entity_processor`.`entity_processor_conversion_action_mapping`
    DROP INDEX `UK2_CAM_AC_CC_PT_PLATFORM_STAGE_STATUS`,
    ADD UNIQUE KEY `UK2_CAM_AC_CC_PT_PLATFORM_STAGE_STATUS_ACCT`
        (`APP_CODE`, `CLIENT_CODE`, `PRODUCT_TEMPLATE_ID`, `CAMPAIGN_PLATFORM`,
         `TRIGGER_STAGE_ID`, `TRIGGER_STATUS_ID`, `PLATFORM_ACCOUNT_ID`);

-- Dispatch lookup filters by (stage, status, template) and now also by
-- (platform, accountId). Add an index that covers the common path.
ALTER TABLE `entity_processor`.`entity_processor_conversion_action_mapping`
    ADD INDEX `IDX3_CAM_TRIGGER_ACCT`
        (`APP_CODE`, `CLIENT_CODE`, `TRIGGER_STAGE_ID`, `CAMPAIGN_PLATFORM`,
         `PLATFORM_ACCOUNT_ID`, `IS_ACTIVE`);

-- Backfill: each existing Google mapping points at one customer's conversion
-- action (parsed from PLATFORM_ACTION_ID = customers/{X}/conversionActions/{Y}).
-- Populate PLATFORM_ACCOUNT_ID from that resource name so historical rows are
-- still selectable under the new unique key without manual fixup.
UPDATE `entity_processor`.`entity_processor_conversion_action_mapping`
SET `PLATFORM_ACCOUNT_ID` = SUBSTRING_INDEX(
        SUBSTRING_INDEX(`PLATFORM_ACTION_ID`, '/conversionActions/', 1),
        'customers/', -1)
WHERE `CAMPAIGN_PLATFORM` = 'GOOGLE'
  AND `PLATFORM_ACTION_ID` LIKE 'customers/%/conversionActions/%'
  AND `PLATFORM_ACCOUNT_ID` IS NULL;
