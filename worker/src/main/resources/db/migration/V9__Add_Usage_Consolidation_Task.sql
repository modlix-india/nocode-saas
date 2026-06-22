-- Widen TASK_JOB_TYPE enum to include the 15-minute token-usage consolidation type
ALTER TABLE `worker`.`worker_tasks`
    MODIFY COLUMN `TASK_JOB_TYPE` ENUM('SSL_RENEWAL', 'TOKEN_CLEANUP', 'PARTNER_DENORM_DELTA', 'PARTNER_DENORM_FULL', 'CAMPAIGN_METRICS_SYNC', 'CAMPAIGN_DISCOVERY_SYNC', 'CONVERSIONS_API_DISPATCH', 'USAGE_CONSOLIDATION') NOT NULL DEFAULT 'SSL_RENEWAL';

SET @csc_id = (SELECT `ID` FROM `worker`.`worker_client_schedule_controls` WHERE `CLIENT_CODE` = 'SYSTEM' AND `APP_CODE` IS NULL LIMIT 1);

-- Token-usage consolidation: every 15 minutes on the boundary. Security reads
-- the durable consumption log for every closed window, groups by
-- (consumer, exposing client, app, action), debits the resolved wallet once per
-- group (idempotent per window), suspends wallets that cross the floor, and
-- purges consumed rows. The 15-minute cadence keeps negative balances tight.
INSERT INTO `worker`.`worker_tasks`
    (`APP_CODE`, `CLIENT_CODE`, `NAME`, `CLIENT_SCHEDULE_CONTROL_ID`, `DESCRIPTION`,
     `TASK_STATE`, `TASK_JOB_TYPE`, `JOB_DATA`, `DURABLE`, `SCHEDULE`, `RECOVERABLE`)
VALUES (NULL, 'SYSTEM', 'usage-consolidation', @csc_id, 'Consolidates the durable token-usage log into wallet debits every 15 minutes',
        'NORMAL', 'USAGE_CONSOLIDATION', '{}', TRUE, '0 0/15 * * * ?', TRUE)
ON DUPLICATE KEY UPDATE `DESCRIPTION` = VALUES(`DESCRIPTION`);
