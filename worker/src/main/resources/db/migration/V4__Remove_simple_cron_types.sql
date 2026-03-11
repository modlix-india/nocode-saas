use worker;
-- Remove SIMPLE and CRON from TASK_JOB_TYPE enum, keep only SSL_RENEWAL and TOKEN_CLEANUP
ALTER TABLE `worker_tasks`
    MODIFY COLUMN `TASK_JOB_TYPE` ENUM('SSL_RENEWAL', 'TOKEN_CLEANUP') NOT NULL DEFAULT 'SSL_RENEWAL';

-- Drop the repeat_interval column (no longer needed without SIMPLE scheduling)
ALTER TABLE `worker_tasks`
    DROP COLUMN `REPEAT_INTERVAL`;
