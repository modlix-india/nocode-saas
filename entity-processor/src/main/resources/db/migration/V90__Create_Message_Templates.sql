-- A reusable message library, ours rather than Meta's.
--
-- The Cloud API's templates are gone with the protocol: there is no approval to request and no
-- Graph API to request it from. What survives is the part that was always actually useful, which is
-- having a named, reusable message with variables in it that a person can pick from a list.
--
-- Two things make this different from what it replaces, and both matter:
--
--   * No status, no rejection reason, no language pair, no component tree. A message is a body, an
--     optional attachment and a set of variables. Everything else on the old table existed only to
--     mirror Meta's approval workflow.
--
--   * It stores SEVERAL bodies, not one. Sending identical text to more than about fifteen
--     recipients an hour is a documented ban trigger, and a stage rule sends one body to every
--     matching lead by construction. Variants are the mitigation, so they are the primary content
--     rather than an optional extra.
--
-- Lives in entity-processor rather than the message service because the bodies interpolate deal and
-- product fields, which are this service's data. The message service no longer knows what a
-- conversation means and should not learn.
CREATE TABLE `entity_processor`.`entity_processor_message_templates`
(
    `ID`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `APP_CODE`          CHAR(64)        NOT NULL COMMENT 'App Code this message belongs to.',
    `CLIENT_CODE`       CHAR(8)         NOT NULL COMMENT 'Client Code this message belongs to.',
    `CODE`              CHAR(22)        NOT NULL COMMENT 'Unique Code to identify this row.',

    `NAME`              VARCHAR(255)    NOT NULL COMMENT 'What a person picks it by, e.g. "Brochure follow-up".',
    `DESCRIPTION`       VARCHAR(1024)            DEFAULT NULL COMMENT 'When to use it, for whoever picks it next.',

    `CHANNEL`           ENUM ('WHATSAPP', 'EMAIL', 'SMS') NOT NULL DEFAULT 'WHATSAPP' COMMENT 'Which channel this is written for. Only WHATSAPP is wired today.',

    -- A JSON array of strings, and the actual content of the row.
    --
    -- One element is allowed and is what a message starts as. The sender rotates across whatever is
    -- here, so a single-variant message used by a stage rule will send identical text to every lead
    -- and is worth warning about in the editor rather than refusing.
    `BODY_VARIANTS`     JSON            NOT NULL COMMENT 'Interchangeable phrasings, rotated per recipient. The point of the table.',

    -- Names only, e.g. ["ownerName","productName"]. The editor uses them to offer insertion and to
    -- warn about a variable used in a body but not declared; substitution resolves against the deal
    -- at send time.
    `VARIABLES`         JSON                     DEFAULT NULL COMMENT 'Variable names the bodies may reference.',

    `ASSET_FILE_DETAIL` JSON                     DEFAULT NULL COMMENT 'Optional attachment, picked from the file browser.',
    `CAPTION`           VARCHAR(1024)            DEFAULT NULL COMMENT 'Caption sent with the attachment.',

    `IS_ACTIVE`         TINYINT         NOT NULL DEFAULT 1 COMMENT 'Flag to check if this message is active or not.',
    `TEMP_ACTIVE`       TINYINT         NOT NULL DEFAULT 1 COMMENT 'Soft-delete companion, matching the other tables in this schema.',
    `USER_ID`           BIGINT UNSIGNED          DEFAULT NULL COMMENT 'Owning user, where the schema tracks one.',
    `CREATED_BY`        BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`        BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_MESSAGE_TEMPLATES_CODE` (`CODE`),
    -- Names are picked from a list per tenant, so they should be unique within one and are allowed
    -- to collide across tenants.
    UNIQUE KEY `UK2_MESSAGE_TEMPLATES_NAME` (`APP_CODE`, `CLIENT_CODE`, `NAME`),
    KEY `IDX1_MESSAGE_TEMPLATES_CHANNEL` (`APP_CODE`, `CLIENT_CODE`, `CHANNEL`, `IS_ACTIVE`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci` COMMENT = 'Reusable message bodies with variants. Replaces Meta-approved templates, which do not exist on the linked-device protocol.';

-- The stage rule now points at the library above rather than at a message-service template row.
--
-- Same column, different meaning, which is only safe because the Cloud API rules are being migrated
-- by hand and the old ids are meaningless on this path anyway. Renamed so nobody reads the old name
-- and assumes the old target.
ALTER TABLE `entity_processor`.`entity_processor_product_message_configs`
    CHANGE COLUMN `MESSAGE_TEMPLATE_ID` `MESSAGE_TEMPLATE_ID` BIGINT UNSIGNED NULL COMMENT 'Row in entity_processor_message_templates. Was a message-service Cloud API template before the pivot; existing values are dead and are migrated by hand.';
