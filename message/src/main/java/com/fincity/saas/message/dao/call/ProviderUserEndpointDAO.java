package com.fincity.saas.message.dao.call;

import static com.fincity.saas.message.jooq.tables.MessageProviderUserEndpoints.MESSAGE_PROVIDER_USER_ENDPOINTS;

import com.fincity.saas.message.dao.base.BaseUpdatableDAO;
import com.fincity.saas.message.dto.call.ProviderUserEndpoint;
import com.fincity.saas.message.jooq.tables.records.MessageProviderUserEndpointsRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ProviderUserEndpointDAO
        extends BaseUpdatableDAO<MessageProviderUserEndpointsRecord, ProviderUserEndpoint> {

    public ProviderUserEndpointDAO() {
        super(ProviderUserEndpoint.class, MESSAGE_PROVIDER_USER_ENDPOINTS, MESSAGE_PROVIDER_USER_ENDPOINTS.ID);
    }

    /**
     * Every place this agent can be reached on a connection, in the order Exotel should try them.
     *
     * <p>The hot path: this runs on every inbound call, between the customer dialling and the phone
     * ringing, and {@code IDX1_PROVIDER_USER_ENDPOINTS_ROUTING} exists for exactly this query.
     *
     * <p>Deliberately not filtered on {@code CLIENT_CODE}. Security user ids are globally unique and
     * a user belongs to one client, so filtering on {@code USER_ID} already scopes the result to a
     * single tenant; the row's client code is that agent's by construction. Adding the filter would
     * mean resolving the agent's client code first, which is a Feign round trip in the path before
     * the phone can ring, to derive something the row already implies.
     */
    public Flux<ProviderUserEndpoint> findActiveEndpoints(
            String appCode, ULong userId, String connectionName, String provider) {

        return Flux.from(this.dslContext
                        .selectFrom(MESSAGE_PROVIDER_USER_ENDPOINTS)
                        .where(MESSAGE_PROVIDER_USER_ENDPOINTS.APP_CODE.eq(appCode))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.USER_ID.eq(userId))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.CONNECTION_NAME.eq(connectionName))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.PROVIDER.eq(provider))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.IS_ACTIVE.eq(Boolean.TRUE))
                        .orderBy(MESSAGE_PROVIDER_USER_ENDPOINTS.PRIORITY.asc()))
                .map(rec -> rec.into(ProviderUserEndpoint.class));
    }

    /**
     * Writes one endpoint, replacing the row already there for that agent on that connection.
     *
     * <p>An upsert rather than an insert, because re-provisioning is how an agent's numbers get
     * changed. {@code UK2_PROVIDER_USER_ENDPOINTS_AGENT} makes one row per agent, connection and
     * endpoint type, so a plain insert on the second run fails on the unique key — which is what
     * turned "update this agent's virtual number" into a duplicate-key error rather than an update.
     *
     * <p>Reactivates as part of the same write. An agent who was deactivated and is being set up
     * again should come back on their existing row rather than leaving an inactive duplicate behind
     * that the routing lookup skips and an operator still sees.
     */
    public Mono<ProviderUserEndpoint> upsert(ProviderUserEndpoint endpoint) {
        return this.updateExisting(endpoint).switchIfEmpty(Mono.defer(() -> this.insertOrConverge(endpoint)));
    }

    /**
     * Updates the row this agent already has, or completes empty when there is none.
     *
     * <p>Empty rather than inserting, so the insert lives in exactly one place. That matters for
     * the retry in {@link #insertOrConverge}: a fallback that could insert again would recurse.
     */
    private Mono<ProviderUserEndpoint> updateExisting(ProviderUserEndpoint endpoint) {

        return Mono.from(this.dslContext
                        .update(MESSAGE_PROVIDER_USER_ENDPOINTS)
                        .set(MESSAGE_PROVIDER_USER_ENDPOINTS.PROVIDER, endpoint.getProvider())
                        .set(MESSAGE_PROVIDER_USER_ENDPOINTS.ENDPOINT_VALUE, endpoint.getEndpointValue())
                        .set(MESSAGE_PROVIDER_USER_ENDPOINTS.PRIORITY, endpoint.getPriority())
                        .set(MESSAGE_PROVIDER_USER_ENDPOINTS.VIRTUAL_NUMBER, endpoint.getVirtualNumber())
                        .set(MESSAGE_PROVIDER_USER_ENDPOINTS.PROVIDER_USER_ID, endpoint.getProviderUserId())
                        .set(MESSAGE_PROVIDER_USER_ENDPOINTS.PROVIDER_METADATA, endpoint.getProviderMetadata())
                        .set(MESSAGE_PROVIDER_USER_ENDPOINTS.IS_ACTIVE, Boolean.TRUE)
                        // Rewritten too: the row records whoever provisioned last, and an owner
                        // in a parent client may provision an agent belonging to a child client.
                        // An audit field, not part of any key — per-agent operations locate rows by
                        // agent, and the caller's right to touch them is settled before they run.
                        .set(MESSAGE_PROVIDER_USER_ENDPOINTS.CLIENT_CODE, endpoint.getClientCode())
                        .where(MESSAGE_PROVIDER_USER_ENDPOINTS.APP_CODE.eq(endpoint.getAppCode()))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.USER_ID.eq(endpoint.getUserId()))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.CONNECTION_NAME.eq(endpoint.getConnectionName()))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.ENDPOINT_TYPE.eq(endpoint.getEndpointType())))
                .flatMap(updated -> updated > 0
                        ? this.findEndpoint(
                                endpoint.getAppCode(),
                                endpoint.getUserId(),
                                endpoint.getConnectionName(),
                                endpoint.getEndpointType())
                        : Mono.empty());
    }

    /**
     * Inserts, and converges rather than failing when a concurrent provision got there first.
     *
     * <p>The update-then-insert above is a read-modify-write, so two provisions of the same agent
     * can both see no row and both insert; one loses on {@code UK2_PROVIDER_USER_ENDPOINTS_AGENT}.
     * Retrying as an update makes the loser converge on the values it was going to write anyway,
     * which is what the caller wanted — the alternative is a duplicate-key error reaching an
     * operator who did nothing wrong. Same shape as {@code persistApp} in the calling service, for
     * the same reason.
     */
    private Mono<ProviderUserEndpoint> insertOrConverge(ProviderUserEndpoint endpoint) {

        return this.create(endpoint)
                .onErrorResume(e -> this.updateExisting(endpoint).switchIfEmpty(Mono.error(e)));
    }

    /**
     * One endpoint, by the columns its unique key uses.
     *
     * <p>Not scoped by client code, matching {@code UK2_PROVIDER_USER_ENDPOINTS_AGENT} as V26
     * defines it: an agent holds one endpoint of each type per connection, whichever tenant's owner
     * provisioned it.
     */
    public Mono<ProviderUserEndpoint> findEndpoint(
            String appCode, ULong userId, String connectionName, String endpointType) {

        return Mono.from(this.dslContext
                        .selectFrom(MESSAGE_PROVIDER_USER_ENDPOINTS)
                        .where(MESSAGE_PROVIDER_USER_ENDPOINTS.APP_CODE.eq(appCode))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.USER_ID.eq(userId))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.CONNECTION_NAME.eq(connectionName))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.ENDPOINT_TYPE.eq(endpointType)))
                .map(rec -> rec.into(ProviderUserEndpoint.class));
    }

    /**
     * Every endpoint mapped to one provider identity, across agents.
     *
     * <p>Exists to answer whether an identity is already claimed. Not scoped by client code on
     * purpose: the point is to catch a second agent anywhere under this app pointing at the same
     * provider user, and a check that stopped at the caller's own tenant would miss exactly the
     * cross-wiring it is meant to prevent.
     */
    public Flux<ProviderUserEndpoint> findByProviderUserId(String appCode, String providerUserId, String provider) {

        if (providerUserId == null || providerUserId.isBlank()) return Flux.empty();

        return Flux.from(this.dslContext
                        .selectFrom(MESSAGE_PROVIDER_USER_ENDPOINTS)
                        .where(MESSAGE_PROVIDER_USER_ENDPOINTS.APP_CODE.eq(appCode))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.PROVIDER_USER_ID.eq(providerUserId))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.PROVIDER.eq(provider))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.IS_ACTIVE.eq(Boolean.TRUE)))
                .map(rec -> rec.into(ProviderUserEndpoint.class));
    }

    /**
     * Every agent mapped on a connection. Admin listing, not a call path.
     *
     * <p><b>Still scoped by client code, unlike the per-agent operations.</b> A connection is
     * resolved per {@code (appCode, clientCode)}, so two sibling tenants can each hold one under the
     * same name — and dropping the filter here would show one tenant's agents in the other's
     * listing. Deactivation can safely key on the agent alone because the caller's right to that
     * agent is settled first; a listing has no such gate.
     *
     * <p>The cost is a blind spot worth knowing: a row written by a parent-client owner carries
     * their client code, so it will not appear in the child's listing even though the child's
     * inbound routing rings it. An agent provisioned from above is invisible from below.
     */
    public Flux<ProviderUserEndpoint> findByConnection(String appCode, String clientCode, String connectionName) {

        return Flux.from(this.dslContext
                        .selectFrom(MESSAGE_PROVIDER_USER_ENDPOINTS)
                        .where(MESSAGE_PROVIDER_USER_ENDPOINTS.APP_CODE.eq(appCode))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.CLIENT_CODE.eq(clientCode))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.CONNECTION_NAME.eq(connectionName))
                        .orderBy(
                                MESSAGE_PROVIDER_USER_ENDPOINTS.USER_ID.asc(),
                                MESSAGE_PROVIDER_USER_ENDPOINTS.PRIORITY.asc()))
                .map(rec -> rec.into(ProviderUserEndpoint.class));
    }

    /**
     * Removes every endpoint on a connection outright.
     *
     * <p>A hard delete, unlike {@link #deactivate}, because this runs only as part of tearing the
     * whole integration down: the provider-side app is gone, so the rows describe SIP identities that
     * no longer exist anywhere. Keeping them soft-deleted would leave the reverse lookup matching
     * endpoints that cannot ring.
     */
    public Mono<Integer> purgeByConnection(String appCode, String clientCode, String connectionName) {

        return Mono.from(this.dslContext
                .deleteFrom(MESSAGE_PROVIDER_USER_ENDPOINTS)
                .where(MESSAGE_PROVIDER_USER_ENDPOINTS.APP_CODE.eq(appCode))
                .and(MESSAGE_PROVIDER_USER_ENDPOINTS.CLIENT_CODE.eq(clientCode))
                .and(MESSAGE_PROVIDER_USER_ENDPOINTS.CONNECTION_NAME.eq(connectionName)));
    }

    /**
     * Soft-deletes every endpoint an agent holds on a connection.
     *
     * <p>Stops this service minting new tokens. It revokes nothing already issued and nothing at the
     * provider, so the provider-side user mapping has to be deleted too or the agent keeps a working
     * softphone until their session ends.
     */
    public Mono<Integer> deactivate(String appCode, ULong userId, String connectionName) {

        return Mono.from(this.dslContext
                .update(MESSAGE_PROVIDER_USER_ENDPOINTS)
                .set(MESSAGE_PROVIDER_USER_ENDPOINTS.IS_ACTIVE, Boolean.FALSE)
                .where(MESSAGE_PROVIDER_USER_ENDPOINTS.APP_CODE.eq(appCode))
                .and(MESSAGE_PROVIDER_USER_ENDPOINTS.USER_ID.eq(userId))
                .and(MESSAGE_PROVIDER_USER_ENDPOINTS.CONNECTION_NAME.eq(connectionName)));
    }
}
