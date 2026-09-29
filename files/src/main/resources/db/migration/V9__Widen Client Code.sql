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

-- EDITED 2026-09-29, after this migration broke stage and production.
--
-- It originally carried a fourth statement, between the two below:
--
--     ALTER TABLE `files`.`files_access_path_backup` MODIFY COLUMN `CLIENT_CODE` CHAR(12) ...
--
-- `files_access_path_backup` was a hand-made snapshot that existed on DEV ONLY, taken
-- 2026-09-16 before altering the live table. MySQL DDL is not transactional, so on stage and
-- production statement 1 applied, statement 2 failed on the missing table, and statements 3 and
-- 4 never ran -- leaving `flyway_schema_history` with `version=9, success=0`. Flyway then
-- refused to validate on every boot, `flywayInitializer` failed, and files-server would not
-- start. It fell back to the previous colour on every deploy for three weeks without anyone
-- being told, because the CI workflow piped keepup.sh into `tee` and lost its exit code.
--
-- It passed CI because CI ran it on dev, which was the one environment where the table existed.
-- A migration may only touch tables that its own migrations created. The snapshot was dropped
-- from dev on 2026-09-28 (dumped first to ~/backups on the jumphost; 426 rows).
--
-- Removing the line changes this file's checksum, so the value recorded in
-- `flyway_schema_history` has to be realigned on every environment that already applied V9, or
-- validation fails there instead. Do not deploy files-server until that is done. The checksum is
-- deliberately not quoted here: it is computed over this file including these comments, so
-- writing it down would change it.
ALTER TABLE `files`.`files_access_path`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client code';
ALTER TABLE `files`.`files_file_system`
    MODIFY COLUMN `CODE` CHAR(12) NOT NULL COMMENT 'Client code';
ALTER TABLE `files`.`files_upload_download`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code to whom the folder belongs to';
