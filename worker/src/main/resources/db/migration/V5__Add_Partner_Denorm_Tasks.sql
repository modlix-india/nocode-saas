-- Add partner denorm job types
ALTER TABLE `worker`.`worker_tasks`
    MODIFY COLUMN `TASK_JOB_TYPE` ENUM('SSL_RENEWAL', 'TOKEN_CLEANUP', 'PARTNER_DENORM_DELTA', 'PARTNER_DENORM_FULL') NOT NULL DEFAULT 'SSL_RENEWAL';

SET @csc_id = (SELECT `ID` FROM `worker`.`worker_client_schedule_controls` WHERE `CLIENT_CODE` = 'SYSTEM' AND `APP_CODE` IS NULL LIMIT 1);

-- Partner Denorm Delta: every 30 minutes
INSERT INTO `worker`.`worker_tasks`
    (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CLIENT_SCHEDULE_CONTROL_ID`, `DESCRIPTION`,
     `TASK_STATE`, `TASK_JOB_TYPE`, `JOB_DATA`, `DURABLE`, `SCHEDULE`, `RECOVERABLE`)
VALUES (NULL, 'SYSTEM', 'partner-denorm-delta', @csc_id, 'Delta sync of denormalized partner fields',
        'NORMAL', 'PARTNER_DENORM_DELTA', '{}', TRUE, '0 15,45 * * * ?', TRUE)
ON DUPLICATE KEY UPDATE `DESCRIPTION` = VALUES(`DESCRIPTION`);

-- Partner Denorm Full: every 6 hours at minute 0
INSERT INTO `worker`.`worker_tasks`
    (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CLIENT_SCHEDULE_CONTROL_ID`, `DESCRIPTION`,
     `TASK_STATE`, `TASK_JOB_TYPE`, `JOB_DATA`, `DURABLE`, `SCHEDULE`, `RECOVERABLE`)
VALUES (NULL, 'SYSTEM', 'partner-denorm-full', @csc_id, 'Full sync of denormalized partner fields',
        'NORMAL', 'PARTNER_DENORM_FULL', '{}', TRUE, '0 0 */6 * * ?', TRUE)
ON DUPLICATE KEY UPDATE `DESCRIPTION` = VALUES(`DESCRIPTION`);
