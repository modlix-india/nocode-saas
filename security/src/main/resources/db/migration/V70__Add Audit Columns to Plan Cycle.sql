use `security`;

-- Add audit columns to security_plan_cycle table to match security_plan table structure

ALTER TABLE `security`.`security_plan_cycle`
  ADD COLUMN `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row' AFTER `STATUS`,
  ADD COLUMN `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created' AFTER `CREATED_BY`,
  ADD COLUMN `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row' AFTER `CREATED_AT`,
  ADD COLUMN `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated' AFTER `UPDATED_BY`;

-- Add audit columns to security_plan_limit table to match security_plan table structure

ALTER TABLE `security`.`security_plan_limit`
  ADD COLUMN `CREATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who created this row' AFTER `STATUS`,
  ADD COLUMN `CREATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created' AFTER `CREATED_BY`,
  ADD COLUMN `UPDATED_BY` bigint unsigned DEFAULT NULL COMMENT 'ID of the user who updated this row' AFTER `CREATED_AT`,
  ADD COLUMN `UPDATED_AT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated' AFTER `UPDATED_BY`;
