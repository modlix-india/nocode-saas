use `security`;

ALTER TABLE `security`.`security_client`
    ADD COLUMN `LEVEL_TYPE` ENUM ('SYSTEM', 'CLIENT', 'CUSTOMER', 'CONSUMER') NOT NULL DEFAULT 'CLIENT' AFTER `TYPE_CODE`;

SET SQL_SAFE_UPDATES = 0;
UPDATE security.security_client AS c
    LEFT JOIN security.security_client_hierarchy AS h ON h.CLIENT_ID = c.ID
SET c.LEVEL_TYPE = CASE
                       WHEN h.MANAGE_CLIENT_LEVEL_0 IS NULL THEN 'SYSTEM'
                       WHEN h.MANAGE_CLIENT_LEVEL_1 IS NULL THEN 'CLIENT'
                       WHEN h.MANAGE_CLIENT_LEVEL_2 IS NULL THEN 'CUSTOMER'
                       WHEN h.MANAGE_CLIENT_LEVEL_3 IS NULL THEN 'CONSUMER'
                       ELSE c.LEVEL_TYPE
    END
WHERE c.LEVEL_TYPE <> CASE
                          WHEN h.MANAGE_CLIENT_LEVEL_0 IS NULL THEN 'SYSTEM'
                          WHEN h.MANAGE_CLIENT_LEVEL_1 IS NULL THEN 'CLIENT'
                          WHEN h.MANAGE_CLIENT_LEVEL_2 IS NULL THEN 'CUSTOMER'
                          WHEN h.MANAGE_CLIENT_LEVEL_3 IS NULL THEN 'CONSUMER'
                          ELSE c.LEVEL_TYPE
    END;
SET SQL_SAFE_UPDATES = 1;