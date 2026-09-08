-- Removing files whose declared lifetime has passed.
--
-- Here rather than as a @Scheduled in the files service, because production runs several instances
-- of it. A scheduled method fires on every replica at the same instant, so each would read the same
-- expired rows and race the others to delete the same objects. The worker runs on a Quartz cluster
-- with a shared job store, which is what makes "once, somewhere" true.
ALTER TABLE `worker`.`worker_tasks`
    MODIFY COLUMN `TASK_JOB_TYPE` ENUM('SSL_RENEWAL', 'TOKEN_CLEANUP', 'PARTNER_DENORM_DELTA', 'PARTNER_DENORM_FULL', 'CAMPAIGN_METRICS_SYNC', 'CAMPAIGN_DISCOVERY_SYNC', 'CONVERSIONS_API_DISPATCH', 'SECURITY_METERING', 'CORE_METERING', 'ENTITY_PROCESSOR_METERING', 'FILES_METERING', 'BILLING_RECONCILE', 'FILES_TTL_CLEANUP') NOT NULL DEFAULT 'SSL_RENEWAL';

SET @csc_id = (SELECT `ID` FROM `worker`.`worker_client_schedule_controls` WHERE `CLIENT_CODE` = 'SYSTEM' AND `APP_CODE` IS NULL LIMIT 1);

-- Hourly, and bounded per run. Retention is measured in days, so nothing is gained by sweeping more
-- often than this, and a capped hourly pass spreads the deletes instead of dropping a month of
-- expiries into one nightly burst against S3.
--
-- The limit is per store, so an hour removes at most 500 secured and 500 static files. If a backlog
-- ever exceeds that rate the count in the task result says so, which is the signal to raise it
-- rather than something to discover from a storage bill.
INSERT INTO `worker`.`worker_tasks`
    (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CLIENT_SCHEDULE_CONTROL_ID`, `DESCRIPTION`,
     `TASK_STATE`, `TASK_JOB_TYPE`, `JOB_DATA`, `DURABLE`, `SCHEDULE`, `RECOVERABLE`)
VALUES (NULL, 'SYSTEM', 'files-ttl-cleanup', @csc_id, 'Delete files whose per-file lifetime has expired (secured + static)',
        'NORMAL', 'FILES_TTL_CLEANUP', '{"limit": 500}', TRUE, '0 20 * * * ?', TRUE)
ON DUPLICATE KEY UPDATE `DESCRIPTION` = VALUES(`DESCRIPTION`);
