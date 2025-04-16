USE `worker`;

CREATE TABLE `worker_task` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each Task',
    `job_name` VARCHAR(255) NOT NULL COMMENT 'name of job',
    `cron_expression` VARCHAR(100) NOT NULL COMMENT 'job schedule expression',
    `next_execution_time` TIMESTAMP DEFAULT  NULL COMMENT  'upcoming execution at',
    `last_execution_time` TIMESTAMP DEFAULT NULL COMMENT  'last execution at',
    `status` ENUM('UPCOMING', 'FINISHED', 'FAILED', 'RUNNING') NOT NULL COMMENT 'JOB Status',
    `last_execution_result` TEXT,

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who last updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is last updated',
    PRIMARY KEY (`ID`),
    INDEX `idx_scheduled_tasks_status` (`status`),
    INDEX `idx_scheduled_tasks_job_name` (`job_name`)
)ENGINE = InnoDB
 DEFAULT CHARSET = `utf8mb4`
 COLLATE = `utf8mb4_unicode_ci`;