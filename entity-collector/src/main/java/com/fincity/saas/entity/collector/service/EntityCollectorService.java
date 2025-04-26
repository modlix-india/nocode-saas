package com.fincity.saas.entity.collector.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.collector.fiegn.IFeignCoreService;
import com.fincity.saas.entity.collector.jooq.enums.EntityCollectorLogStatus;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsInSourceType;
import com.fincity.saas.entity.collector.service.EntityCollectorLogService;
import com.fincity.saas.entity.collector.util.EntityUtil;
import com.fincity.saas.entity.collector.util.MetaEntityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import static com.fincity.saas.entity.collector.util.MetaEntityUtil.extractMetaPayload;
import static com.fincity.saas.entity.collector.util.MetaEntityUtil.fetchMetaData;
import static com.fincity.saas.entity.collector.util.MetaEntityUtil.normalizeMetaEntity;
import static com.fincity.saas.entity.collector.util.EntityUtil.fetchOAuthToken;
import static com.fincity.saas.entity.collector.util.EntityUtil.sendEntityToTarget;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntityCollectorService {

    private final EntityIntegrationService entityIntegrationService;
    private final EntityCollectorLogService entityCollectorLogService;
    private final EntityCollectorMessageResourceService entityCollectorMessageResourceService;
    private final ObjectMapper mapper;
    private final MetaEntityUtil metaEntityUtil;
    private final IFeignCoreService coreService;

    private static final String SUCCESS_ENTITY_MESSAGE = "SUCCESS_ENTITY_MESSAGE";
    private static final String FAILED_ENTITY_MESSAGE = "FAILED_ENTITY_MESSAGE";

    public Mono<JsonNode> handleMetaEntity(JsonNode responseBody) {

        return Mono.just(responseBody);
    }
}
