ALTER TABLE entity_processor.entity_processor_tickets
    ADD COLUMN AD_DATA JSON NULL COMMENT 'Ad attribution data (gclid, fbclid, wbraid, gbraid, _gcl_au, _fbp, _fbc, etc.) captured at lead intake' AFTER META_DATA;
