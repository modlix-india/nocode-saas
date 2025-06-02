ALTER TABLE `entity_processor`.`entity_processor_simple_rules`
    ADD COLUMN `HAS_PARENT` TINYINT NOT NULL DEFAULT 1 COMMENT 'Flag to tell if this rule has a complex parent or not.' AFTER `NEGATE`;

UPDATE `entity_processor`.`entity_processor_simple_rules` sr
   SET sr.`HAS_PARENT` = 1
 WHERE sr.`ID` IN (
     SELECT DISTINCT r.`SIMPLE_CONDITION_ID`
       FROM `entity_processor`.`entity_processor_simple_complex_rule_relations` r
 );
