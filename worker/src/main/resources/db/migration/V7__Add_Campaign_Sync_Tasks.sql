-- Add campaign sync job types
ALTER TABLE `worker`.`worker_tasks`
    MODIFY COLUMN `TASK_JOB_TYPE` ENUM('SSL_RENEWAL', 'TOKEN_CLEANUP', 'PARTNER_DENORM_DELTA', 'PARTNER_DENORM_FULL', 'CAMPAIGN_METRICS_SYNC', 'CAMPAIGN_DISCOVERY_SYNC') NOT NULL DEFAULT 'SSL_RENEWAL';

SET @csc_id = (SELECT `ID` FROM `worker`.`worker_client_schedule_controls` WHERE `CLIENT_CODE` = 'SYSTEM' AND `APP_CODE` IS NULL LIMIT 1);

-- Campaign Metrics Sync: every hour at minute 10 (offset from other hourly jobs)
INSERT INTO `worker`.`worker_tasks`
    (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CLIENT_SCHEDULE_CONTROL_ID`, `DESCRIPTION`,
     `TASK_STATE`, `TASK_JOB_TYPE`, `JOB_DATA`, `DURABLE`, `SCHEDULE`, `RECOVERABLE`)
VALUES (NULL, 'SYSTEM', 'campaign-metrics-sync', @csc_id, 'Hourly metrics refresh for every active enabled campaign across all tenants',
        'NORMAL', 'CAMPAIGN_METRICS_SYNC', '{}', TRUE, '0 10 * * * ?', TRUE)
ON DUPLICATE KEY UPDATE `DESCRIPTION` = VALUES(`DESCRIPTION`);

-- Campaign Discovery Sync: daily at 03:20 — re-mirrors adsets + ads for every enabled campaign
INSERT INTO `worker`.`worker_tasks`
    (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CLIENT_SCHEDULE_CONTROL_ID`, `DESCRIPTION`,
     `TASK_STATE`, `TASK_JOB_TYPE`, `JOB_DATA`, `DURABLE`, `SCHEDULE`, `RECOVERABLE`)
VALUES (NULL, 'SYSTEM', 'campaign-discovery-sync', @csc_id, 'Daily re-mirror of adsets and ads under enabled campaigns',
        'NORMAL', 'CAMPAIGN_DISCOVERY_SYNC', '{}', TRUE, '0 20 3 * * ?', TRUE)
ON DUPLICATE KEY UPDATE `DESCRIPTION` = VALUES(`DESCRIPTION`);
