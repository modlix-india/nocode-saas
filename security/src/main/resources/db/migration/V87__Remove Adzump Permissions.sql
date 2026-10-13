-- =====================================================================================
-- Removes the Adzump permission + role seeded by V78. The adzump service that checked
-- Authorities.Campaign_MANAGE is gone, so nothing grants or checks it any more.
--
-- security_v2_role_permission, security_profile_role, security_v2_role_role,
-- security_v2_user_role and security_app_reg_user_role_v2 cascade on delete. The two V1
-- tables (security_role_permission, security_user_role_permission) RESTRICT, so they are
-- cleared first. Profiles built by modlix-apps adz_create_profiles.sql also name the role in
-- their ARRANGEMENT under the 'adzCampaignMgr' key, which no FK covers; that entry is dropped
-- so the arrangement does not point at a missing role.
-- =====================================================================================

USE `security`;

SELECT `ID` FROM `security`.`security_client` WHERE `CODE` = 'SYSTEM' LIMIT 1 INTO @`v_system_client_id`;
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Campaign MANAGE' LIMIT 1 INTO @`v_perm_campaign_manage`;

UPDATE `security`.`security_profile` `p`
  JOIN `security`.`security_v2_role` `r`
    ON `r`.`NAME` = 'Adzump Campaign Manager' AND `r`.`CLIENT_ID` = @`v_system_client_id`
   SET `p`.`ARRANGEMENT` = JSON_REMOVE(`p`.`ARRANGEMENT`, '$.adzCampaignMgr')
 WHERE JSON_CONTAINS_PATH(`p`.`ARRANGEMENT`, 'one', '$.adzCampaignMgr')
   AND JSON_UNQUOTE(JSON_EXTRACT(`p`.`ARRANGEMENT`, '$.adzCampaignMgr.roleId')) = CAST(`r`.`ID` AS CHAR);

DELETE FROM `security`.`security_v2_role`
 WHERE `NAME` = 'Adzump Campaign Manager' AND `CLIENT_ID` = @`v_system_client_id`;

DELETE FROM `security`.`security_role_permission` WHERE `PERMISSION_ID` = @`v_perm_campaign_manage`;
DELETE FROM `security`.`security_user_role_permission` WHERE `PERMISSION_ID` = @`v_perm_campaign_manage`;
DELETE FROM `security`.`security_permission` WHERE `ID` = @`v_perm_campaign_manage`;
