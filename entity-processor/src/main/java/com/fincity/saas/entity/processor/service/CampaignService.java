package com.fincity.saas.entity.processor.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.CampaignDAO;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.functions.IRepositoryProvider;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorCampaignsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.CampaignRequest;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import com.fincity.saas.entity.processor.util.MapSchemaRepository;
import com.fincity.saas.entity.processor.util.SchemaUtil;
import com.google.gson.Gson;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class CampaignService extends BaseUpdatableService<EntityProcessorCampaignsRecord, Campaign, CampaignDAO>
        implements IRepositoryProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(CampaignService.class);
    private static final String CAMPAIGN_CACHE = "campaign";

    private final ProductService productService;
    private final Gson gson;

    public CampaignService(
            ProductService productService,
            Gson gson) {
        this.productService = productService;
        this.gson = gson;
    }

    @Override
    protected String getCacheName() {
        return CAMPAIGN_CACHE;
    }

    @Override
    protected boolean canOutsideCreate() {
        return false;
    }

    @Override
    protected Mono<Boolean> evictCache(Campaign entity) {
        return Mono.zip(
                super.evictCache(entity),
                super.cacheService.evict(
                        this.getCacheName(),
                        super.getCacheKey(entity.getAppCode(), entity.getClientCode(), entity.getCampaignId())),
                (baseEvicted, campaignEvicted) -> baseEvicted && campaignEvicted);
    }

    public Mono<Campaign> create(CampaignRequest campaignRequest) {

        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.productService.readByIdentity(access, campaignRequest.getProductId()),
                (access, product) -> super.createInternal(
                        access, Campaign.of(campaignRequest).setProductId(product.getId())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignService.create[CampaignRequest]"));
    }

    @Override
    protected Mono<Campaign> updatableEntity(Campaign campaign) {
        return super.updatableEntity(campaign)
                .flatMap(existing -> {
                    existing.setProductId(campaign.getProductId());
                    existing.setCampaignName(campaign.getCampaignName());
                    existing.setCampaignType(campaign.getCampaignType());
                    existing.setCampaignPlatform(campaign.getCampaignPlatform());

                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignService.updatableEntity"));
    }

    public Mono<Campaign> readByCampaignId(ProcessorAccess access, String campaignId) {
        return super.cacheService
                .cacheValueOrGet(
                        this.getCacheName(),
                        () -> this.dao.readByCampaignId(access, campaignId),
                        super.getCacheKey(access.getAppCode(), access.getClientCode(), campaignId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignService.readByCampaignId"));
    }

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.empty();
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(ReactiveRepository<Schema> staticSchemaRepository,
            String appCode, String clientCode) {
        // Generate schema for Campaign class and create a repository with it
        Map<String, Schema> campaignSchemas = new HashMap<>();

        try {
            Class<?> campaignClass = Campaign.class;

            String namespace = SchemaUtil.getNamespaceForClass(campaignClass);
            String name = campaignClass.getSimpleName();

            Schema schema = SchemaUtil.generateSchemaForClass(campaignClass);
            if (schema != null) {
                campaignSchemas.put(namespace + "." + name, schema);
                LOGGER.info("Generated schema for Campaign class: {}.{}", namespace, name);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to generate schema for Campaign class: {}", e.getMessage(), e);
        }

        // If we have Campaign schema, create a hybrid repository combining static and
        // Campaign schemas
        if (!campaignSchemas.isEmpty()) {
            return Mono.just(new MapSchemaRepository(campaignSchemas));
        }

        return Mono.empty();
    }
}
