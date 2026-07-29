-- =====================================================================================
-- Adzump permission + role seed.
--
-- The adzump service (com.modlix.saas.adzump) gates purely on PERMISSIONS, with two
-- @PreAuthorize macros used across ~41 service methods:
--     EDIT  = hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')
--     ADMIN = hasAuthority('Authorities.ROLE_Owner')
-- Reads carry no @PreAuthorize (tenant-scoped at runtime).
--
-- This migration seeds the single new authority the service needs -- the "Campaign MANAGE"
-- permission -- plus a V2 role wrapping it, so profiles can bundle the role in the security
-- UI. Authorities.ROLE_Owner (the ADMIN branch) already exists globally from V38.
-- Profiles (Adzump Admin / Campaign Manager / Viewer) and their role arrangements are created
-- in the security UI, not here.
--
-- CRITICAL: the permission MUST have APP_ID = NULL. AuthoritiesNameUtil prefixes an app-scoped
-- permission with the uppercased APP_CODE (yielding Authorities.ADZUMP.Campaign_MANAGE), which
-- would never match the un-prefixed string the service checks. This is the exact trap V54 hit
-- and V68 reverted for leadzump's Partner Manager role. The role is kept APP_ID = NULL for the
-- same reason (matches the V68 fix and the V57 Notification-role pattern).
-- =====================================================================================

USE `security`;

SELECT `ID` FROM `security`.`security_client` WHERE `CODE` = 'SYSTEM' LIMIT 1 INTO @`v_system_client_id`;

-- 1. Permission. NAME is UNIQUE on security_permission, so INSERT IGNORE keeps this idempotent.
INSERT IGNORE INTO `security`.`security_permission` (`CLIENT_ID`, `NAME`, `DESCRIPTION`)
VALUES (@`v_system_client_id`, 'Campaign MANAGE',
        'Adzump EDIT: build / edit / launch / approve recommendations / attribute campaigns. Yields Authorities.Campaign_MANAGE.');
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Campaign MANAGE' LIMIT 1 INTO @`v_perm_campaign_manage`;

-- 2. V2 role wrapping the permission. security_v2_role has no UNIQUE on NAME, so the insert is
--    guarded by NOT EXISTS to stay idempotent (re-runnable without duplicating the role).
INSERT INTO `security`.`security_v2_role` (`CLIENT_ID`, `NAME`, `SHORT_NAME`, `DESCRIPTION`)
SELECT @`v_system_client_id`, 'Adzump Campaign Manager', 'Campaign Manager',
       'Adzump: wraps the Campaign MANAGE permission (build / edit / launch / approve / attribute).'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `security`.`security_v2_role`
                    WHERE `NAME` = 'Adzump Campaign Manager' AND `CLIENT_ID` = @`v_system_client_id`);
SELECT `ID` FROM `security`.`security_v2_role`
 WHERE `NAME` = 'Adzump Campaign Manager' AND `CLIENT_ID` = @`v_system_client_id`
 ORDER BY `ID` DESC LIMIT 1 INTO @`v_role_campaign_manager`;

-- 3. Link permission -> role (UNIQUE (ROLE_ID, PERMISSION_ID), so INSERT IGNORE is idempotent).
INSERT IGNORE INTO `security`.`security_v2_role_permission` (`ROLE_ID`, `PERMISSION_ID`)
VALUES (@`v_role_campaign_manager`, @`v_perm_campaign_manage`);
