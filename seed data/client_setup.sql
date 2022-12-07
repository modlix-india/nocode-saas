use security;

-- security_client
INSERT INTO `security`.`security_client` (`CODE`, `NAME`, `TYPE_CODE`, `TOKEN_VALIDITY_MINUTES`, `LOCALE_CODE`) VALUES
('CLIA', 'Client A', 'BUS', '20', 'en-US'),
('CLIB', 'Client B', 'BUS', '25', 'en-US'),
('CLIC', 'Client C', 'BUS', '25', 'en-US'),
('CLIA1', 'Client A1', 'BUS', '15', 'en-US'),
('CLIA2', 'Client A2', 'BUS', '17', 'en-US');

SELECT ID FROM `security`.`security_client` where NAME = 'Client A' limit 1 into @v_clienta;
SELECT ID FROM `security`.`security_client` where NAME = 'Client B' limit 1 into @v_clientb;
SELECT ID FROM `security`.`security_client` where NAME = 'Client C' limit 1 into @v_clientc;
SELECT ID FROM `security`.`security_client` where NAME = 'Client A1' limit 1 into @v_clienta1;
SELECT ID FROM `security`.`security_client` where NAME = 'Client A2' limit 1 into @v_clienta2;

-- security user
INSERT INTO `security`.`security_user` (`CLIENT_ID`, `USER_NAME`, `EMAIL_ID`, `PHONE_NUMBER`, `FIRST_NAME`, `LAST_NAME`, `LOCALE_CODE`, `PASSWORD`, `PASSWORD_HASHED`, `ACCOUNT_NON_EXPIRED`, `ACCOUNT_NON_LOCKED`, `CREDENTIALS_NON_EXPIRED`, `NO_FAILED_ATTEMPT`, `STATUS_CODE`) VALUES 
(@v_clienta, 'userA001', 'NONE', 'NONE', 'User A', '001', 'en-US', 'fincity', '0', '1', '1', '1', '0', 'ACTIVE'),
(@v_clienta, 'userA002', 'NONE', 'NONE', 'User A', '002', 'en-US', 'fincity', '0', '1', '1', '1', '0', 'ACTIVE'),
(@v_clientb, 'userB001', 'NONE', 'NONE', 'User B', '001', 'en-US', 'fincity', '0', '1', '1', '1', '0', 'ACTIVE'),
(@v_clientb, 'userB002', 'NONE', 'NONE', 'User B', '002', 'en-US', 'fincity', '0', '1', '1', '1', '0', 'ACTIVE'),
(@v_clientc, 'userC001', 'NONE', 'NONE', 'User C', '001', 'en-US', 'fincity', '0', '1', '1', '1', '0', 'INACTIVE'),
(@v_clientc, 'userC002', 'NONE', 'NONE', 'User C', '002', 'en-US', 'fincity', '0', '1', '1', '1', '0', 'ACTIVE'),
(@v_clienta1, 'userA1001', 'NONE', 'NONE', 'User A1001', 'A1001', 'en-US', 'fincity', '0', '1', '1', '1', '0', 'INACTIVE'),
(@v_clienta1, 'userA1002', 'NONE', 'NONE', 'User A1002', 'A1002', 'en-US', 'fincity', '0', '1', '1', '1', '0', 'ACTIVE'),
(@v_clienta2, 'userA2001', 'NONE', 'NONE', 'User A2001', 'A2001', 'en-US', 'fincity', '0', '1', '1', '1', '0', 'ACTIVE'),
(@v_clienta2, 'userA2002', 'NONE', 'NONE', 'User A2002', 'A2002', 'en-US', 'fincity', '0', '1', '1', '1', '0', 'INACTIVE');

-- client manage
INSERT INTO security.security_client_manage (`CLIENT_ID`,`MANAGE_CLIENT_ID`) VALUES
(@v_clienta1,@v_clienta),
(@v_clienta2,@v_clienta);
