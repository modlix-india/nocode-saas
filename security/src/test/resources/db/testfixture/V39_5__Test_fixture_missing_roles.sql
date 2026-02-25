-- Test fixture: ensure roles referenced by V40 exist in security_v2_role.
-- On a fresh database 'Super Admin' is never created by any production migration,
-- which causes V40's INSERT into security_profile_role to fail with
-- "Column 'ROLE_ID' cannot be null".

SELECT ID FROM security.security_client WHERE CODE = 'SYSTEM' LIMIT 1 INTO @v_sys;

INSERT IGNORE INTO security.security_v2_role (CLIENT_ID, NAME, SHORT_NAME, DESCRIPTION)
VALUES (@v_sys, 'Super Admin', 'Super Admin', 'Super Admin role');
