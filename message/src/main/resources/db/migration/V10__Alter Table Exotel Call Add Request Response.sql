ALTER TABLE `message`.`message_exotel_calls`
    ADD COLUMN `EXOTEL_CALL_REQUEST` JSON NULL COMMENT 'Entire Exotel Request object send to Exotel.' AFTER `LEG2_STATUS`,
    ADD COLUMN `EXOTEL_CALL_RESPONSE` JSON NULL COMMENT 'Entire Exotel Response object send by Exotel.' AFTER `EXOTEL_CALL_REQUEST`;
