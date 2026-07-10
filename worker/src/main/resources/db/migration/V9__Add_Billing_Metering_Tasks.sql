-- Widen TASK_JOB_TYPE enum to include the token-billing metering + reconcile types
ALTER TABLE `worker`.`worker_tasks`
    MODIFY COLUMN `TASK_JOB_TYPE` ENUM('SSL_RENEWAL', 'TOKEN_CLEANUP', 'PARTNER_DENORM_DELTA', 'PARTNER_DENORM_FULL', 'CAMPAIGN_METRICS_SYNC', 'CAMPAIGN_DISCOVERY_SYNC', 'CONVERSIONS_API_DISPATCH', 'SECURITY_METERING', 'CORE_METERING', 'ENTITY_PROCESSOR_METERING', 'FILES_METERING', 'BILLING_RECONCILE') NOT NULL DEFAULT 'SSL_RENEWAL';

SET @csc_id = (SELECT `ID` FROM `worker`.`worker_client_schedule_controls` WHERE `CLIENT_CODE` = 'SYSTEM' AND `APP_CODE` IS NULL LIMIT 1);

-- The four 15-minute meters: each triggers the owning service to compute usage and
-- push token charges to security. Idempotency (wallet, action, date, window) makes a
-- blue-green double-fire safe.
INSERT INTO `worker`.`worker_tasks`
    (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CLIENT_SCHEDULE_CONTROL_ID`, `DESCRIPTION`,
     `TASK_STATE`, `TASK_JOB_TYPE`, `JOB_DATA`, `DURABLE`, `SCHEDULE`, `RECOVERABLE`)
VALUES (NULL, 'SYSTEM', 'security-metering', @csc_id, 'Charge security-owned token rent (app/site/user) for the current 15-minute window',
        'NORMAL', 'SECURITY_METERING', '{}', TRUE, '0 0/15 * * * ?', TRUE)
ON DUPLICATE KEY UPDATE `DESCRIPTION` = VALUES(`DESCRIPTION`);

INSERT INTO `worker`.`worker_tasks`
    (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CLIENT_SCHEDULE_CONTROL_ID`, `DESCRIPTION`,
     `TASK_STATE`, `TASK_JOB_TYPE`, `JOB_DATA`, `DURABLE`, `SCHEDULE`, `RECOVERABLE`)
VALUES (NULL, 'SYSTEM', 'core-metering', @csc_id, 'Charge core storage-row token rent for the current 15-minute window',
        'NORMAL', 'CORE_METERING', '{}', TRUE, '0 0/15 * * * ?', TRUE)
ON DUPLICATE KEY UPDATE `DESCRIPTION` = VALUES(`DESCRIPTION`);

INSERT INTO `worker`.`worker_tasks`
    (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CLIENT_SCHEDULE_CONTROL_ID`, `DESCRIPTION`,
     `TASK_STATE`, `TASK_JOB_TYPE`, `JOB_DATA`, `DURABLE`, `SCHEDULE`, `RECOVERABLE`)
VALUES (NULL, 'SYSTEM', 'entity-processor-metering', @csc_id, 'Charge entity-processor deals token rent for the current 15-minute window',
        'NORMAL', 'ENTITY_PROCESSOR_METERING', '{}', TRUE, '0 0/15 * * * ?', TRUE)
ON DUPLICATE KEY UPDATE `DESCRIPTION` = VALUES(`DESCRIPTION`);

INSERT INTO `worker`.`worker_tasks`
    (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CLIENT_SCHEDULE_CONTROL_ID`, `DESCRIPTION`,
     `TASK_STATE`, `TASK_JOB_TYPE`, `JOB_DATA`, `DURABLE`, `SCHEDULE`, `RECOVERABLE`)
VALUES (NULL, 'SYSTEM', 'files-metering', @csc_id, 'Charge files (static+secured GB) token rent for the current 15-minute window',
        'NORMAL', 'FILES_METERING', '{}', TRUE, '0 0/15 * * * ?', TRUE)
ON DUPLICATE KEY UPDATE `DESCRIPTION` = VALUES(`DESCRIPTION`);

-- Nightly reconciliation at 00:45, reconciling the previous day across all services.
INSERT INTO `worker`.`worker_tasks`
    (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CLIENT_SCHEDULE_CONTROL_ID`, `DESCRIPTION`,
     `TASK_STATE`, `TASK_JOB_TYPE`, `JOB_DATA`, `DURABLE`, `SCHEDULE`, `RECOVERABLE`)
VALUES (NULL, 'SYSTEM', 'billing-reconcile', @csc_id, 'Back-charge any 15-minute window of the previous day missed to downtime',
        'NORMAL', 'BILLING_RECONCILE', '{}', TRUE, '0 45 0 * * ?', TRUE)
ON DUPLICATE KEY UPDATE `DESCRIPTION` = VALUES(`DESCRIPTION`);
