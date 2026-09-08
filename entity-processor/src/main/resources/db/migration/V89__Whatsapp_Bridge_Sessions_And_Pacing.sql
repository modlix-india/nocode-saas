-- WhatsApp moves from the Cloud API to linked-device sessions, and the pacing that used to be
-- Meta's problem becomes ours.
--
-- Three things land here:
--   1. messages point at a bridge session instead of a Meta phone-number row
--   2. stage rules carry a message body instead of an approved template
--   3. a Layer-2 outbox, which is where the 24-hour rule and the caps actually live
--
-- Nothing is dropped that holds data worth keeping. Existing conversations must still render after
-- cutover, so the old columns are relaxed rather than removed.

-- ---------------------------------------------------------------------------------------------
-- 1. Messages belong to a session, not to a Meta phone number.
-- ---------------------------------------------------------------------------------------------

ALTER TABLE `entity_processor`.`entity_processor_whatsapp_messages`
    ADD COLUMN `BRIDGE_SESSION_ID` CHAR(22) NULL COMMENT 'The linked-device session this message belongs to. Matches message_whatsapp_phone_numbers.CODE in the message service. Null on rows that predate the pivot.' AFTER `WHATSAPP_PHONE_NUMBER_ID`,
    ADD KEY `IDX6_WHATSAPP_MESSAGES_BRIDGE_SESSION` (`BRIDGE_SESSION_ID`, `CREATED_AT`);

-- Was NOT NULL and held a message-service row id that will not exist for bridge sessions. Relaxed
-- rather than dropped: pre-pivot rows carry a real id and their threads must keep rendering.
ALTER TABLE `entity_processor`.`entity_processor_whatsapp_messages`
    MODIFY COLUMN `WHATSAPP_PHONE_NUMBER_ID` BIGINT UNSIGNED NULL COMMENT 'Cloud API phone-number row. Null for every bridge-era message; retained so pre-pivot history still resolves.';

-- The record of why a message went out when it did.
--
-- Without this the pacing design is unauditable three months later, and worse, untunable: there is
-- no way to tell whether the caps are set right if nothing records which of them fired.
ALTER TABLE `entity_processor`.`entity_processor_whatsapp_messages`
    ADD COLUMN `SEND_DECISION` VARCHAR(64) NULL COMMENT 'How this send was allowed: INTERACTIVE, RELEASED_BY_REPLY, RELEASED_BY_TIMER or FORCED.' AFTER `MESSAGE_STATUS`,
    ADD COLUMN `FORCED_BY` BIGINT UNSIGNED NULL COMMENT 'User who overrode a hold. Set only on a forced send, and the only evidence of what happened if the number is later banned.' AFTER `SEND_DECISION`,
    ADD COLUMN `FORCE_STATE` JSON NULL COMMENT 'Session health at the moment of the override: caps used, warm-up day, reply rate. Captured because it is what tells you afterwards whether forcing was reasonable.' AFTER `FORCED_BY`;

-- ---------------------------------------------------------------------------------------------
-- 2. Stage rules carry a body, not a template.
-- ---------------------------------------------------------------------------------------------
--
-- There is no approval concept on this protocol, so MESSAGE_TEMPLATE_ID names a row in a table that
-- is being retired. Relaxed rather than dropped so an existing rule is still readable while it is
-- migrated by hand.
ALTER TABLE `entity_processor`.`entity_processor_product_message_configs`
    MODIFY COLUMN `MESSAGE_TEMPLATE_ID` BIGINT UNSIGNED NULL COMMENT 'Cloud API template. Dead on the bridge path; retained until existing rules are migrated.';

-- A set of bodies, not one.
--
-- Sending an identical string to more than about fifteen recipients an hour is a documented
-- trigger, and a stage rule does exactly that by construction: one body, every matching lead. So a
-- rule stores variants and the sender rotates them. JSON array of strings; a single-element array
-- is a rule that has not been given variants yet, which is allowed and logged rather than refused.
ALTER TABLE `entity_processor`.`entity_processor_product_message_configs`
    ADD COLUMN `BODY_VARIANTS` JSON NULL COMMENT 'Interchangeable message bodies, rotated per recipient so one rule does not send identical text to every lead.' AFTER `MESSAGE_TEMPLATE_ID`;

-- ---------------------------------------------------------------------------------------------
-- 3. Opt-out, on the deal, permanent.
-- ---------------------------------------------------------------------------------------------
--
-- On the ticket rather than in the outbox because it has to outlive any individual queued message.
-- A stage change that re-enrols an opted-out lead is precisely the complaint that becomes a report,
-- and a report is what precedes a ban.
ALTER TABLE `entity_processor`.`entity_processor_tickets`
    ADD COLUMN `WHATSAPP_OPTED_OUT` TINYINT NOT NULL DEFAULT 0 COMMENT 'Lead asked us to stop. Permanent, checked before every automated send, and unaffected by stage changes.',
    ADD COLUMN `WHATSAPP_OPTED_OUT_AT` DATETIME NULL COMMENT 'When the opt-out was seen, UTC.',
    ADD COLUMN `WHATSAPP_OPTED_OUT_TEXT` VARCHAR(512) NULL COMMENT 'The message that triggered it, kept so a false positive can be recognised and reversed by a person.';

