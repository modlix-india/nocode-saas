-- =====================================================================
-- One-time data migration: entity_collector → entity_processor
-- =====================================================================
-- Run AFTER applying entity-processor Flyway migrations through V75
-- (V69 creates entity_processor_integrations + entity_processor_collector_log).
--
-- Usage (prod):
--   mysql -h <host> -u <user> -p < migrate-from-entity-collector.sql
--
-- Idempotent: re-running adds only rows whose IDs aren't already in the
-- target tables. Safe to re-run.
--
-- APP_CODE mapping: 'leadzump'. All source rows have OUT_APP_CODE='leadzump'
-- (the home app where entity-processor lives), so we set the new APP_CODE
-- column to that value. Adjust the literal below if your deployment uses
-- a different home app code.
--
-- CODE generation: deterministic 22-char alphanumeric built from
-- LPAD(source.ID,10,'0') + 12-hex of MD5(ID + RAND()). Unique because
-- source.ID is unique; not cryptographic.
-- =====================================================================

-- 1. Integrations
INSERT INTO entity_processor.entity_processor_integrations
  (ID, APP_CODE, CLIENT_CODE, CODE,
   IN_APP_CODE, OUT_APP_CODE, PRIMARY_TARGET, SECONDARY_TARGET,
   IN_SOURCE, IN_SOURCE_TYPE, PRIMARY_VERIFY_TOKEN, SECONDARY_VERIFY_TOKEN,
   STATUS, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT)
SELECT s.ID,
       'leadzump',
       s.CLIENT_CODE,
       CONCAT(LPAD(s.ID, 10, '0'), SUBSTR(MD5(CONCAT(s.ID, RAND())), 1, 12)),
       s.IN_APP_CODE, s.OUT_APP_CODE, s.PRIMARY_TARGET, s.SECONDARY_TARGET,
       s.IN_SOURCE, s.IN_SOURCE_TYPE, s.PRIMARY_VERIFY_TOKEN, s.SECONDARY_VERIFY_TOKEN,
       s.STATUS, s.CREATED_BY, s.CREATED_AT, s.UPDATED_BY, s.UPDATED_AT
FROM entity_collector.entity_integrations s
WHERE s.ID NOT IN (SELECT ID FROM entity_processor.entity_processor_integrations);

-- 2. Collector log (FK to integrations.ID; preserves IDs from source)
INSERT INTO entity_processor.entity_processor_collector_log
  (ID, ENTITY_INTEGRATION_ID, INCOMING_ENTITY_DATA, IP_ADDRESS, OUTGOING_ENTITY_DATA,
   STATUS, STATUS_MESSAGE, CREATED_AT, UPDATED_AT)
SELECT s.ID, s.ENTITY_INTEGRATION_ID, s.INCOMING_ENTITY_DATA, s.IP_ADDRESS, s.OUTGOING_ENTITY_DATA,
       s.STATUS, s.STATUS_MESSAGE, s.CREATED_AT, s.UPDATED_AT
FROM entity_collector.entity_collector_log s
WHERE s.ID NOT IN (SELECT ID FROM entity_processor.entity_processor_collector_log);

-- 3. Verification
SELECT 'integrations.source'   src, COUNT(*) cnt FROM entity_collector.entity_integrations
UNION ALL SELECT 'integrations.target',   COUNT(*) FROM entity_processor.entity_processor_integrations
UNION ALL SELECT 'collector_log.source',  COUNT(*) FROM entity_collector.entity_collector_log
UNION ALL SELECT 'collector_log.target',  COUNT(*) FROM entity_processor.entity_processor_collector_log;
