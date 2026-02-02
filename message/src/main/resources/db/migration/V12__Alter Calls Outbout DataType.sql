ALTER TABLE `message`.`message_calls`
    MODIFY COLUMN `IS_OUTBOUND` TINYINT NOT NULL DEFAULT 1 COMMENT 'Indicates whether the call is outbound.';
