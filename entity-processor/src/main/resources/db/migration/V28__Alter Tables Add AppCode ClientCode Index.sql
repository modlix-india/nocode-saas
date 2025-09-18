use entity_processor;

ALTER TABLE `entity_processor`.`entity_processor_product_templates`
    ADD INDEX `IDX0_PRODUCT_TEMPLATES_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `entity_processor`.`entity_processor_stages`
    ADD INDEX `IDX0_STAGES_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `entity_processor`.`entity_processor_owners`
    ADD INDEX `IDX0_OWNERS_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `entity_processor`.`entity_processor_products`
    ADD INDEX `IDX0_PRODUCTS_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `entity_processor`.`entity_processor_tickets`
    ADD INDEX `IDX0_TICKETS_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `entity_processor`.`entity_processor_product_template_rules`
    ADD INDEX `IDX0_PRODUCT_TEMPLATE_RULES_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `entity_processor`.`entity_processor_product_stage_rules`
    ADD INDEX `IDX0_PRODUCT_STAGE_RULES_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `entity_processor`.`entity_processor_simple_rules`
    ADD INDEX `IDX0_SIMPLE_RULES_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `entity_processor`.`entity_processor_complex_rules`
    ADD INDEX `IDX0_COMPLEX_RULES_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `entity_processor`.`entity_processor_simple_complex_rule_relations`
    ADD INDEX `IDX0_SIMPLE_COMPLEX_RULE_RELATIONS_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `entity_processor`.`entity_processor_task_types`
    ADD INDEX `IDX0_TASK_TYPES_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `entity_processor`.`entity_processor_tasks`
    ADD INDEX `IDX0_TASKS_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `entity_processor`.`entity_processor_notes`
    ADD INDEX `IDX0_NOTES_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `entity_processor`.`entity_processor_activities`
    ADD INDEX `IDX0_ACTIVITIES_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `entity_processor`.`entity_processor_campaigns`
    ADD INDEX `IDX0_CAMPAIGNS_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `entity_processor`.`entity_processor_partners`
    ADD INDEX `IDX0_PARTNERS_AC_CC` (`APP_CODE`, `CLIENT_CODE`);

ALTER TABLE `entity_processor`.`entity_processor_product_comms`
    ADD INDEX `IDX0_PRODUCT_COMMS_AC_CC` (`APP_CODE`, `CLIENT_CODE`);
