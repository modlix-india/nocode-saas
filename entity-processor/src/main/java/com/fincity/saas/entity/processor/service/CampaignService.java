package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.CampaignDAO;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.functions.AbstractProcessorFunction;
import com.fincity.saas.entity.processor.functions.IRepositoryProvider;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorCampaignsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.CampaignRequest;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import com.fincity.saas.entity.processor.util.ListFunctionRepository;
import com.fincity.saas.entity.processor.util.MapSchemaRepository;
import com.fincity.saas.entity.processor.util.SchemaUtil;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class CampaignService extends BaseUpdatableService<EntityProcessorCampaignsRecord, Campaign, CampaignDAO>
        implements IRepositoryProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(CampaignService.class);
    private static final String CAMPAIGN_CACHE = "campaign";

    private final List<ReactiveFunction> functions = new ArrayList<>();

    private final ProductService productService;
    private final Gson gson;

    @Autowired
    @Lazy
    private CampaignService self;

    public CampaignService(ProductService productService, Gson gson) {
        this.productService = productService;
        this.gson = gson;
    }

    @PostConstruct
    private void init() {

        this.functions.addAll(super.getCommonFunctions("Campaign", Campaign.class, gson));

        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "Campaign",
                "CreateRequest",
                Map.of(
                        "campaignRequest",
                        Parameter.of("campaignRequest", Schema.ofRef("EntityProcessor.Model.Request.CampaignRequest"))),
                "created",
                Schema.ofRef("EntityProcessor.DTO.Campaign"),
                "campaignRequest",
                CampaignRequest.class,
                gson,
                self,
                self::createRequest));
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

    public Mono<Campaign> createRequest(CampaignRequest campaignRequest) {

        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.productService.readByIdentity(access, campaignRequest.getProductId()),
                        (access, product) -> super.createInternal(
                                access, Campaign.of(campaignRequest).setProductId(product.getId())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignService.createRequest[CampaignRequest]"));
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
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {
        // Generate schema for Campaign class and create a repository with it
        Map<String, Schema> campaignSchemas = new HashMap<>();

        // TODO: Here we have blocked the Campaign class by annotating with
        // @IgnoreGeneration.
        // When we add dynamic fields the schema will be generated dynamically from DB.
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
