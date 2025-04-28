
select id from security.security_v2_role where name = 'Data Manager' into @v_data_man;
select id from security.security_v2_role where name = 'Data Connection Manager' into @v_conn_man;

delete from security.security_v2_role_role where ROLE_ID in (@v_data_man, @v_conn_man);

select id from security.security_v2_role where name = 'Storage CREATE' into @v_sr_sc;
select id from security.security_v2_role where name = 'Storage READ' into @v_sr_sr;
select id from security.security_v2_role where name = 'Storage UPDATE' into @v_sr_su;
select id from security.security_v2_role where name = 'Storage DELETE' into @v_sr_sd;

insert into security.security_v2_role_role (role_id, sub_role_id) values
                                                                      (@v_data_man, @v_sr_sc),
                                                                      (@v_data_man, @v_sr_sr),
                                                                      (@v_data_man, @v_sr_su),
                                                                      (@v_data_man, @v_sr_sd);

select id from security.security_v2_role where name = 'Connection CREATE' into @v_sr_sc;
select id from security.security_v2_role where name = 'Connection READ' into @v_sr_sr;
select id from security.security_v2_role where name = 'Connection UPDATE' into @v_sr_su;
select id from security.security_v2_role where name = 'Connection DELETE' into @v_sr_sd;

insert into security.security_v2_role_role (role_id, sub_role_id) values
                                                                      (@v_conn_man, @v_sr_sc),
                                                                      (@v_conn_man, @v_sr_sr),
                                                                      (@v_conn_man, @v_sr_su),
                                                                      (@v_conn_man, @v_sr_sd);

select * from security.security_v2_role_role where ROLE_ID in (@v_data_man, @v_conn_man);

Select id from security.security_v2_role  WHERE (`NAME` = 'STATIC Files PATH') into @v_files_static_role;
Select id from security.security_v2_role  WHERE (`NAME` = 'SECURED Files PATH') into @v_files_secured_role;
UPDATE `security`.`security_v2_role` SET `SHORT_NAME` = 'Static' WHERE id = @v_files_static_role;
UPDATE `security`.`security_v2_role` SET `SHORT_NAME` = 'Secured' WHERE id = @v_files_secured_role;

