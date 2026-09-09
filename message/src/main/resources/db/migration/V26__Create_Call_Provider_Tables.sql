-- Browser calling (WebRTC) support for the CALL(EXOTEL) provider, and for whatever provider comes
-- after it.
--
-- Two tables land here, and they answer two different questions:
--
--   message_call_provider_apps        which integration app does this tenant own
--   message_provider_user_endpoints   where can this agent be reached, and in what order
--
-- Both carry a PROVIDER column rather than an enum, so a second provider is a row value and not a
-- migration. Neither table is given a BaseUpdatableController: their PROVIDER_APP_SECRET and
-- PROVIDER_METADATA columns hold credentials, and the generic eager read paths return
-- rec.intoMap() straight off the JOOQ record, which no Jackson annotation can filter.

-- ---------------------------------------------------------------------------------------------
-- The tenant's integration app with the provider.
-- ---------------------------------------------------------------------------------------------
--
-- Tenant-scoped, unlike message_bridge_instances: an Exotel app belongs to exactly one customer's
-- Exotel account, is created with that customer's own credentials, and must never be visible
-- across tenants.
CREATE TABLE `message`.`message_call_provider_apps`
(
    `ID`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `CODE`                CHAR(22)        NOT NULL COMMENT 'Short unique code, from BaseUpdatableDto. Required by the base DAO.',

    `APP_CODE`            CHAR(64)        NOT NULL COMMENT 'Modlix app this registration belongs to.',

    -- From MessageAccess, never from the Connection document: Connection is an AbstractOverridableDTO,
    -- so its own clientCode is the client owning the *definition*, which for an overridden connection
    -- is the parent and is often SYSTEM.
    `CLIENT_CODE`         CHAR(8)         NOT NULL COMMENT 'Tenant that owns this registration. Taken from MessageAccess, not from the Connection, which is overridable and may report SYSTEM.',

    -- Records which connection's credentials created the app, and nothing more. It is deliberately
    -- NOT part of the unique key below: one app serves every agent of a tenant, and the app's name
    -- has never had a connection component either. Keying on it would make a tenant's second CALL
    -- connection look like a tenant with no app, and the provider accepts a repeated app name
    -- without complaint — so the second run would create a duplicate whose secret nobody holds.
    `CONNECTION_NAME`     VARCHAR(256)    NOT NULL COMMENT 'Name of the CALL/EXOTEL Connection whose credentials created this app. Informational: the app is identified by tenant and provider, because one app serves every agent of the tenant.',
    `PROVIDER`            VARCHAR(32)     NOT NULL DEFAULT 'EXOTEL' COMMENT 'Matches ConnectionSubType.getProvider(). A column rather than an enum so a second provider needs no migration.',

    `PROVIDER_APP_ID`     VARCHAR(256)    NOT NULL COMMENT 'Provider-side app id. Exotel AppID.',
    `PROVIDER_APP_SECRET` VARCHAR(512)    NOT NULL COMMENT 'Provider-side app secret. Plaintext, consistent with connection credentials in Mongo; there is no encryption utility in this codebase to use instead. Never expose this column through any read path.',
    `PROVIDER_APP_NAME`   VARCHAR(256)             DEFAULT NULL COMMENT 'App name sent to the provider, so an operator can recognise it in their dashboard. appCode-clientCode-call, because appCode alone collides across tenants sharing one provider account.',
    `ACCOUNT_SID`         VARCHAR(256)    NOT NULL COMMENT 'Provider account this app is bound to. Also checked against inbound callbacks before a row is created for an unknown call id.',

    -- Outbound WebRTC bypasses the App Bazaar call flow entirely: the browser dials through icore,
    -- which has no per-call hook and never runs the flow's Passthru applet. This URL, registered on
    -- the app itself, is the only channel by which those calls report status and recordings back.
    `CALLBACK_URL`        VARCHAR(512)             DEFAULT NULL COMMENT 'Status callback URL registered on the provider app at setup. The only way browser-placed calls report back, since they never run the call flow. Stored so a host change is detectable without a provider round trip.',

    `PROVIDER_METADATA`   JSON                     DEFAULT NULL COMMENT 'Raw provider response, kept for diagnosis. Needs a forcedType entry in pom.xml or it generates as org.jooq.JSON rather than Map.',

    `IS_ACTIVE`           TINYINT         NOT NULL DEFAULT 1 COMMENT 'Cleared to retire a registration without losing the audit trail.',
    `CREATED_BY`          BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`          BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_CALL_PROVIDER_APPS_CODE` (`CODE`),
    -- One app per tenant per provider. This assumes one provider account per tenant, which holds
    -- here: every CALL connection for a client carries the same accountSid. A tenant genuinely
    -- needing two provider accounts should get a second client code rather than a second app
    -- under the same one.
    UNIQUE KEY `UK2_CALL_PROVIDER_APPS_TENANT` (`APP_CODE`, `CLIENT_CODE`, `PROVIDER`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci` COMMENT = 'Provider-side integration app registrations for browser calling, one per tenant.';


-- ---------------------------------------------------------------------------------------------
-- Where an agent can be reached, in ringing order.
-- ---------------------------------------------------------------------------------------------
--
-- Read on every inbound call, between the customer dialling and the phone ringing, so the routing
-- index below is shaped for exactly that query and nothing else.
--
-- On the tenant column, which is the subtle part. CLIENT_CODE is stored but is in no key: the
-- unique key below deliberately omits it, and the routing lookup filters on APP_CODE, USER_ID,
-- CONNECTION_NAME, PROVIDER and IS_ACTIVE -- not on CLIENT_CODE. That is safe because security user
-- ids come from one global sequence and each belongs to exactly one client, so USER_ID already
-- narrows the result to a single tenant's agent.
--
-- The alternative considered was resolving the agent's clientCode on every inbound call via
-- getUserInternal -> getClientById. That is two Feign hops in the path before the phone can ring,
-- to derive a value USER_ID already implies.
--
-- The cross-tenant risk once worth worrying about here was a different thing entirely: the connect
-- applet route used to be permitAll and took userId from the request body, so an unauthenticated
-- caller could iterate user ids. That route now lives under /internal/ and is fronted by nginx, so
-- the concern is closed rather than outstanding.
CREATE TABLE `message`.`message_provider_user_endpoints`
(
    `ID`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key.',
    `CODE`              CHAR(22)        NOT NULL COMMENT 'Short unique code, from BaseUpdatableDto.',

    `APP_CODE`          CHAR(64)        NOT NULL COMMENT 'Modlix app this endpoint belongs to.',
    -- The TENANT's client code, from MessageAccess, exactly as message_call_provider_apps is keyed.
    -- Not the agent's own client: every operation that touches these rows afterwards — deactivating
    -- an agent, tearing down the app, the admin listing — scopes by the caller's tenant, and an owner
    -- may manage agents in client hierarchies below their own. Keyed by the agent's client, those
    -- rows become invisible to the operations meant to manage them: deactivation revokes the SIP
    -- mapping at the provider and matches nothing here, leaving the endpoint active so tokens keep
    -- being minted for an agent who can no longer dial.
    `CLIENT_CODE`       CHAR(8)         NOT NULL COMMENT 'Tenant that owns this endpoint. From MessageAccess, matching message_call_provider_apps — not the agent''s own client, which every management operation would then fail to match.',
    `USER_ID`           BIGINT UNSIGNED NOT NULL COMMENT 'Security user id of the agent this endpoint reaches.',

    `CONNECTION_NAME`   VARCHAR(256)    NOT NULL COMMENT 'Connection this endpoint was provisioned against. In the unique key, so one agent can hold endpoints on several connections, which is how one agent serves two products on two virtual numbers.',
    `PROVIDER`          VARCHAR(32)     NOT NULL DEFAULT 'EXOTEL' COMMENT 'Matches ConnectionSubType.getProvider().',

    `ENDPOINT_TYPE`     VARCHAR(32)     NOT NULL COMMENT 'WEBRTC_SIP or PSTN_PHONE. A string rather than an ENUM so a third destination kind needs no migration.',
    `ENDPOINT_VALUE`    VARCHAR(512)    NOT NULL COMMENT 'What goes into the connect applet''s destination list: a sip: URI, or an E.164 number. Would also be the lookup key for attributing a call this service never placed, if that reconciliation is ever built - see the index note below, which is why no index on it exists yet.',

    -- Ringing is sequential (parallel_ringing.activate = false), so this column decides the order in
    -- which Exotel tries the destinations. It is load-bearing, not decoration.
    `PRIORITY`          INT             NOT NULL DEFAULT 1 COMMENT 'Lower rings first. WEBRTC_SIP 1, PSTN_PHONE 2. Load-bearing: ringing is sequential, so this is the order Exotel actually dials.',

    `VIRTUAL_NUMBER`    VARCHAR(32)              DEFAULT NULL COMMENT 'Virtual number this mapping was created against, supplied by the caller because ProductComm lives in entity-processor and holds one per product. Controls outbound caller ID and the provider-side PSTN fallback.',
    `PROVIDER_USER_ID`  VARCHAR(256)             DEFAULT NULL COMMENT 'Provider-side user id. For Exotel this is the agent''s email, and it is what the browser passes when initialising the softphone.',
    `PROVIDER_METADATA` JSON                     DEFAULT NULL COMMENT 'Rest of the provider response, including the SIP secret. Needs a forcedType entry. Note the provider ships that secret encrypted under a key hardcoded in its public client SDK, so treat this column as plaintext and keep it off every read path.',

    `IS_ACTIVE`         TINYINT         NOT NULL DEFAULT 1 COMMENT 'Cleared when an agent is deprovisioned. A soft delete, so a departed agent stays auditable. Revocation at the provider is a separate call and is the one that actually stops calls.',
    `CREATED_BY`        BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who created this row.',
    `CREATED_AT`        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time when this row is created.',
    `UPDATED_BY`        BIGINT UNSIGNED          DEFAULT NULL COMMENT 'ID of the user who updated this row.',
    `UPDATED_AT`        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Time when this row is updated.',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_PROVIDER_USER_ENDPOINTS_CODE` (`CODE`),
    -- Deliberately without CLIENT_CODE, so this key matches the query that reads the table.
    --
    -- `findActiveEndpoints` -- the lookup the inbound connect applet runs on every call -- filters
    -- on APP_CODE, USER_ID, CONNECTION_NAME and PROVIDER, and not on CLIENT_CODE. That is sound:
    -- security user ids come from a single global sequence and each belongs to exactly one client,
    -- so USER_ID already narrows to one agent in one tenant, and resolving that agent's client code
    -- would mean a Feign round trip in the path before the phone can ring.
    --
    -- Including CLIENT_CODE here would let the two disagree, and the gap is reachable. Endpoint rows
    -- are written under the *caller's* client code rather than the agent's, and `requireManagedUser`
    -- lets an owner in a parent client provision a user belonging to a child client. So one agent
    -- provisioned by an owner in each would satisfy a key that included CLIENT_CODE, leaving two row
    -- sets for the same USER_ID -- and the applet, ringing sequentially, would dial both tenants'
    -- destinations for a single call.
    --
    -- One endpoint per agent, per connection, per type. A second provision by a different owner
    -- updates the row in place and rewrites CLIENT_CODE to whoever provisioned last, which is the
    -- accurate record. The column stays, because every management operation scopes by it --
    -- deactivation, teardown and the admin listing -- and an owner may only manage clients at or
    -- below their own.
    --
    -- The cost is that routing isolation now rests entirely on that one-id-one-client property. It
    -- holds today; a shared or cross-client user id would silently cross tenants here.
    UNIQUE KEY `UK2_PROVIDER_USER_ENDPOINTS_AGENT` (`APP_CODE`, `USER_ID`, `CONNECTION_NAME`, `ENDPOINT_TYPE`),

    -- Ordered to serve the connect applet exactly: by app and agent, active only, in ringing order.
    --
    -- The only index here, deliberately. A reverse index on ENDPOINT_VALUE was added for attributing
    -- a call the backend never placed — given a SIP identity or number from a callback, find the
    -- agent — but that reconciliation is not built, and an index with no reader is a claim that
    -- something uses it. Add it back in the migration that adds the query.
    KEY `IDX1_PROVIDER_USER_ENDPOINTS_ROUTING` (`APP_CODE`, `USER_ID`, `IS_ACTIVE`, `PRIORITY`)

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`
  COLLATE = `utf8mb4_unicode_ci` COMMENT = 'Where a given agent can be reached, in ringing order. Read on every inbound connect applet.';


-- ---------------------------------------------------------------------------------------------
-- Existing call rows have to hold a SIP endpoint, not just a number.
-- ---------------------------------------------------------------------------------------------
--
-- message_exotel_calls predates browser calling and sized FROM and TO for E.164. A call placed from
-- an agent's browser originates at their SIP endpoint, and the provider reports it that way —
-- "sip:<sipId>" — which does not fit. Widened rather than truncated: the value identifies
-- which agent placed the call, and a silently clipped URI matches nothing.

ALTER TABLE `message`.`message_exotel_calls`
    MODIFY COLUMN `FROM` VARCHAR(64) DEFAULT NULL COMMENT 'Caller. An E.164 number, or a sip: URI when the leg is an agent''s WebRTC endpoint.',
    MODIFY COLUMN `TO` VARCHAR(64) DEFAULT NULL COMMENT 'Callee. An E.164 number, or a sip: URI when the leg is an agent''s WebRTC endpoint.';
