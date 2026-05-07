-- Update partner-denorm-delta schedule to run every 5 minutes from minute 5 through 55
UPDATE `worker`.`worker_tasks`
SET `SCHEDULE` = '0 5-55/5 * * * ?'
WHERE `CLIENT_CODE` = 'SYSTEM'
  AND `APP_CODE` IS NULL
  AND `NAME` = 'partner-denorm-delta';
