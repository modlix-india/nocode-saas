USE `worker`;

DROP TABLE IF EXISTS `worker_task`;

-- create worker task/jobs
CREATE TABLE IF NOT EXISTS `worker_task`
(
    `ID`                 BIGINT UNSIGNED                                                   NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each Task',
    `NAME`               VARCHAR(255)                                                      NOT NULL COMMENT 'name of job',
    `CLIENT_ID`          BIGINT UNSIGNED                                                   NOT NULL COMMENT 'Identifier for the client to which this job belongs. References security_client table',
    `APP_ID`             BIGINT UNSIGNED                                                   NOT NULL COMMENT 'Identifier for the application to which this job belongs. References security_app table',
    `SCHEDULER_ID`       BIGINT UNSIGNED                                                   NOT NULL COMMENT 'Identifier for the scheduler to which this job belongs. References worker_scheduler table',
    `GROUP_NAME`         VARCHAR(255)                                                      NOT NULL COMMENT 'job group name',
    `DESCRIPTION`        VARCHAR(255)                                                               DEFAULT NULL COMMENT 'description about the job',
    `FUNCTION_NAME`      VARCHAR(32)                                                       NOT NULL COMMENT 'name of executable function',
    `FUNCTION_NAMESPACE` VARCHAR(32)                                                       NOT NULL COMMENT 'namespace of executable function',
    `FUNCTION_PARAMS`    JSON                                                                       DEFAULT NULL COMMENT 'parameters for the function',
    `STATE`              ENUM ('NONE', 'NORMAL', 'PAUSED', 'COMPLETE', 'ERROR', 'BLOCKED') NOT NULL DEFAULT 'NORMAL' COMMENT 'task triggering state',
    `JOB_TYPE`           ENUM ('SIMPLE', 'CRON')                                                    DEFAULT 'SIMPLE' NOT NULL COMMENT 'job type',
    `JOB_DATA`           JSON                                                                       DEFAULT NULL COMMENT 'job data',
    `DURABLE`            BOOLEAN                                                           NOT NULL DEFAULT FALSE COMMENT 'if we want to keep job even if it does not have any trigger',
    `START_TIME`         TIMESTAMP                                                         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'task start datetime',
    `END_TIME`           TIMESTAMP                                                                  DEFAULT NULL COMMENT 'task end datetime',
    `SCHEDULE`           VARCHAR(255)                                                      NOT NULL COMMENT 'job schedule expression for simple/cron job',
    `REPEAT_INTERVAL`    INT                                                                        DEFAULT NULL COMMENT 'total times this job will repeat, only applicable for simple jobs',
    `RECOVERABLE`        BOOLEAN                                                                    DEFAULT TRUE COMMENT 're-run the job if the scheduler crashed before finishing',
    `NEXT_FIRE_TIME`     TIMESTAMP                                                                  DEFAULT NULL COMMENT 'upcoming execution at',
    `LAST_FIRE_TIME`     TIMESTAMP                                                                  DEFAULT NULL COMMENT 'last execution at',
    `LAST_FIRE_STATUS`   ENUM ('SUCCESS', 'FAILED')                                                 DEFAULT NULL COMMENT 'last task execution status',
    `LAST_FIRE_RESULT`   TEXT                                                                       DEFAULT NULL COMMENT 'last execution log',

    `CREATED_BY`         BIGINT UNSIGNED                                                            DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`         TIMESTAMP                                                         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`         BIGINT UNSIGNED                                                            DEFAULT NULL COMMENT 'ID of the user who last updated this row',
    `UPDATED_AT`         TIMESTAMP                                                         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is last updated',
    PRIMARY KEY (`ID`),
    UNIQUE KEY `UNQ_WORKER_TASK_NAME_GROUP` (`NAME`, `GROUP_NAME`),
    INDEX `IDX_WORKER_TASK_NAME` (`NAME`),
    CONSTRAINT `FK_WORKER_TASK_SCHEDULER` FOREIGN KEY (`SCHEDULER_ID`) REFERENCES `worker_scheduler` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

#     `MISFIRE_POLICY`  INT   DEFAULT NULL COMMENT 'what to do if job trigger missed', -- not including right now, having different properties for simple and cron jobs
