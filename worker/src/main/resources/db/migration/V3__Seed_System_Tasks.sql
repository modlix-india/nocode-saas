-- Seed a system-level client schedule control for internal tasks
INSERT INTO `worker`.`worker_client_schedule_controls` (`APP_CODE`, `CLIENT_CODE`, `NAME`, `SCHEDULER_STATUS`)
VALUES (NULL, 'SYSTEM', 'System Internal Tasks', 'STARTED')
ON DUPLICATE KEY UPDATE `NAME` = VALUES(`NAME`);

SET @csc_id = (SELECT `ID` FROM `worker`.`worker_client_schedule_controls` WHERE `CLIENT_CODE` = 'SYSTEM' AND `APP_CODE` IS NULL LIMIT 1);

-- Seed SSL Renewal task: daily at 2 AM
INSERT INTO `worker`.`worker_tasks`
    (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CLIENT_SCHEDULE_CONTROL_ID`, `DESCRIPTION`,
     `TASK_STATE`, `TASK_JOB_TYPE`, `JOB_DATA`, `DURABLE`, `SCHEDULE`, `REPEAT_INTERVAL`, `RECOVERABLE`)
VALUES (NULL, 'SYSTEM', 'ssl-certificate-renewal', @csc_id, 'Renew expiring SSL certificates',
        'NORMAL', 'SSL_RENEWAL', '{"daysBeforeExpiry": 3}', TRUE, '0 0 2 * * ?', NULL, TRUE)
ON DUPLICATE KEY UPDATE `DESCRIPTION` = VALUES(`DESCRIPTION`);

-- Seed Token Cleanup task: daily at 3 AM
INSERT INTO `worker`.`worker_tasks`
    (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CLIENT_SCHEDULE_CONTROL_ID`, `DESCRIPTION`,
     `TASK_STATE`, `TASK_JOB_TYPE`, `JOB_DATA`, `DURABLE`, `SCHEDULE`, `REPEAT_INTERVAL`, `RECOVERABLE`)
VALUES (NULL, 'SYSTEM', 'expired-token-cleanup', @csc_id, 'Delete expired and unused security and core tokens',
        'NORMAL', 'TOKEN_CLEANUP', '{"unusedDays": 90}', TRUE, '0 0 3 * * ?', NULL, TRUE)
ON DUPLICATE KEY UPDATE `DESCRIPTION` = VALUES(`DESCRIPTION`), `JOB_DATA` = VALUES(`JOB_DATA`);
