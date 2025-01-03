ALTER TABLE `security`.`security_otp`
    ADD COLUMN `EMAIL_ID` VARCHAR(320) NULL COMMENT 'Email ID to which otp was sent' AFTER `USER_ID`,
    ADD COLUMN `PHONE_NUMBER` CHAR(32) NULL COMMENT 'Phone Number to which otp was sent' AFTER `EMAIL_ID`;

ALTER TABLE `security`.`security_otp`
    ADD INDEX (`APP_ID`, `EMAIL_ID`, `PHONE_NUMBER`, `PURPOSE`);
