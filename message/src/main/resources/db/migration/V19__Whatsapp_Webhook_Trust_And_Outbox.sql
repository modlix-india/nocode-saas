-- Inbound webhooks arrive with no way to reach the Meta app secret they must be verified against.
-- The chain is WABA -> WhatsApp connection -> its tokenConnection -> tokenDetails.queryParams
-- .client_secret, and the first link does not exist: nothing records which connection a business
-- account was synced from. Null on existing rows falls back to the configured default connection.
ALTER TABLE `message`.`message_whatsapp_business_accounts`
    ADD COLUMN `CONNECTION_NAME` VARCHAR(255) NULL COMMENT 'Core connection this business account was synced from. Resolves the Meta app secret used to verify inbound webhook signatures.' AFTER `WHATSAPP_BUSINESS_ACCOUNT_ID`;

-- Which service owns conversations on this number. The message service routes inbound to it and
-- never learns what the conversation means. Orthogonal to PRODUCT_ID: a default number with no
-- product still has an owner. Null is unrouted and must park loudly, never drop.
ALTER TABLE `message`.`message_whatsapp_phone_numbers`
    ADD COLUMN `OWNER_SERVICE` VARCHAR(64) NULL COMMENT 'Eureka service id owning conversations on this number, e.g. entity-processor.' AFTER `PRODUCT_ID`;

-- Handoff outbox for the message -> owning service hop.
--
-- Rows are committed before dispatch and deleted on a 2xx, so this table is normally EMPTY and a
-- non-empty one is itself the alert. That is why there is no processed flag: see
-- message_message_webhooks.IS_PROCESSED, which sits at 33k unprocessed against 141 processed
-- because it is only set when a whole event succeeds.
--
-- The 200 to the provider is returned once a row here is durable, not once the consumer has
-- accepted it, so a consumer outage cannot make us look unavailable to Meta or Exotel.
--
-- Channel-agnostic on purpose. WhatsApp and calls have the same handoff problem and the same
-- answer, and one table means one sweeper, one backoff and one row count to watch rather than two
-- of each drifting apart.
CREATE TABLE `message`.`message_dispatch_outbox`
(
    `ID`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`        CHAR(64)        NOT NULL COMMENT 'App Code this handoff belongs to.',
    `CLIENT_CODE`     CHAR(8)         NOT NULL COMMENT 'Client Code this handoff belongs to.',
    `CODE`            CHAR(22)        NOT NULL COMMENT 'Unique Code to identify this row.',

    `OWNER_SERVICE`   VARCHAR(64)     NOT NULL COMMENT 'Dispatch target, taken from the number the event arrived on.',
    `CHANNEL`         ENUM ('WHATSAPP', 'CALL') NOT NULL COMMENT 'Which handler family this routes to.',
    `EVENT_KEY`       VARCHAR(128)    NOT NULL COMMENT 'The provider''s own id for the thing: Meta message id, or Exotel call Sid. The idempotency key, which the consumer upserts on, so replay and out-of-order delivery are both safe.',
    `EVENT_TYPE`      ENUM ('INBOUND_MESSAGE', 'MESSAGE_STATUS', 'CALL_STATUS') NOT NULL COMMENT 'What happened. Every kind rides the same outbox on the same key.',
    `PAYLOAD`         JSON            NOT NULL COMMENT 'Full dispatch body, so a replay needs no re-parse of the original webhook.',

    `ATTEMPTS`        INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT 'Dispatch attempts so far. Drives the sweeper backoff.',
    `LAST_ERROR`      TEXT            NULL COMMENT 'Last dispatch failure, for diagnosis without log archaeology.',
    `NEXT_ATTEMPT_AT` DATETIME        NULL COMMENT 'Earliest next retry. Null means ready now.',

    `CREATED_BY`      BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`      BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_DISPATCH_OUTBOX_CODE` (`CODE`),
    -- One row per (channel, thing, event kind). A duplicate webhook delivery must not enqueue
    -- twice. Channel is in the key because two providers' id spaces are unrelated and a collision,
    -- however unlikely, would silently drop one of them.
    UNIQUE KEY `UK2_DISPATCH_OUTBOX_EVENT` (`CHANNEL`, `EVENT_KEY`, `EVENT_TYPE`),
    KEY `IDX1_DISPATCH_OUTBOX_SWEEP` (`NEXT_ATTEMPT_AT`, `ATTEMPTS`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;

-- Which service owns this call, the call-side twin of the column added above.
--
-- On the call rather than on a number, unlike WhatsApp, because Exotel numbers are configured per
-- product in entity_processor_product_comms and this service has no table of them to hang an owner
-- off. Stamped when the call is created, which both entry points can do because both are initiated
-- by the owning service, and read back when a status callback arrives with nothing but a Sid.
ALTER TABLE `message`.`message_exotel_calls`
    ADD COLUMN `OWNER_SERVICE` VARCHAR(64) NULL COMMENT 'Eureka service id owning this call, e.g. entity-processor. Null is unrouted and must park loudly, never drop.' AFTER `ACCOUNT_SID`;
