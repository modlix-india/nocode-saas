-- Removing the entity-processor schedules that leadzumptwo now runs as core event schedules.
--
-- Each of these drove an entity-processor endpoint for leadzump. leadzumptwo has no entity-processor:
-- the same work is an EventDefinition with a schedule and an EventAction calling a LeadZumpV2
-- function, claimed per client by core's scheduler.
--
--   partner-denorm-delta, partner-denorm-full   partner rows' client and user fields (LZ_PARTNER_DENORM,
--                                               later security change events)
--   campaign-metrics-sync, campaign-discovery-sync   campaign metrics and adset/ad mirror
--   conversions-api-dispatch                    conversions outbox drain to Meta CAPI / Google
--
-- Run only where leadzump has been cut over to leadzumptwo. Anywhere leadzump still runs on
-- entity-processor, this stops its partner denorm, campaign sync and conversions with nothing in
-- their place.
--
-- entity-processor-metering stays: billing is decided separately.
--
-- A task row alone is not the schedule. The worker puts every NORMAL task into Quartz's job store
-- at startup (job and trigger named after the task, group = client code, here SYSTEM), and the
-- trigger keeps firing from those tables whether or not the task row exists; TaskExecutorJob would
-- then log "Task not found" on every tick. So the Quartz rows go first, children before parents
-- (fired, per-type trigger, trigger, job), then the task rows. SCHED_NAME is left open so this holds
-- whatever the scheduler instance is called in an environment.
--
-- The TASK_JOB_TYPE enum keeps its values: the Java enum and the execution services still name
-- them, and narrowing the column belongs with removing that code.

DELETE FROM `worker`.`QRTZ_FIRED_TRIGGERS`
WHERE `TRIGGER_GROUP` = 'SYSTEM'
  AND `TRIGGER_NAME` IN ('partner-denorm-delta', 'partner-denorm-full', 'campaign-metrics-sync',
                         'campaign-discovery-sync', 'conversions-api-dispatch');

DELETE FROM `worker`.`QRTZ_CRON_TRIGGERS`
WHERE `TRIGGER_GROUP` = 'SYSTEM'
  AND `TRIGGER_NAME` IN ('partner-denorm-delta', 'partner-denorm-full', 'campaign-metrics-sync',
                         'campaign-discovery-sync', 'conversions-api-dispatch');

DELETE FROM `worker`.`QRTZ_SIMPLE_TRIGGERS`
WHERE `TRIGGER_GROUP` = 'SYSTEM'
  AND `TRIGGER_NAME` IN ('partner-denorm-delta', 'partner-denorm-full', 'campaign-metrics-sync',
                         'campaign-discovery-sync', 'conversions-api-dispatch');

DELETE FROM `worker`.`QRTZ_SIMPROP_TRIGGERS`
WHERE `TRIGGER_GROUP` = 'SYSTEM'
  AND `TRIGGER_NAME` IN ('partner-denorm-delta', 'partner-denorm-full', 'campaign-metrics-sync',
                         'campaign-discovery-sync', 'conversions-api-dispatch');

DELETE FROM `worker`.`QRTZ_BLOB_TRIGGERS`
WHERE `TRIGGER_GROUP` = 'SYSTEM'
  AND `TRIGGER_NAME` IN ('partner-denorm-delta', 'partner-denorm-full', 'campaign-metrics-sync',
                         'campaign-discovery-sync', 'conversions-api-dispatch');

DELETE FROM `worker`.`QRTZ_TRIGGERS`
WHERE `JOB_GROUP` = 'SYSTEM'
  AND `JOB_NAME` IN ('partner-denorm-delta', 'partner-denorm-full', 'campaign-metrics-sync',
                     'campaign-discovery-sync', 'conversions-api-dispatch');

DELETE FROM `worker`.`QRTZ_JOB_DETAILS`
WHERE `JOB_GROUP` = 'SYSTEM'
  AND `JOB_NAME` IN ('partner-denorm-delta', 'partner-denorm-full', 'campaign-metrics-sync',
                     'campaign-discovery-sync', 'conversions-api-dispatch');

DELETE FROM `worker`.`worker_tasks`
WHERE `CLIENT_CODE` = 'SYSTEM'
  AND `APP_CODE` IS NULL
  AND `TASK_JOB_TYPE` IN ('PARTNER_DENORM_DELTA', 'PARTNER_DENORM_FULL', 'CAMPAIGN_METRICS_SYNC',
                          'CAMPAIGN_DISCOVERY_SYNC', 'CONVERSIONS_API_DISPATCH');
