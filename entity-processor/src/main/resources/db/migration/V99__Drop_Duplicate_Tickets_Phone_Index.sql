-- IDX9_TICKETS_APP_CLIENT_PHONE duplicates IDX7_TICKETS_AC_CC_PHONE exactly: the same three
-- columns, in the same order, on the same table. V85 added IDX7 and V94 added IDX9 nine
-- migrations later, its comment noting "there was no index for that shape" -- there was.
--
-- A duplicate index is not free. Every insert, update and delete on entity_processor_tickets
-- maintains both, and both compete for the same buffer pool. The table is 328MB across 154,881
-- rows on production, so this is real write amplification for no read benefit: the optimizer can
-- only ever choose one of them.
--
-- IDX7 is kept because it is the older of the two and other migrations reference that name.
--
-- Written as a conditional drop rather than a plain DROP INDEX because the index was also
-- dropped by hand on dev, stage and production when this was found, ahead of the next deploy.
-- A bare DROP INDEX would then fail on exactly those environments and take the whole migration
-- chain -- and with it the service's startup -- down with it. MySQL has no
-- DROP INDEX IF EXISTS, so the check has to be written out.

SET @index_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'entity_processor_tickets'
      AND INDEX_NAME = 'IDX9_TICKETS_APP_CLIENT_PHONE'
);

SET @drop_sql := IF(
    @index_exists > 0,
    'DROP INDEX `IDX9_TICKETS_APP_CLIENT_PHONE` ON `entity_processor_tickets`',
    'DO 0'
);

PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
