-- Widens the call endpoint columns so a SIP URI fits.
ALTER TABLE `entity_processor`.`entity_processor_calls`
    MODIFY COLUMN `FROM` VARCHAR(64) DEFAULT NULL COMMENT 'Caller. An E.164 number, or a sip: URI when the leg is an agent''s WebRTC endpoint.',
    MODIFY COLUMN `TO` VARCHAR(64) DEFAULT NULL COMMENT 'Callee. An E.164 number, or a sip: URI when the leg is an agent''s WebRTC endpoint.';
