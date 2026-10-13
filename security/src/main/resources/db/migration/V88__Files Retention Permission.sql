-- =====================================================================================
-- Files retention: the authority that gates creating / updating / deleting file retention
-- policies in the files service (APP rows and CLIENT rows).
--
-- Seeds, in the same shape as the existing 'STATIC Files PATH' / 'SECURED Files PATH' pair:
--   * permission 'Files RETENTION' -> Authorities.Files_RETENTION   (what files checks)
--   * V2 role    'Files RETENTION' -> Authorities.ROLE_Files_RETENTION, wrapping the permission
-- and gives the role to the 'Appbuilder Owner' profile (the profile appbuilder assigns to
-- client owners at registration), both in its ARRANGEMENT and in security_profile_role.
--
-- Permission and role are APP_ID = NULL on purpose: AuthoritiesNameUtil prefixes an app-scoped
-- name with the uppercased APP_CODE (Authorities.APPBUILDER.Files_RETENTION), which would never
-- match the string the files service checks (see V78 / V68).
--
-- The role is NOT made a sub-role of 'Files Manager': that role sits in many profiles across
-- apps, and this change grants the authority to the appbuilder owner profile only. In the
-- arrangement it is nested under the Files Manager node for the security UI (next to Static /
-- Secured), which is grouping only; when that node is not where V40 put it, the entry goes at
-- the top level instead.
--
-- Re-runnable: every insert is guarded (UNIQUE + INSERT IGNORE, or NOT EXISTS), and the
-- arrangement entry uses a fixed key that is checked before it is added.
-- =====================================================================================

USE `security`;

SELECT `ID` FROM `security`.`security_client` WHERE `CODE` = 'SYSTEM' LIMIT 1 INTO @`v_system_client_id`;
SELECT `ID` FROM `security`.`security_app` WHERE `APP_CODE` = 'appbuilder' LIMIT 1 INTO @`v_app_appbuilder`;

-- 1. Permission. NAME is UNIQUE on security_permission, so INSERT IGNORE keeps this idempotent.
INSERT IGNORE INTO `security`.`security_permission` (`CLIENT_ID`, `NAME`, `DESCRIPTION`)
VALUES (@`v_system_client_id`, 'Files RETENTION',
        'Create, update and delete file retention policies. Yields Authorities.Files_RETENTION.');
SELECT `ID` FROM `security`.`security_permission` WHERE `NAME` = 'Files RETENTION' LIMIT 1
  INTO @`v_perm_files_retention`;

-- 2. V2 role wrapping the permission. security_v2_role has no UNIQUE on NAME, so the insert is
--    guarded by NOT EXISTS.
INSERT INTO `security`.`security_v2_role` (`CLIENT_ID`, `NAME`, `SHORT_NAME`, `DESCRIPTION`)
SELECT @`v_system_client_id`, 'Files RETENTION', 'Retention', 'File retention policy management'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `security`.`security_v2_role`
                    WHERE `NAME` = 'Files RETENTION' AND `CLIENT_ID` = @`v_system_client_id`
                      AND `APP_ID` IS NULL);
SELECT `ID` FROM `security`.`security_v2_role`
 WHERE `NAME` = 'Files RETENTION' AND `CLIENT_ID` = @`v_system_client_id` AND `APP_ID` IS NULL
 ORDER BY `ID` LIMIT 1 INTO @`v_role_files_retention`;

-- 3. Link permission -> role (UNIQUE (ROLE_ID, PERMISSION_ID)).
INSERT IGNORE INTO `security`.`security_v2_role_permission` (`ROLE_ID`, `PERMISSION_ID`)
VALUES (@`v_role_files_retention`, @`v_perm_files_retention`);

-- 4. The appbuilder owner profile (root profile, owned by SYSTEM).
SELECT `ID` FROM `security`.`security_profile`
 WHERE `NAME` = 'Appbuilder Owner' AND `APP_ID` = @`v_app_appbuilder`
   AND `CLIENT_ID` = @`v_system_client_id` AND `ROOT_PROFILE_ID` IS NULL
 ORDER BY `ID` LIMIT 1 INTO @`v_profile_appbuilder_owner`;

SELECT `ID` FROM `security`.`security_v2_role`
 WHERE `NAME` = 'Files Manager' AND `CLIENT_ID` = @`v_system_client_id`
 ORDER BY `ID` LIMIT 1 INTO @`v_role_files_manager`;

-- 4a. Arrangement, nested under the Files Manager node when it is where V40 put it.
UPDATE `security`.`security_profile`
   SET `ARRANGEMENT` = JSON_SET(`ARRANGEMENT`,
           '$.g1.subArrangements.r14.subArrangements.filesRetention',
           JSON_OBJECT('roleId', @`v_role_files_retention`, 'assignable', TRUE))
 WHERE `ID` = @`v_profile_appbuilder_owner`
   AND @`v_role_files_retention` IS NOT NULL
   AND JSON_CONTAINS_PATH(`ARRANGEMENT`, 'one', '$.g1.subArrangements.r14.subArrangements')
   AND CAST(JSON_UNQUOTE(JSON_EXTRACT(`ARRANGEMENT`, '$.g1.subArrangements.r14.roleId')) AS UNSIGNED)
       = @`v_role_files_manager`
   AND NOT JSON_CONTAINS_PATH(`ARRANGEMENT`, 'one',
           '$.g1.subArrangements.r14.subArrangements.filesRetention', '$.filesRetention');

-- 4b. Otherwise at the top level.
UPDATE `security`.`security_profile`
   SET `ARRANGEMENT` = JSON_SET(COALESCE(`ARRANGEMENT`, JSON_OBJECT()), '$.filesRetention',
           JSON_OBJECT('name', 'Files Retention', 'roleId', @`v_role_files_retention`, 'assignable', TRUE))
 WHERE `ID` = @`v_profile_appbuilder_owner`
   AND @`v_role_files_retention` IS NOT NULL
   AND NOT JSON_CONTAINS_PATH(COALESCE(`ARRANGEMENT`, JSON_OBJECT()), 'one',
           '$.g1.subArrangements.r14.subArrangements.filesRetention', '$.filesRetention');

-- 4c. Profile -> role (UNIQUE (PROFILE_ID, ROLE_ID)). This row is what grants the authority.
INSERT IGNORE INTO `security`.`security_profile_role` (`PROFILE_ID`, `ROLE_ID`, `EXCLUDE`)
SELECT @`v_profile_appbuilder_owner`, @`v_role_files_retention`, 0
  FROM DUAL
 WHERE @`v_profile_appbuilder_owner` IS NOT NULL AND @`v_role_files_retention` IS NOT NULL;
