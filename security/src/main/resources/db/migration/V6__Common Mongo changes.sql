use security;

select id from `security`.`security_role` where name = 'Application Manager' limit 1 into @v_role_app_manager;
select id from `security`.`security_permission` where name = 'Application CREATE' limit 1 into @v_permision_app_create;
INSERT INTO `security`.`security_role_permission` (role_id, permission_id) values (@v_role_app_manager, @v_permision_app_create);