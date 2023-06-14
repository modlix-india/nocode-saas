INSERT IGNORE INTO `security`.`security_package` (`CLIENT_ID`, `CODE`, `NAME`, `DESCRIPTION`, `BASE`) 
	VALUES ('1', 'SCHEM', 'Schema Management', 'Schema management roles and permissions will be part of this package', '0');

SELECT id from security.security_package where code = 'SCHEM' into @v_package_schema;
SELECT id from security.security_package where code = 'EVEAC' into @v_package_event_action;
SELECT id from security.security_package where code = 'EVEDE' into @v_package_event_def;
SELECT id from security.security_package where code = 'TRANSP' into @v_package_tranport;
SELECT id from security.security_package where code = 'STYLE' into @v_package_style;

SELECT id from security.security_role where name = 'EventAction Manager' into @v_role_event_action;
SELECT id from security.security_role where name = 'EventDefinition Manager' into @v_role_event_def;
SELECT id from security.security_role where name = 'Transport Manager' into @v_role_transport;
SELECT id from security.security_role where name = 'Schema Manager' into @v_role_schema;
SELECT id from security.security_role where name = 'Style Manager' into @v_role_style;

INSERT IGNORE INTO `security`.`security_package_role` (role_id, package_id) values
	(@v_role_event_action, @v_package_event_action),
    (@v_role_event_def, @v_package_event_def),
    (@v_role_transport, @v_package_schema),
    (@v_role_schema, @v_package_tranport),
    (@v_role_style, @v_package_style);
    
