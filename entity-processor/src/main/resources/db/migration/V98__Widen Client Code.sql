-- Client codes widened from CHAR(8) to CHAR(12).
--
-- `ClientDAO.getValidClientCode` builds a code from at most the first five
-- characters of the client's name and appends a collision counter -- KAILA,
-- KAILA1, KAILA2 -- so a popular name stem eats the namespace one registration
-- at a time, and CHAR(8) left room for only three digits. Measured before the
-- change: 304 clients already sat at exactly eight characters, KAILA alone held
-- 294 of its 999, and the code after KAILA999 is nine characters, which strict
-- mode rejects outright with `ERROR 1406: Data too long`. Registration would
-- simply have stopped for those names.
--
-- CHAR rather than VARCHAR, and 12 rather than 64: a client code is a short
-- uppercase token, and CHAR(64) in utf8mb4 would reserve 256 bytes per row
-- across ~70 columns to hold five to eight characters. Twelve gives the counter
-- seven digits, and stays under the thirteen-character limit in
-- `SecuredFileResourceService.checkReadAccessWithClientCode`, so no deploy order
-- between this and that parser fix can break secured file access.
--
-- This schema holds COPIES of `security.security_client.CODE`, which is the
-- source of truth and is widened in its own migration. Until every schema has
-- this, no code longer than eight characters may be issued.
--
-- App codes are deliberately untouched: `AppDAO.generateAppCode` appends a
-- random base36 suffix and regenerates on collision instead of counting, and
-- every APP_CODE column is already 64 wide.

ALTER TABLE `entity_processor`.`entity_processor_activities`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who created this Activity.';
ALTER TABLE `entity_processor`.`entity_processor_ads`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'ClientCode on which this ad created.';
ALTER TABLE `entity_processor`.`entity_processor_adsets`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'ClientCode on which this adset created.';
ALTER TABLE `entity_processor`.`entity_processor_calls`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code related to this call.';
ALTER TABLE `entity_processor`.`entity_processor_campaign_metrics`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code.';
ALTER TABLE `entity_processor`.`entity_processor_campaign_products`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'ClientCode on which this link was created.';
ALTER TABLE `entity_processor`.`entity_processor_campaign_sync_state`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code.';
ALTER TABLE `entity_processor`.`entity_processor_campaigns`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'ClientCode on which this campaign created';
ALTER TABLE `entity_processor`.`entity_processor_conversion_action_mapping`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'ClientCode this mapping belongs to.';
ALTER TABLE `entity_processor`.`entity_processor_conversion_events`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'ClientCode this event belongs to.';
ALTER TABLE `entity_processor`.`entity_processor_diagnostics`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code.';
ALTER TABLE `entity_processor`.`entity_processor_integrations`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code.';
ALTER TABLE `entity_processor`.`entity_processor_message_templates`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code this message belongs to.';
ALTER TABLE `entity_processor`.`entity_processor_notes`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who created this note.';
ALTER TABLE `entity_processor`.`entity_processor_owners`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code to whom this notification we sent.';
ALTER TABLE `entity_processor`.`entity_processor_partners`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who added this partner.';
ALTER TABLE `entity_processor`.`entity_processor_product_comms`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who added this Product Comm.';
ALTER TABLE `entity_processor`.`entity_processor_product_message_configs`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who created this config.';
ALTER TABLE `entity_processor`.`entity_processor_product_template_walk_in_forms`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who created this Product template walk in form.';
ALTER TABLE `entity_processor`.`entity_processor_product_templates`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who created this Product Template.';
ALTER TABLE `entity_processor`.`entity_processor_product_ticket_c_rules`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who created this Rule Config.';
ALTER TABLE `entity_processor`.`entity_processor_product_ticket_ex_rules`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who created this Rule.';
ALTER TABLE `entity_processor`.`entity_processor_product_ticket_ru_rules`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who created this Rule Config.';
ALTER TABLE `entity_processor`.`entity_processor_product_walk_in_forms`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who created this Product walk in form.';
ALTER TABLE `entity_processor`.`entity_processor_products`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code to whom this notification we sent.';
ALTER TABLE `entity_processor`.`entity_processor_sources`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code.';
ALTER TABLE `entity_processor`.`entity_processor_stages`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who created this Stage.';
ALTER TABLE `entity_processor`.`entity_processor_tags`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code.';
ALTER TABLE `entity_processor`.`entity_processor_task_types`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who created this task type.';
ALTER TABLE `entity_processor`.`entity_processor_tasks`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who created this task.';
ALTER TABLE `entity_processor`.`entity_processor_ticket_c_user_distributions`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who created this Rule Config.';
ALTER TABLE `entity_processor`.`entity_processor_ticket_duplication_rules`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who created this Rule Config.';
ALTER TABLE `entity_processor`.`entity_processor_ticket_pe_duplication_rules`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who created this Rule.';
ALTER TABLE `entity_processor`.`entity_processor_ticket_ru_user_distributions`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code who created this Rule Config.';
ALTER TABLE `entity_processor`.`entity_processor_tickets`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code to whom this notification we sent.';
ALTER TABLE `entity_processor`.`entity_processor_whatsapp_messages`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code related to this WhatsApp message.';
ALTER TABLE `entity_processor`.`entity_processor_whatsapp_outbox`
    MODIFY COLUMN `CLIENT_CODE` CHAR(12) NOT NULL COMMENT 'Client Code this queued message belongs to.';
