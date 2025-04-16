USE `worker`;

-- Create Scheduler Table
CREATE TABLE `worker_scheduler`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each Task',
    `name`            VARCHAR(100)    NOT NULL,
    `status`          VARCHAR(20)     NOT NULL,
    `is_running`      BOOLEAN                  DEFAULT FALSE,
    `is_standby_mode` BOOLEAN                  DEFAULT FALSE,
    `is_shutdown`     BOOLEAN                  DEFAULT FALSE,
    `start_time`      TIMESTAMP,
    `CREATED_BY`      BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`      BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who last updated this row',
    `UPDATED_AT`      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is last updated',
    PRIMARY KEY (`ID`),
    CONSTRAINT uk_scheduler_name UNIQUE (name)
);

-- Modify Task table to include scheduler reference
ALTER TABLE `worker_task`
    ADD COLUMN `scheduler` BIGINT UNSIGNED NOT NULL,
    ADD CONSTRAINT fk_worker_task_scheduler FOREIGN KEY (`scheduler`) REFERENCES `worker_scheduler` (`id`);
