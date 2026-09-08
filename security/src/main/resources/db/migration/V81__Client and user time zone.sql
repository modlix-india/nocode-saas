-- The clock a tenant operates on, and a per-person override of it.
--
-- Captured from the browser at registration (Intl.DateTimeFormat().resolvedOptions().timeZone), so
-- these hold IANA ids and not offsets. An offset cannot survive a daylight-saving boundary, and the
-- platform is meant to add countries by configuration rather than by code.
--
-- Deliberately NOT the same thing as security_client.BILLING_TIMEZONE, which V62 added for invoice
-- periods. A company can bill in one place and work in another, and quietly overloading a billing
-- column with operating hours is the kind of reuse that bites during an unrelated billing change.

ALTER TABLE `security`.`security_client`
    ADD COLUMN `TIME_ZONE` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Asia/Kolkata' COMMENT 'IANA time zone the tenant operates on';

-- Nullable on purpose, and null is meaningful: it means "no opinion, use the client's". A default
-- here instead would be indistinguishable from somebody who genuinely chose that zone, so a tenant
-- moving its own zone would silently fail to move anyone who had never touched the setting.
ALTER TABLE `security`.`security_user`
    ADD COLUMN `TIME_ZONE` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'IANA time zone overriding the client default; NULL means inherit';
