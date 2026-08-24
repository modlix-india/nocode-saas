-- Which linked WhatsApp number a product sends from.
--
-- The mapping existed before this, on the other side of the wire: message_whatsapp_phone_numbers
-- carries a PRODUCT_ID, and the send path reads it. The problem was never storage, it was that the
-- column is written exactly once, when the number is linked, and there is no path to change it.
-- Repointing a product at a different number meant unlinking and re-scanning a QR code.
--
-- Moving it here inverts the ownership, and that is the point:
--
--   * The cardinality falls out of the schema. Many products can name the same number; a product
--     cannot name two. The other direction needed a uniqueness constraint on the product column to
--     say the same thing, and nothing was enforcing one.
--
--   * Authorization comes with it. Changing this is a product update, so it goes through the same
--     check as any other product edit rather than needing its own rule. Marking a number the
--     tenant-wide default stays on the number and stays owner-gated, because that decision is about
--     the account rather than about one product.
--
-- Holds the session CODE rather than its numeric id. That is already the idiom for naming a
-- message-service session from this schema: BRIDGE_SESSION_ID on entity_processor_whatsapp_messages
-- and on entity_processor_whatsapp_outbox are both the same CHAR(22). Numeric cross-service ids have
-- gone wrong here once already - MESSAGE_TEMPLATE_ID on entity_processor_product_message_configs
-- pointed into the message service, and V90 had to repatriate it by hand.
--
-- No foreign key: the referenced row lives in another schema. Nothing guarantees the number still
-- exists, so the resolver treats a code it cannot place as if it were unset and falls back to the
-- default. A product must not stop sending because somebody unlinked a handset.
ALTER TABLE `entity_processor`.`entity_processor_products`
    ADD COLUMN `WHATSAPP_SESSION_CODE` CHAR(22) DEFAULT NULL
        COMMENT 'Matches message_whatsapp_phone_numbers.CODE in the message service. Null means this product uses the tenant default number.'
        AFTER `PRODUCT_WALK_IN_FORM_ID`;

-- The page that edits this asks the reverse question - "which products use this number" - once per
-- listed number, so it is a lookup by code within a tenant rather than by product.
CREATE INDEX `IDX1_PRODUCTS_AC_CC_WSC`
    ON `entity_processor`.`entity_processor_products` (`APP_CODE`, `CLIENT_CODE`, `WHATSAPP_SESSION_CODE`);
