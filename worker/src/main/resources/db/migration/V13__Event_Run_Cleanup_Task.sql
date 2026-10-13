-- Retention for core's event run log (core.core_event_runs, core V9).
--
-- Core records a row for every run of an event's EventAction, with each task's outcome, so an
-- app's users can see whether an event ran and why it failed. This task keeps that table to its
-- retention window and closes runs that never reported an outcome.
--
-- Here rather than as a @Scheduled in core, for the same reason as TRANSPORT_CLEANUP: production
-- runs several core instances and a scheduled method fires on every replica at once. The worker's
-- Quartz cluster runs it once, somewhere, and this row is where its schedule and retention change.
--
-- Job data:
--   successRetentionDays  successful runs started longer ago than this are deleted (core floor: 1)
--   retentionDays         every run started longer ago than this is deleted (core floor: 30)
--   staleAfterHours       a run still RUNNING after this is closed as FAILED (core floor: 1)
--   limit                 rows deleted per run, both kinds together (core cap: 200000)
--
-- Successes go after a week because they are nearly every row and the least looked at: a
-- minute-by-minute schedule writes 1,440 a client a day. Failures, skips and missing actions are
-- what someone comes looking for, so they keep the full 90 days.
--
-- Hourly rather than nightly. The table grows with every event every app raises, so small steady
-- deletes keep each one short against a table events are being written to. A LAST_FIRE_RESULT
-- saying "limit reached" is the signal to raise the limit.
--
-- At :25, clear of files-ttl-cleanup at :20 and the 15-minute metering runs.
ALTER TABLE `worker`.`worker_tasks`
    MODIFY COLUMN `TASK_JOB_TYPE` ENUM('SSL_RENEWAL', 'TOKEN_CLEANUP', 'PARTNER_DENORM_DELTA', 'PARTNER_DENORM_FULL', 'CAMPAIGN_METRICS_SYNC', 'CAMPAIGN_DISCOVERY_SYNC', 'CONVERSIONS_API_DISPATCH', 'SECURITY_METERING', 'CORE_METERING', 'ENTITY_PROCESSOR_METERING', 'FILES_METERING', 'BILLING_RECONCILE', 'FILES_TTL_CLEANUP', 'TRANSPORT_CLEANUP', 'EVENT_RUN_CLEANUP') NOT NULL DEFAULT 'SSL_RENEWAL';

SET @csc_id = (SELECT `ID` FROM `worker`.`worker_client_schedule_controls` WHERE `CLIENT_CODE` = 'SYSTEM' AND `APP_CODE` IS NULL LIMIT 1);

INSERT INTO `worker`.`worker_tasks`
    (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CLIENT_SCHEDULE_CONTROL_ID`, `DESCRIPTION`,
     `TASK_STATE`, `TASK_JOB_TYPE`, `JOB_DATA`, `DURABLE`, `SCHEDULE`, `RECOVERABLE`)
VALUES (NULL, 'SYSTEM', 'event-run-cleanup', @csc_id, 'Delete event run log rows older than the retention window and close runs that never reported',
        'NORMAL', 'EVENT_RUN_CLEANUP', '{"retentionDays": 90, "successRetentionDays": 7, "staleAfterHours": 6, "limit": 50000}', TRUE, '0 25 * * * ?', TRUE)
ON DUPLICATE KEY UPDATE `DESCRIPTION` = VALUES(`DESCRIPTION`);
