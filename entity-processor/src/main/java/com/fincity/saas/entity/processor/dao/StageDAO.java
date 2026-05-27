package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages.ENTITY_PROCESSOR_STAGES;

import com.fincity.saas.entity.processor.dao.base.BaseValueDAO;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.enums.FunnelStage;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorStagesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.List;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class StageDAO extends BaseValueDAO<EntityProcessorStagesRecord, Stage> {

    protected StageDAO() {
        super(Stage.class, ENTITY_PROCESSOR_STAGES, ENTITY_PROCESSOR_STAGES.ID);
    }

    public Mono<Boolean> existsByOrder(
            String appCode, String clientCode, ULong productTemplateId, Integer order, ULong excludeId) {

        List<Condition> conditions = this.getBaseCommonConditions(appCode, clientCode, null, productTemplateId, true);
        conditions.add(this.orderField.eq(order));

        if (excludeId != null) conditions.add(this.idField.ne(excludeId));

        return Mono.from(this.dslContext.selectOne().from(this.table).where(DSL.and(conditions)))
                .map(rec -> Boolean.TRUE)
                .defaultIfEmpty(Boolean.FALSE);
    }

    /**
     * Returns all active stages for a product template that have a non-null
     * funnelStage tag. Used by ConversionActionMappingService.seedDefaults to walk
     * tagged stages and create platform-binding rows for each.
     */
    public Flux<Stage> findTaggedForProductTemplate(ProcessorAccess access, ULong productTemplateId) {
        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        .where(ENTITY_PROCESSOR_STAGES
                                .APP_CODE
                                .eq(access.getAppCode())
                                .and(ENTITY_PROCESSOR_STAGES.CLIENT_CODE.eq(access.getClientCode()))
                                .and(ENTITY_PROCESSOR_STAGES.PRODUCT_TEMPLATE_ID.eq(productTemplateId))
                                .and(ENTITY_PROCESSOR_STAGES.FUNNEL_STAGE.isNotNull())
                                .and(ENTITY_PROCESSOR_STAGES.IS_ACTIVE.isTrue())))
                .map(e -> e.into(this.pojoClass));
    }

    /**
     * Targeted single-column update of a stage's {@code FUNNEL_STAGE} tag, scoped
     * to the tenant. Used by the funnel-mapping flow to label a deal-stage as
     * LEAD/MQL/SQL/etc. Returns rows affected (0 if the id is not in this tenant).
     */
    public Mono<Integer> setFunnelStage(ProcessorAccess access, ULong stageId, FunnelStage funnelStage) {
        return Mono.from(this.dslContext
                        .update(this.table)
                        .set(ENTITY_PROCESSOR_STAGES.FUNNEL_STAGE, funnelStage)
                        .where(ENTITY_PROCESSOR_STAGES
                                .ID
                                .eq(stageId)
                                .and(ENTITY_PROCESSOR_STAGES.APP_CODE.eq(access.getAppCode()))
                                .and(ENTITY_PROCESSOR_STAGES.CLIENT_CODE.eq(access.getClientCode()))))
                .map(Number::intValue)
                .defaultIfEmpty(0);
    }
}
