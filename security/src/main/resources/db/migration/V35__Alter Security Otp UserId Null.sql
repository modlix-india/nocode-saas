ALTER TABLE `security`.`security_otp`
    MODIFY COLUMN `USER_ID` BIGINT UNSIGNED NULL COMMENT 'Identifier for the user for whom this OTP is generated. References security_user table';
