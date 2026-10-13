-- One row per run of an event's EventAction, so an app's users can see whether an
-- event ran and, when it did not, why.
--
-- Every run is recorded: a scheduled tick or ScheduleEvent one-off, a CreateEvent,
-- a storage event, a WhatsApp or call hand-off. So are the outcomes that never reach
-- an action: a tick SKIPPED because the previous run was still going, an event that
-- arrived with NO_ACTION to run it, a raise that FAILED before anything ran.
--
-- TASKS holds one entry per task of the action, in the order they ran, with its own
-- status, duration and error, because "the action failed" is not something a user
-- can act on and "task sendWelcome failed: template X not found" is.
--
-- Times are UTC. DATETIME rather than TIMESTAMP so no session time zone ever
-- converts them on the way in or out.
--
-- The worker's EVENT_RUN_CLEANUP task (worker V13) deletes successful runs after 7 days and every
-- other row after 90, through POST /api/core/eventRuns/internal/cleanupOlderThan.

CREATE TABLE IF NOT EXISTS `core`.`core_event_runs`
(
    `ID`                BIGINT UNSIGNED                                           NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `APP_CODE`          CHAR(64)                                                  NOT NULL COMMENT 'App the event was raised in',
    `CLIENT_CODE`       CHAR(12)                                                  NOT NULL COMMENT 'Client the event was raised for',
    `EVENT_NAME`        VARCHAR(255)                                              NOT NULL COMMENT 'Event name, which is also the EventAction name',
    `TRIGGER_TYPE`      ENUM ('SCHEDULE', 'ONE_OFF', 'EVENT')                     NOT NULL COMMENT 'A cron tick, a ScheduleEvent one-off, or any other raise',
    `STATUS`            ENUM ('RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED', 'NO_ACTION') NOT NULL COMMENT 'Outcome of the run',
    `SCHEDULE_RUN_ID`   VARCHAR(64)                                               NULL COMMENT 'schedule_run id of a tick, scheduled_event id of a one-off',
    `SCHEDULED_FOR`     DATETIME(3)                                               NULL COMMENT 'The tick, or the time a one-off was due',
    `ATTEMPT`           INT UNSIGNED                                              NULL COMMENT 'Attempt number of a one-off',
    `IS_DRAFT`          TINYINT(1)                                                NOT NULL DEFAULT 0 COMMENT 'Ran on the draft surface',
    `TRIGGERED_BY`      BIGINT UNSIGNED                                           NULL COMMENT 'User who raised it; null for scheduled and system events',
    `TASK_COUNT`        SMALLINT UNSIGNED                                         NOT NULL DEFAULT 0 COMMENT 'Tasks in the action',
    `FAILED_TASK_COUNT` SMALLINT UNSIGNED                                         NOT NULL DEFAULT 0 COMMENT 'Tasks that failed',
    `TASKS`             JSON                                                      NULL COMMENT 'Per task: key, type, order, status, durationMs, error',
    `ERROR`             TEXT                                                      NULL COMMENT 'Why the run failed, was skipped or had nothing to run',
    `INSTANCE_ID`       VARCHAR(128)                                              NULL COMMENT 'Core instance that ran it',
    `STARTED_AT`        DATETIME(3)                                               NOT NULL COMMENT 'UTC',
    `FINISHED_AT`       DATETIME(3)                                               NULL COMMENT 'UTC; null while running',
    `DURATION_MS`       INT UNSIGNED                                              NULL COMMENT 'Milliseconds from start to finish',

    PRIMARY KEY (`ID`),
    KEY `K1_CLIENT_APP_EVENT_STARTED` (`CLIENT_CODE`, `APP_CODE`, `EVENT_NAME`, `STARTED_AT`),
    KEY `K2_CLIENT_APP_STARTED` (`CLIENT_CODE`, `APP_CODE`, `STARTED_AT`),
    KEY `K3_STATUS_STARTED` (`STATUS`, `STARTED_AT`),
    KEY `K4_STARTED` (`STARTED_AT`),
    -- "All clients" newest first: walked backwards by time with the client filtered
    -- in the index, so a page stops after its rows instead of sorting every client's.
    KEY `K5_APP_EVENT_STARTED_CLIENT` (`APP_CODE`, `EVENT_NAME`, `STARTED_AT`, `CLIENT_CODE`)
)
    ENGINE = INNODB
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
