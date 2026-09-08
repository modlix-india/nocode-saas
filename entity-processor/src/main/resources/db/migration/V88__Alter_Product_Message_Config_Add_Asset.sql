-- Welcome-pack assets on the per-product message config.
--
-- No new table, on purpose. This config already keys on
-- (PRODUCT_ID, STAGE_ID, STATUS_ID, MESSAGE_CHANNEL_TYPE, ORDER) with a unique key that permits N
-- ordered rows per product and stage, which is exactly the shape of a welcome packet: "on the
-- initial stage, send these five things in this order". ProductMessageConfigService already provides
-- the group caching, ordering and duplicate validation that a separate asset table would have had to
-- reimplement.
--
-- What the config could not express is the asset itself. MESSAGE_TEMPLATE_ID names an approved
-- template, and a WhatsApp media template only declares its header FORMAT (IMAGE / VIDEO /
-- DOCUMENT) -- the media is supplied as a runtime parameter at send time. So the row could say
-- "send template X" but nothing said which brochure to put in X's header. These two columns close
-- exactly that gap and nothing more.

ALTER TABLE `entity_processor`.`entity_processor_product_message_configs`
    -- Named ASSET_FILE_DETAIL rather than FILE_DETAIL deliberately. The jOOQ forcedType that maps a
    -- JSON column onto oserver/files FileDetail matches `.*\.(.*_FILE_DETAIL(S)?)`, which requires a
    -- segment before the underscore. A bare FILE_DETAIL would silently generate as a raw String and
    -- the failure would only surface at runtime.
    ADD COLUMN `ASSET_FILE_DETAIL` JSON NULL
        COMMENT 'files-service FileDetail sent as the template header media. Null for a text-only config.'
        AFTER `MESSAGE_TEMPLATE_ID`,

    -- Body variable accompanying the asset. Sized to Meta's own body limit of 1024 characters so an
    -- over-long caption is rejected here rather than as a Graph error at send time.
    ADD COLUMN `CAPTION` VARCHAR(1024) NULL
        COMMENT 'Body variable sent alongside the asset.'
        AFTER `ASSET_FILE_DETAIL`;
