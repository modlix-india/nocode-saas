-- An editing session's draft surface, as its own short-lived hostname.
--
-- Deliberately NOT a row in security_client_url, which is where the permanent
-- draft link lives. Three queries in ClientUrlDAO already have to filter
-- URL_TYPE = 'LIVE' to keep draft hosts out of the general URL readers, and the
-- "latest URL" query orders by last updated and takes one -- a token minted every
-- time somebody opens the page editor would keep winning that and become the
-- app's canonical URL. A separate table cannot be reached by any of them.
--
-- The hostname is `t-<TOKEN><appCodeSuffix>.modlix.com`, so only the 32 hex
-- characters are stored; the rest is derived from configuration at mint time and
-- re-derived when the gateway resolves. The wildcard certificate already covers
-- the name, so there is no DNS or certificate work per token.

CREATE TABLE `security`.`security_draft_token`
(
    `ID`         bigint unsigned                                           NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `TOKEN`      char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '128 bits of SecureRandom as lowercase hex; the hostname label is this with a t- prefix',
    `APP_CODE`   char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'The one app this token grants the draft surface for',
    `CLIENT_ID`  bigint unsigned                                           NOT NULL COMMENT 'Client that minted it; the client being previewed must be this one or one it manages',
    `USER_ID`    bigint unsigned                                           NOT NULL COMMENT 'User that minted it, for audit',
    `EXPIRES_AT` timestamp                                                 NOT NULL COMMENT 'Extended in place by the heartbeat while the editor is open; never rotated within a session',

    `CREATED_BY` bigint unsigned                                           DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` timestamp                                                 NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created',

    PRIMARY KEY (`ID`),

    -- Resolution is by token on every request from a preview iframe, so this is
    -- the read that has to be cheap. Unique because the token IS the credential.
    UNIQUE KEY `UK1_DRAFT_TOKEN` (`TOKEN`),

    KEY `IDX1_DRAFT_TOKEN_EXPIRES_AT` (`EXPIRES_AT`),

    KEY `FK1_DRAFT_TOKEN_CLIENT_ID` (`CLIENT_ID`),
    CONSTRAINT `FK1_DRAFT_TOKEN_CLIENT_ID` FOREIGN KEY (`CLIENT_ID`) REFERENCES `security_client` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT,

    KEY `FK2_DRAFT_TOKEN_USER_ID` (`USER_ID`),
    CONSTRAINT `FK2_DRAFT_TOKEN_USER_ID` FOREIGN KEY (`USER_ID`) REFERENCES `security_user` (`ID`) ON DELETE CASCADE ON UPDATE RESTRICT
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;
