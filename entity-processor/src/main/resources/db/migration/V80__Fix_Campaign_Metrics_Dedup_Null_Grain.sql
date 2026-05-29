USE `entity_processor`;
SET SQL_SAFE_UPDATES = 0;

-- The UNIQUE key UK2_METRICS_COMPOSITE on (APP_CODE, CLIENT_CODE, CAMPAIGN_ID,
-- ADSET_ID, AD_ID, METRIC_DATE, PLATFORM) silently allowed duplicates for
-- campaign-grain rows (ADSET_ID NULL + AD_ID NULL) and adset-grain rows (AD_ID
-- NULL), because MySQL treats NULL values in a UNIQUE index as distinct. The
-- ON DUPLICATE KEY UPDATE in CampaignMetricDAO.bulkUpsert therefore never
-- matched on those grains, so every metrics-sync run inserted a fresh row.
-- SUM(SPEND) in the campaign-level report then multiplied the spend by the
-- number of syncs.
--
-- Fix: collapse existing duplicates (keep the most recent row per logical
-- grain), then replace the UNIQUE key with one that uses STORED generated
-- columns (COALESCE of the nullable ids to 0) so the dedup key has the
-- intended semantics. The original ADSET_ID / AD_ID columns stay nullable so
-- application code (DAO inserts, IS NULL filters in reports) does not need to
-- change.

-- 1. Collapse duplicates. Keep MAX(ID) per logical (app, client, campaign,
--    adset-or-0, ad-or-0, date, platform) grain. That row is the most
--    recently written one, which matches what ON DUPLICATE KEY UPDATE would
--    have left behind if the constraint had worked from day one.
DELETE m1 FROM `entity_processor_campaign_metrics` m1
INNER JOIN `entity_processor_campaign_metrics` m2
    ON  m1.APP_CODE    = m2.APP_CODE
    AND m1.CLIENT_CODE = m2.CLIENT_CODE
    AND m1.CAMPAIGN_ID = m2.CAMPAIGN_ID
    AND COALESCE(m1.ADSET_ID, 0) = COALESCE(m2.ADSET_ID, 0)
    AND COALESCE(m1.AD_ID, 0)    = COALESCE(m2.AD_ID, 0)
    AND m1.METRIC_DATE = m2.METRIC_DATE
    AND m1.PLATFORM    = m2.PLATFORM
    AND m1.ID < m2.ID;

-- 2. Drop the broken UNIQUE.
ALTER TABLE `entity_processor_campaign_metrics`
    DROP INDEX `UK2_METRICS_COMPOSITE`;

-- 3. Add STORED generated columns that normalize NULL to 0, so the new UNIQUE
--    dedups campaign-grain and adset-grain rows correctly. STORED (not VIRTUAL)
--    because MySQL only supports indexing virtual columns on a per-version
--    basis and STORED is safe across 5.7+ / 8.x.
ALTER TABLE `entity_processor_campaign_metrics`
    ADD COLUMN `ADSET_ID_KEY` BIGINT UNSIGNED
        GENERATED ALWAYS AS (COALESCE(`ADSET_ID`, 0)) STORED NOT NULL
        COMMENT 'Dedup helper: ADSET_ID coalesced to 0 for the UNIQUE key. Do not write -- generated.',
    ADD COLUMN `AD_ID_KEY` BIGINT UNSIGNED
        GENERATED ALWAYS AS (COALESCE(`AD_ID`, 0)) STORED NOT NULL
        COMMENT 'Dedup helper: AD_ID coalesced to 0 for the UNIQUE key. Do not write -- generated.';

-- 4. Re-add the UNIQUE using the normalized key columns.
ALTER TABLE `entity_processor_campaign_metrics`
    ADD UNIQUE KEY `UK2_METRICS_COMPOSITE` (
        `APP_CODE`, `CLIENT_CODE`, `CAMPAIGN_ID`,
        `ADSET_ID_KEY`, `AD_ID_KEY`,
        `METRIC_DATE`, `PLATFORM`
    );
SET SQL_SAFE_UPDATES = 1;