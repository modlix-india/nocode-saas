-- Client codes were running out.
--
-- `ClientDAO.getValidClientCode` builds a code from at most the first FIVE
-- characters of the client's name and then appends a collision counter:
-- KAILA, KAILA1, KAILA2, ... So a popular prefix consumes the namespace one
-- registration at a time, and CHAR(8) allows only three digits of counter.
--
-- Measured before this change: 304 clients already sat at exactly 8 characters,
-- all with three-digit suffixes. KAILA alone held 294 codes, SHIVA 187, REVAN
-- 123. At KAILA999 the generator produces KAILA1000, which is nine characters,
-- and with strict mode on that is a hard `ERROR 1406: Data too long` — client
-- registration simply stops working for every name beginning with those five
-- letters.
--
-- CHAR, not VARCHAR, and 12, not 64. The type is right: a client code is a
-- short uppercase alphanumeric token with no trailing-space semantics, and
-- CHAR(64) in utf8mb4 would reserve 256 bytes per row across ~70 columns and
-- ~126 indexes to hold a value of five to eight characters. Twelve gives the
-- five-character prefix seven digits of counter — ten million per prefix,
-- against KAILA's 294 — and stays under the thirteen-character boundary in
-- `SecuredFileResourceService.checkReadAccessWithClientCode`, so this migration
-- does not depend on that parser fix shipping first. (That bug is real and
-- worth fixing; it just must not be a prerequisite for widening, because
-- getting the order wrong would 403 every secured file in the platform.)
--
-- App codes do NOT have this problem and are deliberately untouched:
-- `AppDAO.generateAppCode` appends a base36 random suffix and regenerates a
-- fresh one on collision rather than incrementing, and every APP_CODE column is
-- already 64 wide.
--
-- This is the source of truth. Every other schema carries a copy of this value
-- and is widened to match in its own migration; until all of them have landed,
-- no code longer than eight characters may be issued.

ALTER TABLE `security`.`security_client`
    MODIFY COLUMN `CODE` CHAR(12) NOT NULL COMMENT 'Client code';

ALTER TABLE `security`.`security_app_billing_config`
    MODIFY COLUMN `SUSPEND_CLIENT_CODE` CHAR(12) NULL COMMENT 'Client code to serve when suspended';
