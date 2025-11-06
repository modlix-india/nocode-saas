package com.fincity.saas.entity.collector.service;

import static com.fincity.saas.entity.collector.util.EntityUtil.getClientIpAddress;
import static com.fincity.saas.entity.collector.util.MetaEntityUtil.extractMetaPayload;
import static com.fincity.saas.entity.collector.util.MetaEntityUtil.fetchMetaData;
import static com.fincity.saas.entity.collector.util.MetaEntityUtil.normalizeMetaEntity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.dto.EntityResponse;
import com.fincity.saas.entity.collector.dto.WebsiteDetails;
import com.fincity.saas.entity.collector.jooq.enums.EntityCollectorLogStatus;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsInSourceType;
import com.fincity.saas.entity.collector.model.LeadDetails;
import com.fincity.saas.entity.collector.service.commons.AbstractConnectionService;
import com.fincity.saas.entity.collector.util.EntityUtil;
import com.fincity.saas.entity.collector.util.GoogleEntityUtil;
import com.fincity.saas.entity.collector.util.MetaEntityUtil;
import lombok.extern.slf4j.Slf4j;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
public class EntityCollectorService extends AbstractConnectionService {

    private final EntityIntegrationService entityIntegrationService;
    private final EntityCollectorLogService entityCollectorLogService;
    private final ObjectMapper mapper;
    private static final Logger logger = LoggerFactory.getLogger(EntityCollectorService.class);

    public EntityCollectorService(
            EntityIntegrationService entityIntegrationService,
            EntityCollectorLogService entityCollectorLogService,
            ObjectMapper mapper) {
        this.entityIntegrationService = entityIntegrationService;
        this.entityCollectorLogService = entityCollectorLogService;
        this.mapper = new ObjectMapper();
    }

    public Mono<Void> processMetaFormEntity(JsonNode responseBody) {

        logger.info("Processing meta lead form entity: {}", responseBody);

        return FlatMapUtil.flatMapMonoWithNull(

                () -> extractMetaPayload(responseBody),

                extractList -> Flux.fromIterable(extractList)
                        .flatMap(extract -> FlatMapUtil.flatMapMonoWithNull(

                                () -> Mono.just(extract),

                                extractPayload -> entityIntegrationService.findByInSourceAndType(
                                        extractPayload.formId(), EntityIntegrationsInSourceType.FACEBOOK_FORM),

                                (extractPayload, integration) -> entityCollectorLogService.create(
                                        integration.getId(), mapper.convertValue(responseBody, new TypeReference<>() {}), null),

                                (extractPayload, integration, logId) -> this.getConnectionOAuth2Token(
                                                 integration.getInAppCode(), integration.getClientCode(), EntityUtil.META_CONNECTION_NAME)
                                        // Treat blank token as empty to stop the chain
                                        .filter(token -> token != null && !token.isBlank())
                                        // If empty/blank, log and STOP further processing for this item
                                        .switchIfEmpty(entityCollectorLogService
                                                .updateOnError(logId, "OAuth token fetch returned empty/blank")
                                                .then(Mono.empty()))
                                        // If token retrieval errors, log and STOP
                                        .onErrorResume(ex -> entityCollectorLogService
                                                .updateOnError(logId, "OAuth token fetch failed: " + ex.getMessage())
                                                .then(Mono.empty())),

                                (extractPayload, integration, logId, token) -> fetchMetaData(
                                        extractPayload.leadGenId(),
                                        extractPayload.formId(),
                                        token,
                                        entityCollectorLogService,
                                        logId),

                                (extractPayload, integration, logId, token, metaData) -> Mono.just(normalizeMetaEntity(
                                        metaData.getT1(),
                                        metaData.getT2(),
                                        extractPayload.adId(),
                                        token,
                                        integration,
                                        msgService,
                                        entityCollectorLogService,
                                        logId)),
                                (extractPayload, integration, logId, token, metaData, normalizedEntity) ->
                                        normalizedEntity.flatMap(response -> msgService
                                                .getMessage(
                                                        EntityCollectorMessageResourceService.SUCCESS_ENTITY_MESSAGE)
                                                .flatMap(successMessage -> EntityUtil.sendEntityToTarget(
                                                                integration, response, null)
                                                        .then(entityCollectorLogService.update(
                                                                logId,
                                                                mapper.convertValue(response, new TypeReference<>() {}),
                                                                EntityCollectorLogStatus.SUCCESS,
                                                                successMessage))))))
                        .then(),
                (extractList, ex) -> Mono.empty());
    }

