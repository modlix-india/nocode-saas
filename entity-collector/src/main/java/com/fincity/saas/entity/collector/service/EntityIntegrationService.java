package com.fincity.saas.entity.collector.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.entity.collector.dao.EntityIntegrationDAO;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.jooq.tables.records.EntityIntegrationsRecord;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsInSourceType;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

@Service
public class EntityIntegrationService
        extends AbstractJOOQUpdatableDataService<
        EntityIntegrationsRecord, ULong, EntityIntegration, EntityIntegrationDAO> {


    public Mono<EntityIntegration> findByInSourceAndType(String inSource, EntityIntegrationsInSourceType inSourceType) {
        return this.dao.findByInSourceAndInSourceType(inSource, inSourceType);
    }

    @Override
    public Mono<EntityIntegration> updatableEntity(EntityIntegration entity) {
        return this.read(entity.getId()).flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
                .map(ca -> {
                    // TO:DO add fields
                    return existing;
                }));
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

        Map<String, Object> newFields = new HashMap<>();

        // TO:DO add updatable fields to map

        return Mono.just(newFields);
    }

    @Override
    public Mono<EntityIntegration> create(EntityIntegration entity){
        return FlatMapUtil.flatMapMono(
                () -> verifyTargetUrl(entity),
                isVerified -> {
                    if (!isVerified) {
                        return Mono.error(new RuntimeException("Verification failed!"));
                    }
                    return super.create(entity);
                }
        );
    }


    private Mono<Boolean> verifyTargetUrl(EntityIntegration entity){
        String challenge = "1243";
        URI targetUri;

        WebClient webClient = WebClient.builder().build();

        try {
            targetUri = new URI(entity.getPrimaryTarget());
        } catch (URISyntaxException e) {
            return Mono.error(new RuntimeException("Invalid URL in target: " + entity.getPrimaryTarget(), e));
        }

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(targetUri.getScheme())
                        .host(targetUri.getHost())
                        .path(targetUri.getPath())
                        .queryParam("hub.mode", "subscribe")
                        .queryParam("hub.verify_token", entity.getPrimaryVerifyToken())
                        .queryParam("hub.challenge", challenge)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> response != null && response.equals(challenge));

    }
}
