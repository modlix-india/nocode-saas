use security;

select id
from security.security_client
where CODE = 'SYSTEM'
into @v_system_client_id;

INSERT INTO `security`.`security_v2_role` (`CLIENT_ID`, `NAME`, `SHORT_NAME`, `DESCRIPTION`)
VALUES (@v_system_client_id, 'MobileApp CREATE', 'Create', 'Mobile Application create');

INSERT INTO `security`.`security_v2_role` (`CLIENT_ID`, `NAME`, `SHORT_NAME`, `DESCRIPTION`)
VALUES (@v_system_client_id, 'MobileApp UPLOADER', 'Uploader', 'Mobile Application file uploader');