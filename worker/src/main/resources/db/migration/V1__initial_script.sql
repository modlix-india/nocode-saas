CREATE DATABASE IF NOT EXISTS `worker`;

DROP TABLE IF EXISTS `worker`.`worker_schedulers`;

CREATE TABLE IF NOT EXISTS `worker`.`worker_schedulers`
(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each scheduler',
    `NAME` VARCHAR(255) NOT NULL COMMENT 'name of the scheduler',
    `SCHEDULER_STATUS` ENUM ('STARTED', 'STANDBY', 'SHUTDOWN') DEFAULT 'STARTED' NOT NULL COMMENT 'Scheduler running flag.',
    `INSTANCE_ID` VARCHAR(32) NOT NULL COMMENT 'scheduler instance id',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who last updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is last updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_SCHEDULERS_NAME` (`NAME`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;


DROP TABLE IF EXISTS `worker`.`worker_tasks`;

CREATE TABLE IF NOT EXISTS `worker`.`worker_tasks`
(
    `ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each task',
    `APP_CODE` CHAR(64) NOT NULL COMMENT 'App Code on which this task was created.',
    `CLIENT_CODE` CHAR(8) NOT NULL COMMENT 'Client Code who created this task.',
    `NAME` VARCHAR(255) NOT NULL COMMENT 'name of job',
    `SCHEDULER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Identifier for the scheduler. References worker_schedulers table',
    `GROUP_NAME` VARCHAR(255) NOT NULL COMMENT 'job group name',
    `DESCRIPTION` VARCHAR(255) DEFAULT NULL COMMENT 'description about the job',
    `TASK_STATE` ENUM ('NONE', 'NORMAL', 'PAUSED', 'COMPLETE', 'ERROR', 'BLOCKED') NOT NULL DEFAULT 'NORMAL' COMMENT 'Task triggering state.',
    `TASK_JOB_TYPE` ENUM ('SIMPLE', 'CRON', 'SSL_RENEWAL', 'TICKET_EXPIRATION') NOT NULL DEFAULT 'SIMPLE' COMMENT 'Job type.',
    `JOB_DATA` JSON DEFAULT NULL COMMENT 'FunctionExecutionSpec: functionName, functionNamespace, functionParams (Map)',
    `DURABLE` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'if we want to keep job even if it does not have any trigger',
    `START_TIME` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'task start datetime',
    `END_TIME` TIMESTAMP DEFAULT NULL COMMENT 'task end datetime',
    `SCHEDULE` VARCHAR(255) NOT NULL COMMENT 'job schedule expression for simple/cron job',
    `REPEAT_INTERVAL` INT DEFAULT NULL COMMENT 'total times this job will repeat, only applicable for simple jobs',
    `RECOVERABLE` BOOLEAN DEFAULT TRUE COMMENT 're-run the job if the scheduler crashed before finishing',
    `NEXT_FIRE_TIME` TIMESTAMP DEFAULT NULL COMMENT 'upcoming execution at',
    `LAST_FIRE_TIME` TIMESTAMP DEFAULT NULL COMMENT 'last execution at',
    `TASK_LAST_FIRE_STATUS` ENUM ('SUCCESS', 'FAILED') DEFAULT NULL COMMENT 'Last task execution status.',
    `LAST_FIRE_RESULT` TEXT DEFAULT NULL COMMENT 'last execution log',

    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who last updated this row',
    `UPDATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is last updated',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_TASKS_NAME_GROUP` (`NAME`, `GROUP_NAME`),
    INDEX `IDX1_TASKS_NAME` (`NAME`),
    CONSTRAINT `FK1_TASKS_SCHEDULER_ID` FOREIGN KEY (`SCHEDULER_ID`) REFERENCES `worker_schedulers` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
