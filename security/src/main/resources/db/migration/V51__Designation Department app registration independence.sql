use security;

ALTER TABLE `security`.`security_app_reg_designation`
    DROP FOREIGN KEY `FK2_APP_REG_DESIGNATION_DEPARTMENT_ID`;
ALTER TABLE `security`.`security_app_reg_designation`
    CHANGE COLUMN `DEPARTMENT_ID` `DEPARTMENT_ID` BIGINT UNSIGNED NULL COMMENT 'Department ID for which this designation belongs to';
ALTER TABLE `security`.`security_app_reg_designation`
    ADD CONSTRAINT `FK2_APP_REG_DESIGNATION_DEPARTMENT_ID`
        FOREIGN KEY (`DEPARTMENT_ID`)
            REFERENCES `security`.`security_app_reg_department` (`ID`)
            ON DELETE CASCADE
            ON UPDATE CASCADE;