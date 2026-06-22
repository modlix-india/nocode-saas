-- Widen TASK_JOB_TYPE enum to include the hourly file-rent drip type
ALTER TABLE `worker`.`worker_tasks`
    MODIFY COLUMN `TASK_JOB_TYPE` ENUM('SSL_RENEWAL', 'TOKEN_CLEANUP', 'PARTNER_DENORM_DELTA', 'PARTNER_DENORM_FULL', 'CAMPAIGN_METRICS_SYNC', 'CAMPAIGN_DISCOVERY_SYNC', 'CONVERSIONS_API_DISPATCH', 'USAGE_CONSOLIDATION', 'STORAGE_RENT', 'FILE_RENT') NOT NULL DEFAULT 'SSL_RENEWAL';

SET @csc_id = (SELECT `ID` FROM `worker`.`worker_client_schedule_controls` WHERE `CLIENT_CODE` = 'SYSTEM' AND `APP_CODE` IS NULL LIMIT 1);

-- File rent: hourly on the hour. The files service sums each billed client's
-- stored bytes (SUM(SIZE), client-scoped across STATIC + SECURED) and asks
-- security to drip monthlyRate / hoursInMonth * GB onto the consumer's wallet.
INSERT INTO `worker`.`worker_tasks`
    (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CLIENT_SCHEDULE_CONTROL_ID`, `DESCRIPTION`,
     `TASK_STATE`, `TASK_JOB_TYPE`, `JOB_DATA`, `DURABLE`, `SCHEDULE`, `RECOVERABLE`)
VALUES (NULL, 'SYSTEM', 'file-rent', @csc_id, 'Hourly file-storage rent: sums stored bytes per billed client and drips rent',
        'NORMAL', 'FILE_RENT', '{}', TRUE, '0 0 * * * ?', TRUE)
ON DUPLICATE KEY UPDATE `DESCRIPTION` = VALUES(`DESCRIPTION`);
