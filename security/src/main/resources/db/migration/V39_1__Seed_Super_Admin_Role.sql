-- Seeds the 'Super Admin' role that V40 depends on.
--
-- V40 ("AppBuilder Owner profile updated") runs:
--     select id from security.security_v2_role where name = 'Super Admin' limit 1 into @v_r_25;
-- and then inserts (@v_profile_appbuilder_owner, @v_r_25) into security.security_profile_role.
--
-- No migration before V40 ever creates a role with that name -- V40 is the only file in
-- db/migration/ that mentions 'Super Admin', and it only reads it. On a schema built from
-- scratch the lookup yields NULL and V40 aborts with:
--     SQL State 23000 / Error Code 1048 / Column 'ROLE_ID' cannot be null
-- which takes the whole `security` service down on a clean local database.
--
-- Versioned at 39.1 so it sorts AFTER V39 and BEFORE V40. This ADDS a migration rather
-- than editing V1..V39, so no existing migration checksum changes and databases that are
-- already past V39 are unaffected.
--
-- Guarded so it stays a no-op if the role is ever seeded by some other path. client_id 1
-- is the single client every other seeded role belongs to, and CLIENT_ID is NOT NULL.

INSERT INTO security.security_v2_role (client_id, name, short_name, description)
SELECT 1, 'Super Admin', 'SUPER_ADMIN', 'Super administrator role'
WHERE NOT EXISTS (
  SELECT 1 FROM security.security_v2_role WHERE name = 'Super Admin'
);