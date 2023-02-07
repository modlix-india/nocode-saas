UPDATE security.security_permission SET APP_ID = null WHERE 
	APP_ID = (SELECT id from security.security_app WHERE app_code = 'appbuilder' LIMIT 1);
    
UPDATE security.security_role SET APP_ID = null WHERE 
	APP_ID = (SELECT id from security.security_app WHERE app_code = 'appbuilder' LIMIT 1);