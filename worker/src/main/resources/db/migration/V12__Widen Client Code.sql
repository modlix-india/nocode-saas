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

ALTER TABLE `worker`.`worker_client_schedule_controls`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client code; scheduling is controlled per app and client.';
ALTER TABLE `worker`.`worker_tasks`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client code for the client who created this task.';
