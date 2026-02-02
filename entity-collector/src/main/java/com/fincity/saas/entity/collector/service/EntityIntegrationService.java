package com.fincity.saas.entity.collector.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.entity.collector.dao.EntityIntegrationDAO;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsInSourceType;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsStatus;
import com.fincity.saas.entity.collector.jooq.tables.records.EntityIntegrationsRecord;
import java.net.URI;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class EntityIntegrationService
        extends AbstractJOOQUpdatableDataService<
                EntityIntegrationsRecord, ULong, EntityIntegration, EntityIntegrationDAO> {

    private static final String HUB_MODE = "hub.mode";
    private static final String HUB_VERIFY_TOKEN = "hub.verify_token";
    private static final String SUBSCRIBE = "subscribe";
    private static final String HUB_CHALLENGE = "hub.challenge";
    private static final String CACHE_NAME_ENTITY_INTEGRATIONS = "EntityIntegrations";


    protected final CacheService cacheService;
    protected final EntityCollectorMessageResourceService entityCollectorMessageResourceService;

    public EntityIntegrationService(EntityCollectorMessageResourceService entityCollectorMessageResourceService, CacheService cacheService) {
        this.entityCollectorMessageResourceService = entityCollectorMessageResourceService;
        this.cacheService = cacheService;
    }

    public Mono<EntityIntegration> findByInSourceAndType(String inSource, EntityIntegrationsInSourceType inSourceType) {

        return this.cacheService
                .cacheValueOrGet(
                        CACHE_NAME_ENTITY_INTEGRATIONS,
                        () -> this.dao.findByInSourceAndInSourceType(inSource, inSourceType),
                        getCacheKeys(inSource, inSourceType))
                .switchIfEmpty(entityCollectorMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        EntityCollectorMessageResourceService.INTEGRATION_NOT_FOUND));
    }

    @Override
    public Mono<EntityIntegration> create(EntityIntegration entity) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> verifyTargetUrl(ca, entity),

                (ca, verified) -> super.create(entity));
    }

    @Override
    public Mono<EntityIntegration> update(EntityIntegration entity) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.read(entity.getId()),

                        (ca, existingEntity) -> verifyTargetUrl(ca, entity),

                        (ca,existingEntity, verified) -> this.cacheService.evict(
                                CACHE_NAME_ENTITY_INTEGRATIONS,
                                getCacheKeys(entity.getInSource(), entity.getInSourceType())),

                        (ca,existingEntity, verified, evicted) -> super.update(entity))
                .switchIfEmpty(entityCollectorMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        EntityCollectorMessageResourceService.OBJECT_NOT_FOUND));
    }

    @Override
    public Mono<EntityIntegration> updatableEntity(EntityIntegration entity) {
        return this.read(entity.getId()).map(existing -> {
            existing.setPrimaryTarget(entity.getPrimaryTarget());
            existing.setSecondaryTarget(entity.getSecondaryTarget());
            existing.setPrimaryVerifyToken(entity.getPrimaryVerifyToken());
            existing.setSecondaryVerifyToken(entity.getSecondaryVerifyToken());
            existing.setInSource(entity.getInSource());
            existing.setInSourceType(entity.getInSourceType());
            existing.setStatus(entity.getStatus());
            return existing;
        });
    }

    @Override
    protected Mono<ULong> getLoggedInUserId() {
        return SecurityContextUtil.getUsersContextUser().map(ContextUser::getId).map(ULong::valueOf);
    }

    @Override
    public Mono<Integer> delete(ULong id) {

        return this.read(id)
                .map(e -> {
                    e.setStatus(EntityIntegrationsStatus.DELETED);
                    return e;
                })
                .flatMap(super::update)
                .flatMap(e -> this.cacheService
                        .evict(CACHE_NAME_ENTITY_INTEGRATIONS, getCacheKeys(e.getInSource(), e.getInSourceType()))
                        .map(x -> 1));
    }

    private Mono<Boolean> sendVerificationRequest(
            ContextAuthentication ca, String targetUrl, String verifyToken, int challenge) {

        URI targetUri = URI.create(targetUrl);

        WebClient webClient = WebClient.builder().build();

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(targetUri.getScheme())
                        .host(targetUri.getHost())
                        .path(targetUri.getPath())
                        .queryParam(HUB_MODE, SUBSCRIBE)
                        .queryParam(HUB_VERIFY_TOKEN, verifyToken)
                        .queryParam(HUB_CHALLENGE, challenge)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> Integer.parseInt(response) == challenge)
                .onErrorResume(e -> Mono.just(false))
                .flatMap(success -> {
                    if (!success) {
                        return entityCollectorMessageResourceService
                                .throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        EntityCollectorMessageResourceService.VERIFICATION_FAILED,
                                        targetUrl)
                                .thenReturn(false);
                    }
                    return Mono.just(true);
                });
    }

    private Mono<Boolean> verifyTargetUrl(ContextAuthentication ca, EntityIntegration entity) {

        int challenge = UniqueUtil.shortUUID().hashCode();

        return FlatMapUtil.flatMapMono(

                () -> sendVerificationRequest(ca, entity.getPrimaryTarget(), entity.getPrimaryVerifyToken(), challenge),

                primaryVerified -> {
                    if (entity.getSecondaryTarget() == null || entity.getSecondaryVerifyToken() == null) {
                        return Mono.just(true);
                    }
                    return sendVerificationRequest(ca, entity.getSecondaryTarget(), entity.getSecondaryVerifyToken(), challenge);
                });
    }

    private Object[] getCacheKeys(String inSource, EntityIntegrationsInSourceType inSourceType) {
        return new Object[] {inSourceType.toString(), ":", inSource};
    }
}
