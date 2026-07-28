ALTER TABLE entity_processor.entity_processor_tickets
    ADD COLUMN FORM_DATA JSON NULL COMMENT 'Lead form submission captured at intake: normalized standard + custom question answers, plus raw provider snapshot' AFTER AD_DATA;
