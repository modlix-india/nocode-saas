USE `worker`;

DROP TABLE IF EXISTS `worker_scheduler`;

-- Create Scheduler Table
CREATE TABLE IF NOT EXISTS `worker_scheduler`
(
    `ID`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key, unique identifier for each Task',
    `NAME`        VARCHAR(100)    NOT NULL COMMENT 'name of the scheduler',
    `STATUS`      ENUM ('STARTED', 'STANDBY', 'SHUTDOWN') DEFAULT 'STARTED' NOT NULL COMMENT 'scheduler running flag',
    `INSTANCE_ID` VARCHAR(32)     NOT NULL COMMENT 'scheduler instance id',

    `CREATED_BY`  BIGINT UNSIGNED                         DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT`  TIMESTAMP       NOT NULL                DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',
    `UPDATED_BY`  BIGINT UNSIGNED                         DEFAULT NULL COMMENT 'ID of the user who last updated this row',
    `UPDATED_AT`  TIMESTAMP       NOT NULL                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is last updated',
    PRIMARY KEY (`ID`),
    UNIQUE KEY (NAME)
);