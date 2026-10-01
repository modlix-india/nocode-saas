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
     * Every place this agent can be reached on a connection, in ring order. Runs on every inbound call before the
     * phone rings; {@code IDX1_PROVIDER_USER_ENDPOINTS_ROUTING} exists for this query.
     *
     * <p>Deliberately not filtered on {@code CLIENT_CODE}: user ids are globally unique and a user belongs to one
     * client, so {@code USER_ID} already scopes to one tenant, and resolving the client code would add a Feign
     * round trip before the phone can ring.
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
     * The connections an agent holds an active endpoint of one type on. Not filtered on {@code CLIENT_CODE}, for
     * the reason {@link #findActiveEndpoints} gives.
     */
    public Flux<String> findActiveConnectionNames(String appCode, ULong userId, String endpointType) {

        return Flux.from(this.dslContext
                        .selectDistinct(MESSAGE_PROVIDER_USER_ENDPOINTS.CONNECTION_NAME)
                        .from(MESSAGE_PROVIDER_USER_ENDPOINTS)
                        .where(MESSAGE_PROVIDER_USER_ENDPOINTS.APP_CODE.eq(appCode))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.USER_ID.eq(userId))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.ENDPOINT_TYPE.eq(endpointType))
                        .and(MESSAGE_PROVIDER_USER_ENDPOINTS.IS_ACTIVE.eq(Boolean.TRUE)))
                .map(rec -> rec.get(MESSAGE_PROVIDER_USER_ENDPOINTS.CONNECTION_NAME));
    }

    /**
     * Writes one endpoint, replacing and reactivating the row already there for that agent, connection and type.
     * An upsert because re-provisioning is how an agent's numbers change, and
     * {@code UK2_PROVIDER_USER_ENDPOINTS_AGENT} allows one such row.
     */
    public Mono<ProviderUserEndpoint> upsert(ProviderUserEndpoint endpoint) {
        return this.updateExisting(endpoint).switchIfEmpty(Mono.defer(() -> this.insertOrConverge(endpoint)));
    }

    /**
     * Updates the row this agent already has, or completes empty. Never inserts, so the retry in
     * {@link #insertOrConverge} cannot recurse.
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
                        // Records whoever provisioned last, possibly a parent-client owner. An audit
                        // field, not part of any key: per-agent operations locate rows by agent.
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
     * Inserts, retrying as an update when a concurrent provision of the same agent won the race on
     * {@code UK2_PROVIDER_USER_ENDPOINTS_AGENT}, so the loser converges instead of surfacing a duplicate-key error.
     */
    private Mono<ProviderUserEndpoint> insertOrConverge(ProviderUserEndpoint endpoint) {

        return this.create(endpoint)
                .onErrorResume(e -> this.updateExisting(endpoint).switchIfEmpty(Mono.error(e)));
    }

    /** One endpoint, by the columns of {@code UK2_PROVIDER_USER_ENDPOINTS_AGENT}, which excludes client code. */
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
     * Every endpoint mapped to one provider identity, to tell whether it is already claimed. Not scoped by client
     * code on purpose: it must catch a second agent anywhere under this app pointing at the same provider user.
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
     * Every agent mapped on a connection, for admin listing. Scoped by client code, unlike the per-agent
     * operations, because sibling tenants can each hold a connection of the same name.
     *
     * <p>So a row written by a parent-client owner carries their client code and is missing from the child's
     * listing, though the child's inbound routing still rings it.
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
     * Hard-deletes every endpoint on a connection. Runs only on teardown, when the provider-side identities no
     * longer exist; soft-deleted rows would leave the reverse lookup matching endpoints that cannot ring.
     */
    public Mono<Integer> purgeByConnection(String appCode, String clientCode, String connectionName) {

        return Mono.from(this.dslContext
                .deleteFrom(MESSAGE_PROVIDER_USER_ENDPOINTS)
                .where(MESSAGE_PROVIDER_USER_ENDPOINTS.APP_CODE.eq(appCode))
                .and(MESSAGE_PROVIDER_USER_ENDPOINTS.CLIENT_CODE.eq(clientCode))
                .and(MESSAGE_PROVIDER_USER_ENDPOINTS.CONNECTION_NAME.eq(connectionName)));
    }

    /**
     * Soft-deletes every endpoint an agent holds on a connection. Revokes nothing already issued or at the
     * provider, so the provider-side user must be deleted too or the softphone works until the session ends.
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
