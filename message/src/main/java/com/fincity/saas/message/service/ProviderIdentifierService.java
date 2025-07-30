package com.fincity.saas.message.service;

import com.fincity.saas.message.dao.ProviderIdentifierDAO;
import com.fincity.saas.message.dto.ProviderIdentifier;
import com.fincity.saas.message.jooq.tables.records.MessageProviderIdentifiersRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.base.BaseUpdatableService;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProviderIdentifierService
        extends BaseUpdatableService<MessageProviderIdentifiersRecord, ProviderIdentifier, ProviderIdentifierDAO> {

    private static final String PROVIDER_IDENTIFIER = "providerIdentifier";

    @Override
    protected String getCacheName() {
        return PROVIDER_IDENTIFIER;
    }

    @Override
    protected Mono<Boolean> evictCache(ProviderIdentifier entity) {
        return Mono.zip(this.evictProviderIdentifierCaches(entity), super.evictCache(entity))
                .map(tuple -> tuple.getT1() && tuple.getT2());
    }

    private Mono<Boolean> evictProviderIdentifierCaches(ProviderIdentifier entity) {

        String identifierCacheKey = super.getCacheKey(
                entity.getAppCode(),
                entity.getClientCode(),
                entity.getConnectionType(),
                entity.getConnectionSubType(),
                entity.getIdentifier());

        Mono<Boolean> evictIdentifier = this.cacheService.evict(this.getCacheName(), identifierCacheKey);

        if (entity.isDefault()) {
            Mono<Boolean> evictDefault = this.evictDefaultProviderIdentifierCache(
                    entity.getAppCode(), entity.getClientCode(),
                    entity.getConnectionType(), entity.getConnectionSubType());
            return Mono.zip(evictIdentifier, evictDefault).map(tuple -> tuple.getT1() && tuple.getT2());
        }

        return evictIdentifier;
    }

    private Mono<Boolean> evictDefaultProviderIdentifierCache(
            String appCode, String clientCode, ConnectionType connectionType, ConnectionSubType connectionSubType) {
        String defaultCacheKey = super.getCacheKey("default", appCode, clientCode, connectionType, connectionSubType);
        return this.cacheService.evict(this.getCacheName(), defaultCacheKey);
    }

    @Override
    protected Mono<ProviderIdentifier> updatableEntity(ProviderIdentifier entity) {
        return super.updatableEntity(entity).flatMap(uEntity -> {
            uEntity.setIdentifier(entity.getIdentifier());
            uEntity.setDefault(entity.isDefault());
            return Mono.just(uEntity);
        });
    }

    @Override
    public Mono<ProviderIdentifier> create(ProviderIdentifier entity) {
        return this.hasAccess().flatMap(access -> this.createInternal(access, entity));
    }

    @Override
    public Mono<ProviderIdentifier> createInternal(MessageAccess access, ProviderIdentifier entity) {
        entity.setAppCode(access.getAppCode());
        entity.setClientCode(access.getClientCode());
        entity.setUserId(access.getUserId());

        if (entity.isDefault())
            return this.clearExistingDefault(
                            access.getAppCode(),
                            access.getClientCode(),
                            entity.getConnectionType(),
                            entity.getConnectionSubType())
                    .then(super.create(entity));

        return super.create(entity);
    }

    @Override
    public Mono<ProviderIdentifier> update(ProviderIdentifier entity) {
        return this.hasAccess().flatMap(access -> this.updateInternal(access, entity));
    }

    public Mono<ProviderIdentifier> updateInternal(MessageAccess access, ProviderIdentifier entity) {
        if (entity.isDefault())
            return this.clearExistingDefault(
                            access.getAppCode(),
                            access.getClientCode(),
                            entity.getConnectionType(),
                            entity.getConnectionSubType(),
                            entity.getId())
                    .then(super.update(entity));

        return super.update(entity);
    }

    private Mono<Integer> clearExistingDefault(
            String appCode, String clientCode, ConnectionType connectionType, ConnectionSubType connectionSubType) {
        return this.clearExistingDefault(appCode, clientCode, connectionType, connectionSubType, null);
    }

    private Mono<Integer> clearExistingDefault(
            String appCode,
            String clientCode,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType,
            ULong excludeId) {
        return this.dao.clearExistingDefault(appCode, clientCode, connectionType, connectionSubType, excludeId);
    }

    public Mono<ProviderIdentifier> getProviderIdentifier(
            String appCode,
            String clientCode,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType,
            String identifier) {
        String cacheKey = super.getCacheKey(appCode, clientCode, connectionType, connectionSubType, identifier);
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.findByIdentifier(appCode, clientCode, connectionType, connectionSubType, identifier),
                cacheKey);
    }

    public Mono<ProviderIdentifier> getDefaultProviderIdentifier(
            String appCode, String clientCode, ConnectionType connectionType, ConnectionSubType connectionSubType) {
        String cacheKey = super.getCacheKey("default", appCode, clientCode, connectionType, connectionSubType);
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.findDefaultIdentifier(appCode, clientCode, connectionType, connectionSubType),
                cacheKey);
    }

    public Mono<ProviderIdentifier> findByIdentifierOnly(
            ConnectionType connectionType, ConnectionSubType connectionSubType, String identifier) {
        String cacheKey = super.getCacheKey("identifier-only", connectionType, connectionSubType, identifier);
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.findByIdentifierOnly(connectionType, connectionSubType, identifier),
                cacheKey);
    }
}
