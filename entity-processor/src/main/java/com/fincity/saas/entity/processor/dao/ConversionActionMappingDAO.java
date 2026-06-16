package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorConversionActionMapping.ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.ConversionActionMapping;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorConversionActionMappingRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import org.jooq.Condition;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ConversionActionMappingDAO
        extends BaseUpdatableDAO<EntityProcessorConversionActionMappingRecord, ConversionActionMapping> {

    protected ConversionActionMappingDAO() {
        super(
                ConversionActionMapping.class,
                ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING,
                ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING.ID);
    }

    /**
     * Finds active mappings whose trigger matches the given (stage, status) for the
     * caller's app/client. {@code statusId} is matched exactly when non-null;
     * mappings with a NULL TRIGGER_STATUS_ID are always considered (they apply to
     * any status under the given stage).
     *
     * <p>When {@code platformAccountId} is non-null, returned mappings must either
     * match it exactly OR have a NULL PLATFORM_ACCOUNT_ID (Meta, where the pixel
     * routes via the campaign itself, and legacy rows that predate the column).
     * Google conversion actions live inside one specific customer's account, so
     * dispatching a Purva ticket through a Cityville customer's action gets
     * rejected by Google with INVALID_CUSTOMER_FOR_CLICK.
     */
    public Flux<ConversionActionMapping> findActiveByTrigger(
            ProcessorAccess access,
            ULong stageId,
            ULong statusId,
            ULong productTemplateId,
            String platformAccountId) {

        Condition statusMatch = ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING
                .TRIGGER_STATUS_ID
                .isNull()
                .or(statusId == null
                        ? org.jooq.impl.DSL.noCondition()
                        : ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING.TRIGGER_STATUS_ID.eq(statusId));

        Condition productTemplateMatch = ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING
                .PRODUCT_TEMPLATE_ID
                .isNull()
                .or(productTemplateId == null
                        ? org.jooq.impl.DSL.noCondition()
                        : ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING.PRODUCT_TEMPLATE_ID.eq(productTemplateId));

        Condition accountMatch = (platformAccountId == null || platformAccountId.isBlank())
                ? org.jooq.impl.DSL.noCondition()
                : ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING
                        .PLATFORM_ACCOUNT_ID
                        .isNull()
                        .or(ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING.PLATFORM_ACCOUNT_ID.eq(platformAccountId));

        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        .where(ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING
                                .APP_CODE
                                .eq(access.getAppCode())
                                .and(ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING.CLIENT_CODE.eq(access.getClientCode()))
                                .and(ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING.TRIGGER_STAGE_ID.eq(stageId))
                                .and(statusMatch)
                                .and(productTemplateMatch)
                                .and(accountMatch)
                                .and(ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING.IS_ACTIVE.isTrue())))
                .map(e -> e.into(this.pojoClass));
    }

    /**
     * Finds an existing mapping for the unique tuple. Used by seedDefaults and
     * applyFunnel to skip duplicates. {@code platformAccountId} narrows to a
     * specific customer when non-null (matching the widened unique key); when
     * null, matches the legacy row with NULL PLATFORM_ACCOUNT_ID.
     */
    public reactor.core.publisher.Mono<ConversionActionMapping> findExisting(
            ProcessorAccess access,
            ULong productTemplateId,
            CampaignPlatform platform,
            String platformAccountId,
            ULong triggerStageId,
            ULong triggerStatusId) {

        Condition productTemplateMatch = productTemplateId == null
                ? ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING.PRODUCT_TEMPLATE_ID.isNull()
                : ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING.PRODUCT_TEMPLATE_ID.eq(productTemplateId);

        Condition statusMatch = triggerStatusId == null
                ? ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING.TRIGGER_STATUS_ID.isNull()
                : ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING.TRIGGER_STATUS_ID.eq(triggerStatusId);

        Condition accountMatch = (platformAccountId == null || platformAccountId.isBlank())
                ? ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING.PLATFORM_ACCOUNT_ID.isNull()
                : ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING.PLATFORM_ACCOUNT_ID.eq(platformAccountId);

        return reactor.core.publisher.Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING
                                .APP_CODE
                                .eq(access.getAppCode())
                                .and(ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING.CLIENT_CODE.eq(access.getClientCode()))
                                .and(productTemplateMatch)
                                .and(ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING.CAMPAIGN_PLATFORM.eq(platform))
                                .and(accountMatch)
                                .and(ENTITY_PROCESSOR_CONVERSION_ACTION_MAPPING.TRIGGER_STAGE_ID.eq(triggerStageId))
                                .and(statusMatch)))
                .map(e -> e.into(this.pojoClass));
    }
}