-- ---------------------------------------------------------------------------------------------
-- 4. The Layer-2 outbox: where the 24-hour rule lives.
-- ---------------------------------------------------------------------------------------------
--
-- Layer 1 is the bridge's per-session queue, which spaces sends 5-15 seconds apart. It cannot hold
-- a message for a day, and a 15-minute sweeper cannot produce a 7-second gap, which is why these
-- are two mechanisms in two places rather than one.
--
-- Unlike message_dispatch_outbox, rows here are NOT deleted on success. This is a record of what
-- was sent to whom and why it was allowed, and it is the only evidence available if a number is
-- banned. It is also the only way to tell whether the caps are set correctly.
CREATE TABLE `entity_processor`.`entity_processor_whatsapp_outbox`
(
    `ID`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`          CHAR(64)        NOT NULL COMMENT 'App Code this queued message belongs to.',
    `CLIENT_CODE`       CHAR(8)         NOT NULL COMMENT 'Client Code this queued message belongs to.',
    `CODE`              CHAR(22)        NOT NULL COMMENT 'Unique Code to identify this row.',

    `TICKET_ID`         BIGINT UNSIGNED NOT NULL COMMENT 'The deal this is addressed to.',
    `PRODUCT_ID`        BIGINT UNSIGNED          DEFAULT NULL COMMENT 'Product whose stage rule queued this.',
    `STAGE_ID`          BIGINT UNSIGNED          DEFAULT NULL COMMENT 'Stage that triggered it.',
    `CONFIG_ID`         BIGINT UNSIGNED          DEFAULT NULL COMMENT 'The product_message_configs row this came from, so a packet can be traced back to its rule.',

    `BRIDGE_SESSION_ID` CHAR(22)                 DEFAULT NULL COMMENT 'Session it will be sent from. Resolved at queue time so the caps are computed against the number that will actually send.',
    `TO_PHONE`          CHAR(20)        NOT NULL COMMENT 'Recipient in E.164.',

    `BODY_TEXT`         TEXT            NOT NULL COMMENT 'The message, with the chosen variant already resolved and variables substituted.',
    `ASSET_FILE_DETAIL` JSON                     DEFAULT NULL COMMENT 'Optional attachment, carried over from the rule.',
    `CAPTION`           VARCHAR(1024)            DEFAULT NULL COMMENT 'Caption for the attachment.',

    -- PENDING is the resting state and covers "held": HOLD_REASON says which gate is holding it.
    -- Kept as one state rather than two so the sweeper has a single predicate to select on and a
    -- row cannot get stranded in HELD when the reason clears.
    `STATUS`            ENUM ('PENDING', 'SENT', 'FAILED', 'CANCELLED') NOT NULL DEFAULT 'PENDING' COMMENT 'CANCELLED covers a sequence stopped because the lead went quiet or opted out.',

    -- Never null while a row is being held. A PENDING row with no reason is unexplainable later,
    -- which is the failure mode this whole column exists to prevent.
    `HOLD_REASON`       VARCHAR(255)             DEFAULT NULL COMMENT 'Which gate refused to release it on the last sweep: WAITING_24H, NEW_CONTACT_CAP, WARM_UP_CAP, REPLY_RATE_LOW, QUIET_HOURS, LEAD_QUIET, OPTED_OUT, PREVIOUS_FAILED, SESSION_NOT_READY.',

    `SEQUENCE_ORDER`    INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT 'Position within a welcome packet. A packet drains in order and stops entirely if one of its messages fails.',

    `EARLIEST_SEND_AT`  DATETIME                 DEFAULT NULL COMMENT 'Not before this, UTC. Set when a gate reschedules rather than refuses, e.g. quiet hours moving a 3am message to the window opening.',
    `SENT_AT`           DATETIME                 DEFAULT NULL COMMENT 'When it actually went, UTC.',
    `MESSAGE_ID`        VARCHAR(255)             DEFAULT NULL COMMENT 'WhatsApp id of the sent message, linking this row to the conversation.',

    `ATTEMPTS`          INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT 'Send attempts. Bounded, so a permanently failing message surfaces instead of spinning.',
    `LAST_ERROR`        TEXT                     DEFAULT NULL COMMENT 'Last send failure.',

    `SEND_DECISION`     VARCHAR(64)              DEFAULT NULL COMMENT 'Why it was finally allowed: RELEASED_BY_REPLY, RELEASED_BY_TIMER or FORCED.',
    `FORCED_BY`         BIGINT UNSIGNED          DEFAULT NULL COMMENT 'User who overrode the hold, if anyone did.',

    `IS_ACTIVE`         TINYINT         NOT NULL DEFAULT 1 COMMENT 'Flag to check if this row is active or not.',
    `CREATED_BY`        BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`        BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_WHATSAPP_OUTBOX_CODE` (`CODE`),
    -- The sweeper's query: pending rows whose earliest-send has passed, oldest first.
    KEY `IDX1_WHATSAPP_OUTBOX_SWEEP` (`STATUS`, `EARLIEST_SEND_AT`),
    -- Per-deal reads: the sequence for one ticket, in order. Also what the "previous message in this
    -- sequence failed" check reads.
    KEY `IDX2_WHATSAPP_OUTBOX_TICKET` (`TICKET_ID`, `SEQUENCE_ORDER`),
    -- Per-session caps: how many first contacts this number has sent today.
    KEY `IDX3_WHATSAPP_OUTBOX_SESSION` (`BRIDGE_SESSION_ID`, `SENT_AT`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci` COMMENT = 'Queued automated WhatsApp messages and the pacing decision for each. Not cleared on success: this is the audit trail.';
