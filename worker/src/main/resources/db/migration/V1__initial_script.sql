CREATE DATABASE IF NOT EXISTS `worker`;

DROP TABLE IF EXISTS `worker`.`worker_client_schedule_controls`;

CREATE TABLE IF NOT EXISTS `worker`.`worker_client_schedule_controls`
(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key and unique identifier for each client schedule control.',
    `APP_CODE` CHAR(64) NULL COMMENT 'App code; scheduling is controlled per app and client.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client code; scheduling is controlled per app and client.',
    `NAME` VARCHAR(255) DEFAULT NULL COMMENT 'Optional display name for this control.',
    `SCHEDULER_STATUS` ENUM ('STARTED', 'STANDBY', 'SHUTDOWN') DEFAULT 'STARTED' NOT NULL COMMENT 'Logical scheduling state for this app and client group.',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Timestamp when this row was created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who last updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Timestamp when this row was last updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_CLIENT_SCHEDULE_CONTROLS_APP_CLIENT` (`APP_CODE`, `CLIENT_CODE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

DROP TABLE IF EXISTS `worker`.`worker_tasks`;

CREATE TABLE IF NOT EXISTS `worker`.`worker_tasks`
(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key and unique identifier for each task.',
    `APP_CODE` CHAR(64) NULL COMMENT 'App code on which this task was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client code for the client who created this task.',
    `NAME` VARCHAR(255) NOT NULL COMMENT 'Name of the job.',
    `CLIENT_SCHEDULE_CONTROL_ID` BIGINT UNSIGNED NOT NULL COMMENT 'References the client schedule control row in worker_client_schedule_controls.',
    `DESCRIPTION` VARCHAR(255) DEFAULT NULL COMMENT 'Description of the job.',
    `TASK_STATE` ENUM ('NONE', 'NORMAL', 'PAUSED', 'COMPLETE', 'ERROR', 'BLOCKED') NOT NULL DEFAULT 'NORMAL' COMMENT 'Current task triggering state.',
    `TASK_JOB_TYPE` ENUM ('SIMPLE', 'CRON', 'SSL_RENEWAL', 'TICKET_EXPIRATION') NOT NULL DEFAULT 'SIMPLE' COMMENT 'Type of the job.',
    `JOB_DATA` JSON DEFAULT NULL COMMENT 'FunctionExecutionSpec: functionName, functionNamespace, functionParams (Map).',
    `DURABLE` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'If true, the job is kept in the store even when it has no triggers.',
    `START_TIME` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Scheduled start time for the task.',
    `END_TIME` TIMESTAMP DEFAULT NULL COMMENT 'Scheduled end time for the task.',
    `SCHEDULE` VARCHAR(255) NOT NULL COMMENT 'Schedule expression for simple or cron jobs.',
    `REPEAT_INTERVAL` INT DEFAULT NULL COMMENT 'Number of times this job repeats; only applicable for simple jobs.',
    `RECOVERABLE` BOOLEAN DEFAULT TRUE COMMENT 'If true, the job is re-run if the scheduler crashed before it finished.',
    `NEXT_FIRE_TIME` TIMESTAMP DEFAULT NULL COMMENT 'Next scheduled execution time.',
    `LAST_FIRE_TIME` TIMESTAMP DEFAULT NULL COMMENT 'Last execution time.',
    `TASK_LAST_FIRE_STATUS` ENUM ('SUCCESS', 'FAILED') DEFAULT NULL COMMENT 'Status of the last task execution.',
    `LAST_FIRE_RESULT` TEXT DEFAULT NULL COMMENT 'Result or log of the last execution.',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Timestamp when this row was created.',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who last updated this row.',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Timestamp when this row was last updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_TASKS_NAME_APP_CLIENT` (`NAME`, `APP_CODE`, `CLIENT_CODE`),
    INDEX `IDX1_TASKS_NAME` (`NAME`),
    CONSTRAINT `FK1_TASKS_CLIENT_SCHEDULE_CONTROL_ID` FOREIGN KEY (`CLIENT_SCHEDULE_CONTROL_ID`) REFERENCES `worker_client_schedule_controls` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
