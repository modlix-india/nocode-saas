-- Sweeping app-transport import receipts that are past their retention window.
--
-- A transport document holds the uploaded zip base64'd into a field. applyTransport reads it back
-- once by id while the import runs and nothing ever reads it again, but nothing deleted it either:
-- an app's transports go when the app itself is deleted, and there was no other path. Creating one
-- also writes a second copy of the same bytes into the version collection, since versioning is on
-- for every overridable service.
--
-- The cost is not the disk. AbstractTransportService.create looks a transport up by
-- uniqueTransportCode before inserting and that field has no index, so every chunk of every import
-- scans the whole collection and drags multi-megabyte documents through the cache. Measured on
-- stage 2026-09-14: ui.transport at 955 documents and 2.1 GB against a 2.3 GB WiredTiger cache,
-- one such lookup at 265 seconds, and a single Application object taking 113 seconds to import
-- where a smaller environment took 2.
--
-- This keeps the collection small enough for that scan to stay cheap. It does not remove the scan:
-- that needs an index on uniqueTransportCode, which is a separate change.
--
-- Here rather than as a @Scheduled in ui/core, because production runs several instances of both
-- and a scheduled method fires on every replica at once, each racing the others to delete the same
-- documents. The worker runs on a Quartz cluster with a shared job store, which is what makes
-- "once, somewhere" true.
ALTER TABLE `worker`.`worker_tasks`
    MODIFY COLUMN `TASK_JOB_TYPE` ENUM('SSL_RENEWAL', 'TOKEN_CLEANUP', 'PARTNER_DENORM_DELTA', 'PARTNER_DENORM_FULL', 'CAMPAIGN_METRICS_SYNC', 'CAMPAIGN_DISCOVERY_SYNC', 'CONVERSIONS_API_DISPATCH', 'SECURITY_METERING', 'CORE_METERING', 'ENTITY_PROCESSOR_METERING', 'FILES_METERING', 'BILLING_RECONCILE', 'FILES_TTL_CLEANUP', 'TRANSPORT_CLEANUP') NOT NULL DEFAULT 'SSL_RENEWAL';

SET @csc_id = (SELECT `ID` FROM `worker`.`worker_client_schedule_controls` WHERE `CLIENT_CODE` = 'SYSTEM' AND `APP_CODE` IS NULL LIMIT 1);

-- One row per service, one job type. ui and core own separate transport collections with nothing in
-- common but the shape, and they are nowhere near the same size: on stage ui holds 2.1 GB to core's
-- 116 MB. A row each is what lets them be scheduled, tuned, paused and reported on separately,
-- while `service` in the job data keeps a new sweepable service a row rather than an enum value and
-- another migration. The unique key is (NAME, APP_CODE, CLIENT_CODE), so sharing a job type across
-- rows is fine.
--
-- Nightly, and bounded per run. Retention is 90 days, so there is nothing to gain from sweeping
-- more often, and the deletes are against the same collections a live import reads. 200 a night
-- clears roughly a month of promotions while leaving an environment with a large backlog to drain
-- over several. The counts come back in LAST_FIRE_RESULT, which is the signal to raise the limit
-- rather than something to discover from a slow import.
--
-- Staggered, and both clear of token cleanup at 03:00.
INSERT INTO `worker`.`worker_tasks`
    (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CLIENT_SCHEDULE_CONTROL_ID`, `DESCRIPTION`,
     `TASK_STATE`, `TASK_JOB_TYPE`, `JOB_DATA`, `DURABLE`, `SCHEDULE`, `RECOVERABLE`)
VALUES (NULL, 'SYSTEM', 'transport-cleanup-ui', @csc_id, 'Delete ui app-transport import receipts and their versions older than the retention window',
        'NORMAL', 'TRANSPORT_CLEANUP', '{"service": "ui", "retentionDays": 90, "limit": 200}', TRUE, '0 40 3 * * ?', TRUE),
       (NULL, 'SYSTEM', 'transport-cleanup-core', @csc_id, 'Delete core app-transport import receipts and their versions older than the retention window',
        'NORMAL', 'TRANSPORT_CLEANUP', '{"service": "core", "retentionDays": 90, "limit": 200}', TRUE, '0 50 3 * * ?', TRUE)
ON DUPLICATE KEY UPDATE `DESCRIPTION` = VALUES(`DESCRIPTION`);
