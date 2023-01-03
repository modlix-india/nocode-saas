USE security;

INSERT IGNORE INTO security_permission (CLIENT_ID,NAME, DESCRIPTION) values (1 , 'Client Password Policy READ','client password policy read')
, (1 , 'Client Password Policy CREATE','client password policy create') , (1 , 'Client Password Policy UPDATE','client password policy update')
,(1 , 'Client Password Policy DELETE','client password policy delete');

