-- A draft surface is one extra row in security_client_url: the same CLIENT_ID and
-- APP_CODE, a generated hostname as URL_PATTERN, and URL_TYPE = 'DRAFT'.
--
-- The uuid needs no column of its own. UK1_URL_PATTERN already makes it globally
-- unique, and the gateway already resolves a host to (clientCode, appCode) through
-- this table, so a draft host costs no new lookup.
--
-- Every existing row is LIVE, which the default handles, so nothing needs
-- backfilling.
ALTER TABLE `security`.`security_client_url`
ADD COLUMN `URL_TYPE` ENUM('LIVE','DRAFT') NOT NULL DEFAULT 'LIVE'
  COMMENT 'LIVE serves published content; DRAFT serves the app draft surface'
AFTER `APP_CODE`;

-- Resolution is by hostname, and the gateway looks up every request, so the index
-- carries URL_TYPE to keep that a covering read.
CREATE INDEX `IDX1_CLIENT_URL_TYPE` ON `security`.`security_client_url` (`URL_TYPE`, `CLIENT_ID`, `APP_CODE`);
