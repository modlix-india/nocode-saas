-- Client codes widened from CHAR(8) to CHAR(12).
--
-- `ClientDAO.getValidClientCode` builds a code from at most the first five
-- characters of the client's name and appends a collision counter -- KAILA,
-- KAILA1, KAILA2 -- so a popular name stem eats the namespace one registration
-- at a time, and CHAR(8) left room for only three digits. Measured before the
-- change: 304 clients already sat at exactly eight characters, KAILA alone held
-- 294 of its 999, and the code after KAILA999 is nine characters, which strict
-- mode rejects outright with `ERROR 1406: Data too long`. Registration would
-- simply have stopped for those names.
--
-- CHAR rather than VARCHAR, and 12 rather than 64: a client code is a short
-- uppercase token, and CHAR(64) in utf8mb4 would reserve 256 bytes per row
-- across ~70 columns to hold five to eight characters. Twelve gives the counter
-- seven digits, and stays under the thirteen-character limit in
-- `SecuredFileResourceService.checkReadAccessWithClientCode`, so no deploy order
-- between this and that parser fix can break secured file access.
--
-- This schema holds COPIES of `security.security_client.CODE`, which is the
-- source of truth and is widened in its own migration. Until every schema has
-- this, no code longer than eight characters may be issued.
--
-- App codes are deliberately untouched: `AppDAO.generateAppCode` appends a
-- random base36 suffix and regenerates on collision instead of counting, and
-- every APP_CODE column is already 64 wide.

ALTER TABLE `adzump`.`adzump_action_audit`
    MODIFY COLUMN `client_code` CHAR(12) NOT NULL COMMENT 'Client code';
ALTER TABLE `adzump`.`adzump_asset`
    MODIFY COLUMN `client_code` CHAR(12) NOT NULL COMMENT 'Client code (tenant-private)';
ALTER TABLE `adzump`.`adzump_autonomy_config`
    MODIFY COLUMN `client_code` CHAR(12) NOT NULL COMMENT 'Client code';
ALTER TABLE `adzump`.`adzump_campaign_plan`
    MODIFY COLUMN `client_code` CHAR(12) NOT NULL COMMENT 'Client code';
ALTER TABLE `adzump`.`adzump_competition_research`
    MODIFY COLUMN `client_code` CHAR(12) NOT NULL COMMENT 'Client code (tenant-private)';
ALTER TABLE `adzump`.`adzump_creative_attribute`
    MODIFY COLUMN `client_code` CHAR(12) NOT NULL COMMENT 'Client code';
ALTER TABLE `adzump`.`adzump_experiment`
    MODIFY COLUMN `client_code` CHAR(12) NOT NULL COMMENT 'Client code (tenant-private)';
ALTER TABLE `adzump`.`adzump_milestone_mapping`
    MODIFY COLUMN `client_code` CHAR(12) NOT NULL COMMENT 'Client code';
ALTER TABLE `adzump`.`adzump_performance_policy`
    MODIFY COLUMN `client_code` CHAR(12) NOT NULL COMMENT 'Client code';
ALTER TABLE `adzump`.`adzump_performance_snapshot`
    MODIFY COLUMN `client_code` CHAR(12) NOT NULL COMMENT 'Client code';
