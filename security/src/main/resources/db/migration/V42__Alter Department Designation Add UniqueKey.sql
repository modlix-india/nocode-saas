USE security;

truncate `security`.`security_department`;

truncate `security`.`security_designation`;

ALTER TABLE `security`.`security_department`
    ADD CONSTRAINT `UK1_SECURITY_DEPARTMENT_CLIENT_ID_NAME` UNIQUE (`CLIENT_ID`, `NAME`);

ALTER TABLE `security`.`security_designation`
    ADD CONSTRAINT `UK1_SECURITY_DESIGNATION_CLIENT_ID_NAME` UNIQUE (`CLIENT_ID`, `NAME`);
