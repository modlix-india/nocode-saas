package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.functions.AbstractServiceFunction;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.repository.ListFunctionRepository;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.CampaignDAO;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorCampaignsRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.CampaignRequest;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class CampaignService extends BaseUpdatableService<EntityProcessorCampaignsRecord, Campaign, CampaignDAO>
        implements IRepositoryProvider {

    private static final String NAMESPACE = "EntityProcessor.Campaign";

    private final List<ReactiveFunction> functions = new ArrayList<>();

    private final ProductService productService;
    private final Gson gson;

    private static final ClassSchema classSchema =
            ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());

    @Autowired
    @Lazy
    private CampaignService self;

    public CampaignService(ProductService productService, Gson gson) {
        this.productService = productService;
        this.gson = gson;
    }

    @PostConstruct
    private void init() {

        this.functions.addAll(super.getCommonFunctions(NAMESPACE, Campaign.class, classSchema, gson));

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "CreateRequest",
                ClassSchema.ArgSpec.ofRef("campaignRequest", CampaignRequest.class, classSchema),
                "created",
                Schema.ofRef("EntityProcessor.DTO.Campaign"),
                gson,
                self::createRequest));
    }

    @Override
    protected boolean canOutsideCreate() {
        return false;
    }


    public Mono<Campaign> createRequest(CampaignRequest campaignRequest) {

        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.productService.readByIdentity(access, campaignRequest.getProductId()),
                        (access, product) -> {
                            if (!product.isActive())
                                return this.msgService.<Campaign>throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.PRODUCT_NOT_ACTIVE);
                            return super.createInternal(
                                    access, Campaign.of(campaignRequest).setProductId(product.getId()));
                        })
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
                    existing.setPlatformAccountId(campaign.getPlatformAccountId());
                    existing.setPlatformLoginId(campaign.getPlatformLoginId());
                    existing.setPlatformDatasetId(campaign.getPlatformDatasetId());

                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignService.updatableEntity"));
    }

    public Mono<Campaign> readByCampaignId(ProcessorAccess access, String campaignId) {
        return this.dao.readByCampaignId(access, campaignId)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignService.readByCampaignId"));
    }

    /** Cross-tenant: every active campaign with a known platform. Worker-driven syncs only. */
    public reactor.core.publisher.Flux<Campaign> findAllActive() {
        return this.dao.findAllActive()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignService.findAllActive"));
    }

    /**
     * Cross-tenant id lookup without {@code hasAccess()}. Worker-driven flows only
     * (e.g. {@code ConversionsDrainService}); never expose via a public controller.
     * Sibling of {@link #findAllActive()}.
     */
    public Mono<Campaign> findById(ULong id) {
        return this.dao.readById(id)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignService.findById"));
    }

    /**
     * Resolves the caller-tenant's account context on a platform from any campaign
     * that carries it (account-level identifiers are shared across the client's
     * campaigns). Empty when no campaign with a resolved account exists yet.
     */
    public Mono<Campaign> findPlatformAccount(CampaignPlatform platform) {
        return FlatMapUtil.flatMapMono(this::hasAccess, access -> this.dao.findAccountForClient(access, platform))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignService.findPlatformAccount"));
    }

    /**
     * Product ids linked to a campaign, without a caller security gate. For
     * internal/worker flows (e.g. ticket attribution) that run under a system
     * {@code ProcessorAccess} rather than a JWT context. Use {@link #getProductIds}
     * for user-facing calls.
     */
    public Mono<List<ULong>> findLinkedProductIds(ULong campaignId) {
        return this.dao.findProductIdsForCampaign(campaignId)
                .collectList()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignService.findLinkedProductIds"));
    }

    /** Product ids linked to a campaign via the many-to-many join. */
    public Mono<List<ULong>> getProductIds(ULong campaignId) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.read(campaignId),
                        (access, campaign) -> this.dao.findProductIdsForCampaign(campaignId).collectList())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignService.getProductIds"));
    }

    /**
     * Replaces a campaign's full product link set (set semantics). Each identity
     * is validated to be an active product in the caller's tenant. The deprecated
     * {@code PRODUCT_ID} column is synced to the primary (first) product. Returns
     * the campaign with {@code productIds} hydrated.
     */
    public Mono<Campaign> setProducts(ULong campaignId, List<Identity> productIdentities) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.read(campaignId),
                        (access, campaign) -> this.resolveActiveProductIds(access, productIdentities),
                        (access, campaign, productIds) -> this.dao
                                .setProducts(access, campaignId, productIds)
                                .then(this.dao.updatePrimaryProduct(
                                        campaignId, productIds.isEmpty() ? null : productIds.get(0)))
                                .thenReturn(campaign
                                        .setProductId(productIds.isEmpty() ? null : productIds.get(0))
                                        .setProductIds(productIds)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CampaignService.setProducts"));
    }

    private Mono<List<ULong>> resolveActiveProductIds(ProcessorAccess access, List<Identity> identities) {
        if (identities == null || identities.isEmpty()) return Mono.just(List.of());
        return reactor.core.publisher.Flux.fromIterable(identities)
                .concatMap(identity -> this.productService
                        .readByIdentity(access, identity)
                        .flatMap(product -> product.isActive()
                                ? Mono.just(product.getId())
                                : this.msgService.<ULong>throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.PRODUCT_NOT_ACTIVE)))
                .collectList();
    }

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {
        return this.defaultSchemaRepositoryFor(Campaign.class, classSchema);
    }
}