    public Mono<Map<String, Object>> processWebsiteFormEntity(WebsiteDetails websiteBody, ServerHttpRequest request) {

        return FlatMapUtil.flatMapMonoWithNull(

                () -> Mono.just(EntityUtil.getHost(request)),

                host -> entityIntegrationService.findByInSourceAndType(host, EntityIntegrationsInSourceType.WEBSITE),

                (host, integration) -> entityCollectorLogService.create(
                        integration.getId(),
                        mapper.convertValue(websiteBody, new TypeReference<>() {}),
                        getClientIpAddress(request)),

                (host, integration, logId) -> handleWebsiteEntity(
                        websiteBody, integration, logId),

                (host, integration, logId, response) ->
                        msgService.getMessage(EntityCollectorMessageResourceService.SUCCESS_ENTITY_MESSAGE),

                (host, integration, logId, response, sMessage) -> EntityUtil.sendEntityToTarget(integration, response, websiteBody.getProductURL()),

                (host, integration, logId, response, sMessage, result) -> this.entityCollectorLogService.update(
                        logId,
                        mapper.convertValue(response, new TypeReference<>() {}),
                        EntityCollectorLogStatus.SUCCESS,
                        sMessage),

                (host, integration, logId, response, sMessage, result, uLog) -> Mono.just(result));
    }

    private Mono<EntityResponse> handleWebsiteEntity(WebsiteDetails websiteDetails, EntityIntegration integration, ULong logId) {

        LeadDetails lead = new LeadDetails().createLead(websiteDetails);

        EntityResponse response = new EntityResponse();
        response.setLeadDetails(lead);
        response.setAppCode(integration.getOutAppCode());
        response.setClientCode(integration.getClientCode());

        String connectionName;
        if (websiteDetails.getUtmSource().equals(EntityUtil.GOOGLE_UTM_SOURCE))
            connectionName = EntityUtil.GOOGLE_CONNECTION_NAME;
        else if ( websiteDetails.getUtmSource().equals(EntityUtil.META_UTM_SOURCE))
            connectionName = EntityUtil.META_CONNECTION_NAME;
        else {
            connectionName = null;
        }

        return connectionName == null ? Mono.just(response) : processCampaignForWebsite(response, websiteDetails, integration, connectionName, logId);

    }

    private Mono<EntityResponse> processCampaignForWebsite(
            EntityResponse response,
            WebsiteDetails websiteDetails,
            EntityIntegration integration,
            String connectionName,
            ULong logId) {

        String adId = websiteDetails.getUtmAd();

        if (adId == null || adId.isBlank()) {
            return Mono.just(response);
        }

        return FlatMapUtil.flatMapMonoWithNull(

                        () -> this.getConnectionOAuth2Token(
                                        integration.getInAppCode(), integration.getClientCode(), connectionName)
                                .onErrorResume(ex -> entityCollectorLogService
                                        .updateOnError(logId, "OAuth token fetch failed: " + ex.getMessage())
                                        .then(Mono.empty())),

                        token -> websiteDetails.getUtmSource().equals(EntityUtil.GOOGLE_UTM_SOURCE)
                                ? GoogleEntityUtil.buildCampaignDetails(websiteDetails.getUtmLoginCustomer(), websiteDetails.getUtmCustomer(), adId, token)
                                : MetaEntityUtil.buildCampaignDetails(adId, token),

                        (token, campaignDetails) -> {
                            response.setCampaignDetails(campaignDetails);
                            return Mono.just(response);
                        })
                .switchIfEmpty(this.msgService
                        .getMessage(EntityCollectorMessageResourceService.FAILED_NORMALIZE_ENTITY)
                        .flatMap(msg -> this.entityCollectorLogService
                                .updateOnError(logId, msg)
                                .then(Mono.empty())));
    }
}
