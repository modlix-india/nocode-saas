-- Add SSL_RENEWAL to TASK_JOB_TYPE enum
ALTER TABLE worker_tasks
    MODIFY COLUMN TASK_JOB_TYPE ENUM ('SIMPLE', 'CRON', 'SSL_RENEWAL') NOT NULL DEFAULT 'SIMPLE';

-- Create ssl-renewal scheduler (if not exists)
INSERT IGNORE INTO worker_schedulers (NAME, SCHEDULER_STATUS, INSTANCE_ID)
VALUES ('ssl-renewal', 'STARTED', 'default');

-- Create SSL renewal task
-- CLIENT_ID=1 and APP_ID=2 reference system client and appbuilder app from security seed
INSERT IGNORE INTO worker_tasks (
    NAME,
    CLIENT_ID,
    APP_ID,
    SCHEDULER_ID,
    GROUP_NAME,
    DESCRIPTION,
    TASK_STATE,
    TASK_JOB_TYPE,
    JOB_DATA,
    DURABLE,
    SCHEDULE,
    START_TIME,
    RECOVERABLE
)
SELECT 'ssl-certificate-renewal',
       1,
       2,
       (SELECT ID FROM worker_schedulers WHERE NAME = 'ssl-renewal' LIMIT 1),
       'ssl-renewal',
       'Renews expiring SSL certificates for client URLs (HTTP challenge only)',
       'NORMAL',
       'SSL_RENEWAL',
       '{"daysBeforeExpiry": 30}',
       TRUE,
       '0 0 2 * * ?',
       NOW(),
       TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM worker_tasks WHERE NAME = 'ssl-certificate-renewal' AND GROUP_NAME = 'ssl-renewal'
);
