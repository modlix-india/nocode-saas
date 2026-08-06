-- The message service becomes the control plane for the WhatsApp bridge fleet.
--
-- The provider relationship this service owns is no longer Meta's Graph API but a fleet of
-- linked-device processes, one per shard, deployed per country. The role is unchanged; only what
-- sits behind it moved. That is why message_dispatch_outbox, EventDispatcher and OWNER_SERVICE
-- routing all survive the pivot untouched: the message-to-owner handoff problem did not change.
--
-- Two things land here: a registry of instances, and the columns that turn
-- message_whatsapp_phone_numbers into the session table it already almost was.

-- ---------------------------------------------------------------------------------------------
-- The fleet registry.
-- ---------------------------------------------------------------------------------------------
--
-- Deliberately NOT tenant-scoped, and so deliberately not shaped like every other table in this
-- schema. An instance is infrastructure: it holds sessions belonging to many tenants and belongs to
-- none of them. Giving it APP_CODE/CLIENT_CODE would force a lie into every row and invite a
-- tenant-scoped query to silently hide half the fleet during an incident.
--
-- Not in Eureka, either. Eureka does service discovery; what is needed here is session ownership,
-- which it has no concept of, and the instances are cross-region and not JVMs.
CREATE TABLE `message`.`message_bridge_instances`
(
    `ID`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',

    `INSTANCE_ID`       VARCHAR(64)     NOT NULL COMMENT 'The bridge''s own id, e.g. inst-in-01. Chosen by the instance and stable across restarts, because it also keys the device store volume it is bound to.',
    `BASE_URL`          VARCHAR(512)    NOT NULL COMMENT 'Where this service calls the instance. Reported by the instance rather than inferred from the request, because it sits behind the per-country ingress.',

    -- Comma-separated ISO-3166 alpha-2. A list rather than a single value so one instance can serve
    -- neighbouring markets later without a schema change, though today every instance serves one.
    `COUNTRIES`         VARCHAR(255)    NOT NULL COMMENT 'ISO country codes this instance may host numbers for, comma separated. Declared BY the instance, so bringing up a country is deploying a box rather than editing anything here.',

    -- Policy, not hardware. 25-50 is an order of magnitude below what the box could hold; the
    -- binding constraint is IP reputation and blast radius, not RAM.
    `SESSION_CAP`       INT UNSIGNED    NOT NULL DEFAULT 25 COMMENT 'Maximum live sessions. A risk limit, not a capacity limit.',

    `VERSION`           VARCHAR(128)             DEFAULT NULL COMMENT 'Build actually running, reported at registration. This is what makes a rollout observable rather than a matter of trust.',

    `STATE`             ENUM ('UP', 'DRAINING', 'DOWN') NOT NULL DEFAULT 'UP' COMMENT 'UP takes placements. DRAINING keeps its sessions but takes no new ones, ahead of a deploy or decommission. DOWN has missed its heartbeats.',

    -- The deployment channel. CI declares a desired image here and the instance's local agent rolls
    -- itself when what is running differs. Nothing reaches into Mumbai; the bridge only calls out.
    `DESIRED_IMAGE`     VARCHAR(512)             DEFAULT NULL COMMENT 'Image this instance should be running. Returned on every heartbeat and read by the host agent.',

    `ACTIVE_SESSIONS`   INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT 'Sessions occupying a slot, excluding terminal ones. This is what the cap is measured against.',
    `HELD_SESSIONS`     INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT 'Every session on the instance including terminal ones awaiting the reaper. The gap against ACTIVE_SESSIONS is dead weight.',

    `LAST_HEARTBEAT_AT` DATETIME                 DEFAULT NULL COMMENT 'Last successful heartbeat, UTC. Staleness here is a distinct alert from a failed Prometheus scrape: a wedged bridge still serves /metrics.',
    `MISSED_HEARTBEATS` INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT 'Consecutive misses. Three marks the instance DOWN.',

    `LAST_ERROR`        TEXT                     DEFAULT NULL COMMENT 'Last reconciliation or dispatch problem, so diagnosis does not start with log archaeology.',

    `IS_ACTIVE`         TINYINT         NOT NULL DEFAULT 1 COMMENT 'Cleared to retire an instance permanently, as opposed to DOWN which is transient.',
    `CREATED_BY`        BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`        BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_BRIDGE_INSTANCES_INSTANCE_ID` (`INSTANCE_ID`),
    KEY `IDX1_BRIDGE_INSTANCES_PLACEMENT` (`STATE`, `IS_ACTIVE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci` COMMENT = 'Registry of WhatsApp bridge instances. Fleet infrastructure, not tenant data.';

-- ---------------------------------------------------------------------------------------------
-- message_whatsapp_phone_numbers becomes the session table.
-- ---------------------------------------------------------------------------------------------
--
-- Kept rather than replaced because it already keys a number to app, client, product and owner
-- service, which is most of what a session row is. The rest is which instance holds it and what
-- state it is in.
--
-- The session id is this row's existing CODE. No new identifier: CODE is already unique, already
-- generated on insert, and already the thing a caller can safely be handed. The bridge is told the
-- session id at create time and reports it back on every event, so inbound resolution is a lookup
-- by CODE.
ALTER TABLE `message`.`message_whatsapp_phone_numbers`
    ADD COLUMN `BRIDGE_INSTANCE_ID` VARCHAR(64) NULL COMMENT 'Instance holding this session. Authoritative: routing is a table lookup, never a hash and never a balance. Null means unplaced, which for a Cloud API era row is its permanent state.' AFTER `OWNER_SERVICE`,

    ADD COLUMN `SESSION_STATE` ENUM ('PAIRING', 'CONNECTED', 'DISCONNECTED', 'LOGGED_OUT', 'BANNED', 'COUNTRY_MISMATCH') NULL COMMENT 'Lifecycle state as the bridge reports it, surfaced verbatim to the UI. COUNTRY_MISMATCH is separate from LOGGED_OUT because it is the only failure here a customer can fix themselves in seconds, and only if told what it is.' AFTER `BRIDGE_INSTANCE_ID`,

    ADD COLUMN `SESSION_REASON` VARCHAR(512) NULL COMMENT 'Why the session is in this state. A BANNED row with no reason is unexplainable three months later.' AFTER `SESSION_STATE`,

    ADD COLUMN `COUNTRY` CHAR(2) NULL COMMENT 'ISO country of the linked number, established authoritatively at PairSuccess from the linked JID rather than from what the caller declared. Placement and re-verification both read this.' AFTER `SESSION_REASON`,

    ADD COLUMN `LINKED_AT` DATETIME NULL COMMENT 'When the number was linked, UTC. Drives the warm-up ramp, which is derived from this and must not be an editable field.' AFTER `COUNTRY`,

    ADD COLUMN `STATE_SINCE` DATETIME NULL COMMENT 'When SESSION_STATE last changed, UTC. Distinct from UPDATED_AT so a dead session cannot reset its own retirement clock by being touched.' AFTER `LINKED_AT`;

-- Cloud API rows have a WABA; bridge sessions never will. Relaxed rather than dropped: the column
-- and the accounts table go when the Graph layer is retired, and doing both at once would mean one
-- migration that cannot be rolled back independently of a large code change.
ALTER TABLE `message`.`message_whatsapp_phone_numbers`
    MODIFY COLUMN `WHATSAPP_BUSINESS_ACCOUNT_ID` BIGINT UNSIGNED NULL COMMENT 'Meta business account. Null for every bridge session; retained until the Graph API layer is retired.';

-- One live link per number, enforced rather than trusted.
--
-- Two rows for the same number on two instances would mean two device links receiving the same
-- inbound messages and filing them into two tenants, which is a data leak rather than a duplicate.
-- MySQL has no partial index, so the generated column carries the number only for rows that are
-- actually bridge sessions: legacy Cloud API rows stay NULL and unique keys ignore NULLs, so this
-- constrains the new world without touching the old one.
--
-- Note what this also enforces, which is not incidental: retirement MUST clear BRIDGE_INSTANCE_ID.
-- A retired session that kept its instance would block the customer from ever re-linking, and
-- placing them fresh on a different instance is exactly the intended behaviour.
ALTER TABLE `message`.`message_whatsapp_phone_numbers`
    ADD COLUMN `LINKED_NUMBER_KEY` CHAR(20)
        GENERATED ALWAYS AS (IF(`BRIDGE_INSTANCE_ID` IS NULL, NULL, `DISPLAY_PHONE_NUMBER`)) STORED
        COMMENT 'DISPLAY_PHONE_NUMBER for placed sessions only, NULL otherwise. Exists purely to carry the unique key below.',
    ADD UNIQUE KEY `UK3_WHATSAPP_PHONE_NUMBERS_LINKED_NUMBER` (`LINKED_NUMBER_KEY`);

-- Routing reads this on every inbound event and placement reads it on every create, so both get an
-- index rather than a scan that is fine at ten sessions and not at a thousand.
ALTER TABLE `message`.`message_whatsapp_phone_numbers`
    ADD KEY `IDX4_WHATSAPP_PHONE_NUMBERS_SESSION` (`BRIDGE_INSTANCE_ID`, `SESSION_STATE`),
    ADD KEY `IDX5_WHATSAPP_PHONE_NUMBERS_COUNTRY` (`COUNTRY`, `SESSION_STATE`);
