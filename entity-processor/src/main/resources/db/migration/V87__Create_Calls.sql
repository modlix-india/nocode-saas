-- Calls move here from the message service, for the same reason WhatsApp messages did in V88: the
-- message service cannot answer "may this user see this call", because it has no idea what a deal
-- is. Today `GET /api/message/call/exotel/eager/query` filtered by customer phone number returns any
-- customer's call history to any authenticated user in the tenant, and the deal profile page is only
-- incidentally safe because the user navigated there from a deal they could already open.
--
-- The message service keeps the provider relationship: placing the call through Exotel, the connect
-- applet, the status callbacks, connection and token resolution. It routes a call event to whichever
-- service owns the number it arrived on and never learns what the call was about.
--
-- Two deliberate differences from the message-service shape:
--
-- 1. One table, not two. message_calls and message_exotel_calls are split provider-agnostic from
--    provider-specific, but message_calls already carries a hardcoded EXOTEL_CALL_ID column, so the
--    split does not actually buy provider independence. A second provider would want its own raw
--    payload columns here rather than a second join.
-- 2. TICKET_ID exists, which is the entire point of the move.
CREATE TABLE `entity_processor`.`entity_processor_calls`
(
    `ID`                            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`                      CHAR(64)        NOT NULL COMMENT 'App Code related to this call.',
    `CLIENT_CODE`                   CHAR(8)         NOT NULL COMMENT 'Client Code related to this call.',
    `USER_ID`                       BIGINT UNSIGNED NULL COMMENT 'Agent on this call. For inbound this is the deal assignee the call was routed to, which is not necessarily whoever created the row.',
    `CODE`                          CHAR(22)        NOT NULL COMMENT 'Unique Code to identify this row.',

    -- The provider's own call id (Exotel calls it the Sid) and the idempotency key for the whole
    -- handoff. Unique per tenant, because status callbacks arrive separately from the call that
    -- created the row and both write through an upsert on this.
    `PROVIDER_CALL_ID`              VARCHAR(64)     NULL COMMENT 'Provider call id (Exotel Sid). Idempotency key: every write path upserts on it.',
    `PARENT_CALL_SID`               VARCHAR(64)     NULL COMMENT 'Parent call id where the provider reports one.',
    `ACCOUNT_SID`                   VARCHAR(64)     NULL COMMENT 'Provider account the call was placed on.',

    `TICKET_ID`                     BIGINT UNSIGNED NULL COMMENT 'Deal this call was filed against. Null only for a call that could not be matched, which the inbound path avoids by creating the deal.',
    `PRODUCT_ID`                    BIGINT UNSIGNED NULL COMMENT 'Product scope, taken from the business number that placed or received the call.',

    `CONNECTION_NAME`               VARCHAR(255)    NULL COMMENT 'Core connection used for the call.',
    `CALL_PROVIDER`                 VARCHAR(64)     NULL COMMENT 'Provider name, e.g. EXOTEL.',
    `IS_OUTBOUND`                   TINYINT         NOT NULL DEFAULT 1 COMMENT 'Whether we placed the call.',

    `FROM_DIAL_CODE`                SMALLINT        NOT NULL DEFAULT 91 COMMENT 'Dial code of the caller.',
    `FROM`                          CHAR(15)        NULL COMMENT 'Phone number of the caller.',
    `TO_DIAL_CODE`                  SMALLINT        NOT NULL DEFAULT 91 COMMENT 'Dial code of the receiver.',
    `TO`                            CHAR(15)        NULL COMMENT 'Phone number of the receiver.',
    -- Whichever of the two above is the customer, resolved at write time. Direction decides which,
    -- and doing it once here keeps every read from having to work it out again.
    `CUSTOMER_DIAL_CODE`            SMALLINT        NOT NULL DEFAULT 91 COMMENT 'Dial code of the customer.',
    `CUSTOMER_PHONE_NUMBER`         CHAR(15)        NULL COMMENT 'The customer end of the call, resolved from direction at write time.',
    `CALLER_ID`                     CHAR(50)        NULL COMMENT 'Business number shown to the customer.',

    -- Two status columns on purpose. CALL_STATUS is the normalised one this service reasons about;
    -- EXOTEL_CALL_STATUS is the provider's own, kept because the deal profile displays it verbatim
    -- and collapsing the two would lose detail the agent actually reads.
    `CALL_STATUS`                   ENUM ('UNKNOWN','QUEUED','ORIGINATE','FAILED','BUSY','NO_ANSWER','CALL_COMPLETE','INSUFFICIENT_BALANCE','CANCELED') NOT NULL DEFAULT 'UNKNOWN' COMMENT 'Normalised call status.',
    `EXOTEL_CALL_STATUS`            ENUM ('QUEUED','IN_PROGRESS','COMPLETED','FAILED','BUSY','NO_ANSWER','CANCELLED') NULL COMMENT 'Provider status, as reported.',
    `DIRECTION`                     VARCHAR(50)     NULL COMMENT 'Provider direction string, e.g. outbound-api or inbound.',
    `ANSWERED_BY`                   VARCHAR(255)    NULL COMMENT 'Who or what answered.',

    `START_TIME`                    DATETIME        NULL COMMENT 'When the call started.',
    `END_TIME`                      DATETIME        NULL COMMENT 'When the call ended.',
    `DURATION`                      BIGINT          NULL COMMENT 'Total call duration in seconds.',
    `CONVERSATION_DURATION`         BIGINT          NULL COMMENT 'Talk time in seconds, excluding ringing.',
    `PRICE`                         DECIMAL(12, 2)  NULL COMMENT 'What the provider charged.',
    `RECORDING_URL`                 VARCHAR(2083)   NULL COMMENT 'Recording location, when one exists.',

    -- Same literal set as EXOTEL_CALL_STATUS, and nullable. The message service's leg columns use a
    -- different set ('NULL','CANCELED') from the enum they are mapped to ('CANCELLED', no 'NULL'),
    -- so two of their six values cannot be read back into Java at all. Not worth importing.
    `LEG1_STATUS`                   ENUM ('QUEUED','IN_PROGRESS','COMPLETED','FAILED','BUSY','NO_ANSWER','CANCELLED') NULL COMMENT 'Status of the first leg.',
    `LEG2_STATUS`                   ENUM ('QUEUED','IN_PROGRESS','COMPLETED','FAILED','BUSY','NO_ANSWER','CANCELLED') NULL COMMENT 'Status of the second leg.',

    -- No per-leg column, matching the message service, which also carries `legs` in memory only.
    -- The detail is inside EXOTEL_CALL_RESPONSE anyway, and a JSON array needs a converter that the
    -- shared JSONtoClassConverter does not cleanly provide.

    -- Raw provider payloads, stored verbatim and never interpreted here. The deal profile binds
    -- into these directly today, so they are part of the read contract and not merely an audit
    -- trail; dropping them would mean rewriting page bindings in the same change as the move.
    `EXOTEL_CALL_REQUEST`           JSON            NULL COMMENT 'Outbound call request sent to the provider.',
    `EXOTEL_CONNECT_APPLET_REQUEST` JSON            NULL COMMENT 'Inbound connect applet request from the provider.',
    `EXOTEL_CALL_RESPONSE`          JSON            NULL COMMENT 'Raw provider response.',

    `NOTE`                          TEXT            NULL COMMENT 'Agent note on the call, for manually logged calls.',

    -- Present for BaseUpdatableDAO, which binds NAME and TEMP_ACTIVE at construction and would
    -- otherwise hold nulls and NPE on the name lookup. A call has no meaningful name.
    `NAME`                          VARCHAR(512)    NULL COMMENT 'Unused. Present only because the shared DAO base expects the column.',
    `TEMP_ACTIVE`                   TINYINT         NOT NULL DEFAULT 0 COMMENT 'Unused. Present only because the shared DAO base expects the column.',

    `IS_ACTIVE`                     TINYINT         NOT NULL DEFAULT 1 COMMENT 'Flag to check if this call is active or not.',
    `CREATED_BY`                    BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`                    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`                    BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`                    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_CALLS_CODE` (`CODE`),
    -- Scoped by tenant rather than global, so one tenant's redelivery can never collide with
    -- another's row. Nullable, and MySQL permits repeated NULLs, so a manually logged call that
    -- never touched a provider can exist without one.
    UNIQUE KEY `UK2_CALLS_AC_CC_PROVIDER_CALL_ID` (`APP_CODE`, `CLIENT_CODE`, `PROVIDER_CALL_ID`),

    KEY `IDX0_CALLS_AC_CC` (`APP_CODE`, `CLIENT_CODE`),
    -- The call log read: calls on a set of visible deals, newest first.
    KEY `IDX1_CALLS_TICKET` (`APP_CODE`, `CLIENT_CODE`, `TICKET_ID`, `START_TIME`),
    -- Matching a provider event to a deal when the row does not exist yet, and the backfill.
    KEY `IDX2_CALLS_CUSTOMER` (`APP_CODE`, `CLIENT_CODE`, `CUSTOMER_PHONE_NUMBER`, `START_TIME`),

    -- SET NULL rather than CASCADE, matching the WhatsApp table: a deleted deal must not silently
    -- take the record of what was said to the customer with it.
    CONSTRAINT `FK1_CALLS_TICKET_ID` FOREIGN KEY (`TICKET_ID`)
        REFERENCES `entity_processor_tickets` (`ID`)
        ON DELETE SET NULL
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
