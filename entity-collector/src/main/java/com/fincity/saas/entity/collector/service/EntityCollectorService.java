package com.fincity.saas.entity.collector.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.collector.dto.EntityResponse;
import com.fincity.saas.entity.collector.dto.WebsiteDetails;
import com.fincity.saas.entity.collector.fiegn.IFeignCoreService;
import com.fincity.saas.entity.collector.jooq.enums.EntityCollectorLogStatus;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsInSourceType;
import com.fincity.saas.entity.collector.util.MetaEntityUtil;
import com.fincity.saas.entity.collector.util.WebsiteEntityUtil;
import com.fincity.saas.entity.collector.util.EntityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static com.fincity.saas.entity.collector.util.EntityUtil.fetchOAuthToken;
import static com.fincity.saas.entity.collector.util.MetaEntityUtil.extractMetaPayload;
import static com.fincity.saas.entity.collector.util.MetaEntityUtil.fetchMetaData;
import static com.fincity.saas.entity.collector.util.MetaEntityUtil.normalizeMetaEntity;
import static com.fincity.saas.entity.collector.util.WebsiteEntityUtil.getHost;
import static com.fincity.saas.entity.collector.util.WebsiteEntityUtil.handleWebsiteLeadNormalization;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntityCollectorService {

    private final EntityIntegrationService entityIntegrationService;
    private final EntityCollectorLogService entityCollectorLogService;
    private final EntityCollectorMessageResourceService entityCollectorMessageResponseService;
    private final ObjectMapper mapper;
    private final IFeignCoreService coreService;


    public Mono<Void> handleMetaEntity(JsonNode responseBody) {
        return FlatMapUtil.flatMapMonoWithNull(
                () -> Mono.just(extractMetaPayload(responseBody)),

                extractList -> extractList.flatMapMany(Flux::fromIterable)
                        .flatMap(extract -> processSingleExtract(extract, responseBody))
                        .then(),
                (extractList, ex) -> Mono.empty()
        );
    }

    private Mono<Void> processSingleExtract(MetaEntityUtil.ExtractPayload extract, JsonNode responseBody) {

        return FlatMapUtil.flatMapMonoWithNull(
                () -> Mono.just(extract),

                extractPayload -> entityIntegrationService
                        .findByInSourceAndType(extractPayload.formId(), EntityIntegrationsInSourceType.FACEBOOK_FORM),

                (extractPayload, integration) -> entityCollectorLogService.create(
                        integration.getId(),
                        mapper.convertValue(responseBody, new TypeReference<>() {
                        }), null),
                (extractPayload, integration, logId) -> fetchOAuthToken(
                        coreService,
                        integration.getClientCode(),
                        integration.getAppCode()),
                (extractPayload, integration, logId, token) -> fetchMetaData(
                        extractPayload.leadGenId(),
                        extractPayload.formId(),
                        token),
                (extractPayload, integration, logId, token, metaData) -> Mono.just(

                        normalizeMetaEntity(metaData.getT1(), metaData.getT2(), extractPayload.adId(), token, integration)),

                (extractPayload, integration, logId, token, metaData, normalizedEntity) ->
                        normalizedEntity.flatMap(response ->
                                entityCollectorMessageResponseService.getMessage(EntityCollectorMessageResourceService.SUCCESS_ENTITY_MESSAGE)
                                        .flatMap(successMessage ->
                                                EntityUtil.sendEntityToTarget(integration, response)
                                                        .then(entityCollectorLogService.update(logId, mapper.convertValue(response, new TypeReference<>() {
                                                                }),
                                                                EntityCollectorLogStatus.SUCCESS,
                                                                successMessage
                                                        ))
                                        ))
        ).then();
    }


    public Mono<EntityResponse> handleWebsiteEntity(WebsiteDetails websiteBody, ServerHttpRequest request) {

        return FlatMapUtil.flatMapMonoWithNull(
                () -> Mono.just(getHost(request)),
                host -> entityIntegrationService.findByInSourceAndType(host, EntityIntegrationsInSourceType.WEBSITE),
                (host, integration) -> entityCollectorLogService.create(
                        integration.getId(),
                        mapper.convertValue(websiteBody, new TypeReference<>() {
                        }),
                        null
                ),
                (host, integration, logId) -> handleWebsiteLeadNormalization(websiteBody, integration, coreService)
                        .flatMap(response ->
                                entityCollectorMessageResponseService
                                        .getMessage(EntityCollectorMessageResourceService.SUCCESS_ENTITY_MESSAGE)
                                        .flatMap(successMessage ->
                                                EntityUtil.sendEntityToTarget(integration, response)
                                                        .then(entityCollectorLogService.update(logId, mapper.convertValue(response, new TypeReference<>() {
                                                                }),
                                                                EntityCollectorLogStatus.SUCCESS, successMessage
                                                        ))
                                                        .thenReturn(response)
                                        )
                        )
        );
    }

}
