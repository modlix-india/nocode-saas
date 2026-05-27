package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCampaignProducts.ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCampaigns.ENTITY_PROCESSOR_CAMPAIGNS;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorCampaignsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.jooq.InsertValuesStep5;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CampaignDAO extends BaseUpdatableDAO<EntityProcessorCampaignsRecord, Campaign> {

    protected CampaignDAO() {
        super(Campaign.class, ENTITY_PROCESSOR_CAMPAIGNS, ENTITY_PROCESSOR_CAMPAIGNS.ID);
    }

    public Mono<Campaign> readByCampaignId(ProcessorAccess access, String campaignId) {

        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(ENTITY_PROCESSOR_CAMPAIGNS
                                .CAMPAIGN_ID
                                .eq(campaignId)
                                .and(ENTITY_PROCESSOR_CAMPAIGNS.APP_CODE.eq(access.getAppCode()))
                                .and(ENTITY_PROCESSOR_CAMPAIGNS.CLIENT_CODE.eq(access.getClientCode()))))
                .map(e -> e.into(this.pojoClass));
    }

    /**
     * Cross-tenant scan: every active campaign with a known platform. Used by the
     * worker-driven sync jobs (no caller security context — relies on the worker's
     * SYSTEM tenant).
     */
    public Flux<Campaign> findAllActive() {

        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        .where(ENTITY_PROCESSOR_CAMPAIGNS
                                .IS_ACTIVE
                                .isTrue()
                                .and(ENTITY_PROCESSOR_CAMPAIGNS.CAMPAIGN_PLATFORM.isNotNull())))
                .map(e -> e.into(this.pojoClass));
    }

    /**
     * First campaign in the caller's tenant on the given platform that carries a
     * resolved account context ({@code PLATFORM_ACCOUNT_ID} not null). Used to
     * derive the Google customer/login id for conversion-action listing without a
     * per-campaign context (conversion actions are account-level). Empty when the
     * client has no enabled campaign on that platform yet.
     */
    public Mono<Campaign> findAccountForClient(ProcessorAccess access, CampaignPlatform platform) {

        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(ENTITY_PROCESSOR_CAMPAIGNS
                                .APP_CODE
                                .eq(access.getAppCode())
                                .and(ENTITY_PROCESSOR_CAMPAIGNS.CLIENT_CODE.eq(access.getClientCode()))
                                .and(ENTITY_PROCESSOR_CAMPAIGNS.CAMPAIGN_PLATFORM.eq(platform))
                                .and(ENTITY_PROCESSOR_CAMPAIGNS.PLATFORM_ACCOUNT_ID.isNotNull()))
                        .limit(1))
                .map(e -> e.into(this.pojoClass));
    }

    /**
     * Targeted UPDATE for the three platform-context columns. Used by
     * {@code MetricsSyncService} when a platform-service's {@code ensurePlatformContext}
     * lazily resolves missing IDs from the platform API. Each column is updated
     * only when the provided value is non-null, so callers can backfill one
     * field without clobbering the others. Returns the number of rows affected
     * (0 if all three inputs were null — i.e. nothing to do).
     */
    public Mono<Integer> updatePlatformIds(
            ULong id, String platformAccountId, String platformLoginId, String platformDatasetId) {

        if (platformAccountId == null && platformLoginId == null && platformDatasetId == null) {
            return Mono.just(0);
        }

        var update = this.dslContext.update(this.table);
        var setStep = (platformAccountId != null)
                ? update.set(ENTITY_PROCESSOR_CAMPAIGNS.PLATFORM_ACCOUNT_ID, platformAccountId)
                : null;
        if (platformLoginId != null) {
            setStep = (setStep != null ? setStep : update)
                    .set(ENTITY_PROCESSOR_CAMPAIGNS.PLATFORM_LOGIN_ID, platformLoginId);
        }
        if (platformDatasetId != null) {
            setStep = (setStep != null ? setStep : update)
                    .set(ENTITY_PROCESSOR_CAMPAIGNS.PLATFORM_DATASET_ID, platformDatasetId);
        }
        return Mono.from(setStep.where(ENTITY_PROCESSOR_CAMPAIGNS.ID.eq(id)));
    }

    // ---- Campaign <-> Product many-to-many (entity_processor_campaign_products) ----

    /** Product ids linked to one campaign, via the join table. */
    public Flux<ULong> findProductIdsForCampaign(ULong campaignId) {

        return Flux.from(this.dslContext
                        .select(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.PRODUCT_ID)
                        .from(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS)
                        .where(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.CAMPAIGN_ID.eq(campaignId)))
                .map(r -> r.get(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.PRODUCT_ID));
    }

    /**
     * Campaign ids associated with a product for a tenant. Used by the report
     * filter to scope rows through the join table.
     */
    public Flux<ULong> findCampaignIdsForProduct(String appCode, String clientCode, ULong productId) {

        return Flux.from(this.dslContext
                        .select(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.CAMPAIGN_ID)
                        .from(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS)
                        .where(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS
                                .PRODUCT_ID
                                .eq(productId)
                                .and(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.APP_CODE.eq(appCode))
                                .and(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.CLIENT_CODE.eq(clientCode))))
                .map(r -> r.get(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.CAMPAIGN_ID));
    }

    /** Bulk hydration: campaign id -> its linked product ids, for a set of campaigns. */
    public Mono<Map<ULong, List<ULong>>> productIdsByCampaigns(List<ULong> campaignIds) {

        if (campaignIds == null || campaignIds.isEmpty()) return Mono.just(Map.of());

        return Flux.from(this.dslContext
                        .select(
                                ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.CAMPAIGN_ID,
                                ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.PRODUCT_ID)
                        .from(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS)
                        .where(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.CAMPAIGN_ID.in(campaignIds)))
                .collect(
                        java.util.HashMap::new,
                        (Map<ULong, List<ULong>> acc, org.jooq.Record2<ULong, ULong> r) -> acc
                                .computeIfAbsent(
                                        r.get(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.CAMPAIGN_ID),
                                        k -> new ArrayList<>())
                                .add(r.get(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.PRODUCT_ID)));
    }

    /**
     * Set semantics: replaces the campaign's full product link set with
     * {@code productIds}. Deletes existing links then inserts the new set in one
     * chained operation. An empty/null list clears all links. Duplicate ids are
     * de-duplicated (preserving order). Returns the number of links written.
     */
    public Mono<Integer> setProducts(ProcessorAccess access, ULong campaignId, List<ULong> productIds) {

        Mono<Integer> deleteExisting = Mono.from(this.dslContext
                        .deleteFrom(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS)
                        .where(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.CAMPAIGN_ID.eq(campaignId)))
                .map(Number::intValue)
                .defaultIfEmpty(0);

        List<ULong> distinct = (productIds == null)
                ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(productIds.stream().filter(p -> p != null).toList()));

        if (distinct.isEmpty()) return deleteExisting.thenReturn(0);

        InsertValuesStep5<?, ULong, ULong, String, String, ULong> insert = this.dslContext.insertInto(
                ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS,
                ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.CAMPAIGN_ID,
                ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.PRODUCT_ID,
                ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.APP_CODE,
                ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.CLIENT_CODE,
                ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.CREATED_BY);

        for (ULong productId : distinct)
            insert = insert.values(
                    campaignId, productId, access.getAppCode(), access.getClientCode(), access.getUserId());

        final InsertValuesStep5<?, ULong, ULong, String, String, ULong> finalInsert = insert;

        return deleteExisting.then(Mono.from(finalInsert)).thenReturn(distinct.size());
    }

    /**
     * Keeps the deprecated {@code PRODUCT_ID} column in sync with the "primary"
     * product (first of the linked set, or null when none) so legacy readers stay
     * consistent. Targeted single-column update; does not touch other fields.
     */
    public Mono<Integer> updatePrimaryProduct(ULong id, ULong productId) {

        return Mono.from(this.dslContext
                        .update(this.table)
                        .set(ENTITY_PROCESSOR_CAMPAIGNS.PRODUCT_ID, productId)
                        .where(ENTITY_PROCESSOR_CAMPAIGNS.ID.eq(id)))
                .map(Number::intValue)
                .defaultIfEmpty(0);
    }

    /** Removes a single campaign-product link. Returns rows affected (0 if none). */
    public Mono<Integer> unlinkProduct(ULong campaignId, ULong productId) {

        return Mono.from(this.dslContext
                        .deleteFrom(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS)
                        .where(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS
                                .CAMPAIGN_ID
                                .eq(campaignId)
                                .and(ENTITY_PROCESSOR_CAMPAIGN_PRODUCTS.PRODUCT_ID.eq(productId))))
                .map(Number::intValue)
                .defaultIfEmpty(0);
    }
}
