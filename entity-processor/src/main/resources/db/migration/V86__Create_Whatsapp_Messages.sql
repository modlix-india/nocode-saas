-- WhatsApp messages move here from the message service.
--
-- The reason is search. Unread counts and previews can be enriched per page across a service
-- boundary, but "find messages containing X, restricted to the deals I can see, ordered by
-- recency, paginated" needs the content filter and the access condition in one query. Split across
-- services that either over-fetches or paginates wrong.
--
-- The message service keeps everything provider-shaped: the webhook, sending through the Graph API,
-- templates, WABAs, phone numbers, media. It routes an inbound message to whichever service owns
-- the receiving number and never learns what a deal is.
--
-- Largely a mirror of message.message_whatsapp_messages, with four deliberate differences noted
-- inline: MESSAGE_ID is unique, the two message-service foreign keys are dropped, the business
-- number is denormalised, and BODY_TEXT exists.
CREATE TABLE `entity_processor`.`entity_processor_whatsapp_messages`
(
    `ID`                           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`                     CHAR(64)        NOT NULL COMMENT 'App Code related to this WhatsApp message.',
    `CLIENT_CODE`                  CHAR(8)         NOT NULL COMMENT 'Client Code related to this WhatsApp message.',
    `USER_ID`                      BIGINT UNSIGNED NULL COMMENT 'User associated with this message.',
    `CODE`                         CHAR(22)        NOT NULL COMMENT 'Unique Code to identify this row.',

    -- Meta's message id, and the idempotency key for the entire handoff. Unique here where it was
    -- only indexed in the message service, because the consumer upserts on it: that is what makes
    -- a webhook redelivery, an outbox replay after a failed delete, and a status update arriving
    -- before its own message all safe.
    `MESSAGE_ID`                   VARCHAR(255)    NULL COMMENT 'WhatsApp message ID from Meta. Idempotency key.',

    -- Plain columns, not foreign keys: both referents live in the message service's schema.
    `WHATSAPP_BUSINESS_ACCOUNT_ID` BIGINT UNSIGNED NULL COMMENT 'message.message_whatsapp_business_accounts ID. Not an FK, that table is in another service.',
    `WHATSAPP_PHONE_NUMBER_ID`     BIGINT UNSIGNED NOT NULL COMMENT 'message.message_whatsapp_phone_numbers ID. Not an FK, same reason.',
    -- Denormalised so this service can group a conversation by business number without calling
    -- back. Matters because a number can be re-pointed at a different product later, and history
    -- must keep the number it was actually exchanged on.
    `WHATSAPP_PHONE_NUMBER`        CHAR(20)        NULL COMMENT 'The business number as dialled, captured at write time so a later re-mapping cannot rewrite history.',

    `TICKET_ID`                    BIGINT UNSIGNED NULL COMMENT 'Deal this message was filed against. A label, not the access boundary: reads resolve the visible deal set first and match against this.',

    `FROM_DIAL_CODE`               SMALLINT        NOT NULL DEFAULT 91 COMMENT 'Dial code of the sender.',
    `FROM`                         CHAR(15)        NULL COMMENT 'Phone number of the sender.',
    `TO_DIAL_CODE`                 SMALLINT        NOT NULL DEFAULT 91 COMMENT 'Dial code of the recipient.',
    `TO`                           CHAR(15)        NULL COMMENT 'Phone number of the recipient.',
    `CUSTOMER_WA_ID`               CHAR(20)        NULL COMMENT 'Customer WhatsApp ID.',
    `CUSTOMER_DIAL_CODE`           SMALLINT        NOT NULL DEFAULT 91 COMMENT 'Dial code of the customer.',
    `CUSTOMER_PHONE_NUMBER`        CHAR(15)        NULL COMMENT 'Phone number of the customer.',

    `MESSAGE_TYPE`                 ENUM ('AUDIO','BUTTON','CONTACTS','DOCUMENT','LOCATION','TEXT','TEMPLATE','IMAGE','INTERACTIVE','ORDER','REACTION','STICKER','SYSTEM','UNKNOWN','VIDEO','UNSUPPORTED') NOT NULL DEFAULT 'TEXT' COMMENT 'Type of the message.',
    `MESSAGE_STATUS`               ENUM ('SENT','DELIVERED','READ','FAILED','DELETED') NOT NULL DEFAULT 'SENT' COMMENT 'Delivery status.',

    `SENT_TIME`                    DATETIME        NULL COMMENT 'When the message was sent.',
    `DELIVERED_TIME`               DATETIME        NULL COMMENT 'When the message was delivered.',
    `READ_TIME`                    DATETIME        NULL COMMENT 'When the message was read.',
    `FAILED_TIME`                  DATETIME        NULL COMMENT 'When the message failed.',
    `FAILURE_REASON`               TEXT            NULL COMMENT 'Reason for failure.',

    `IS_OUTBOUND`                  TINYINT         NOT NULL DEFAULT 1 COMMENT 'Whether we sent it.',

    -- The searchable projection of whatever text the message carries, extracted at write time.
    -- Search is the reason this table moved, and neither JSON_EXTRACT over MESSAGE nor a LIKE
    -- across a service boundary can be indexed. FULLTEXT ignores tokens shorter than
    -- innodb_ft_min_token_size (3 by default), so very short terms fall back to a LIKE.
    `BODY_TEXT`                    TEXT            NULL COMMENT 'Plain text of the message, extracted for search.',

    `MESSAGE`                      JSON            NULL COMMENT 'Outbound message object sent to WhatsApp.',
    `MEDIA_FILE_DETAIL`            JSON            NULL COMMENT 'File details when the message carries media.',
    `IN_MESSAGE`                   JSON            NULL COMMENT 'Raw inbound message object.',
    `MESSAGE_RESPONSE`             JSON            NULL COMMENT 'Raw response object from WhatsApp.',

    -- Present for BaseUpdatableDAO, which binds NAME and TEMP_ACTIVE at construction and would
    -- otherwise hold nulls and NPE on the name lookup. A message has no meaningful name, so NAME
    -- stays unset; BODY_TEXT is the searchable content and the preview.
    `NAME`                         VARCHAR(512)    NULL COMMENT 'Unused. Present only because the shared DAO base expects the column.',
    `TEMP_ACTIVE`                  TINYINT         NOT NULL DEFAULT 0 COMMENT 'Unused. Present only because the shared DAO base expects the column.',

    `IS_ACTIVE`                    TINYINT         NOT NULL DEFAULT 1 COMMENT 'Flag to check if this message is active or not.',
    `CREATED_BY`                   BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`                   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`                   BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`                   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_WA_MESSAGES_CODE` (`CODE`),
    -- Scoped by tenant rather than global. Meta ids are unique in practice, but a duplicate must
    -- never let one tenant's redelivery collide with another's row. Nullable, and MySQL permits
    -- repeated NULLs, so an outbound row can exist before Meta has answered with an id.
    UNIQUE KEY `UK2_WA_MESSAGES_AC_CC_MESSAGE_ID` (`APP_CODE`, `CLIENT_CODE`, `MESSAGE_ID`),

    KEY `IDX0_WA_MESSAGES_AC_CC` (`APP_CODE`, `CLIENT_CODE`),
    -- The thread read: messages for a set of visible deals, newest first.
    KEY `IDX1_WA_MESSAGES_TICKET` (`APP_CODE`, `CLIENT_CODE`, `TICKET_ID`, `SENT_TIME`),
    -- Conversation grouping by business number and customer, which survives a number re-mapping.
    KEY `IDX2_WA_MESSAGES_CONVERSATION` (`APP_CODE`, `CLIENT_CODE`, `WHATSAPP_PHONE_NUMBER_ID`, `CUSTOMER_WA_ID`, `SENT_TIME`),
    -- Unread counts: inbound messages on a deal with no READ_TIME.
    KEY `IDX3_WA_MESSAGES_UNREAD` (`APP_CODE`, `CLIENT_CODE`, `TICKET_ID`, `IS_OUTBOUND`, `READ_TIME`),
    FULLTEXT KEY `FT1_WA_MESSAGES_BODY` (`BODY_TEXT`),

    -- SET NULL rather than CASCADE: a deleted deal must not silently take the record of what was
    -- said to the customer with it.
    CONSTRAINT `FK1_WA_MESSAGES_TICKET_ID` FOREIGN KEY (`TICKET_ID`)
        REFERENCES `entity_processor_tickets` (`ID`)
        ON DELETE SET NULL
        ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci`;
