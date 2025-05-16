package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages.ENTITY_PROCESSOR_STAGES;

import com.fincity.saas.entity.processor.dao.base.BaseValueDAO;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorStagesRecord;
import java.util.List;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class StageDAO extends BaseValueDAO<EntityProcessorStagesRecord, Stage> {

    protected StageDAO() {
        super(Stage.class, ENTITY_PROCESSOR_STAGES, ENTITY_PROCESSOR_STAGES.ID);
    }

    public Mono<Stage> getLatestStageByOrder(
            String appCode, String clientCode, ULong valueTemplateId, Platform platform, Boolean isParent) {

        return Mono.from(this.dslContext
                        .selectFrom(ENTITY_PROCESSOR_STAGES)
                        .where(DSL.and(getStageConditions(appCode, clientCode, valueTemplateId, platform, isParent)))
                        .and(ENTITY_PROCESSOR_STAGES.IS_ACTIVE.eq((byte) 1))
                        .orderBy(ENTITY_PROCESSOR_STAGES.ORDER.desc())
                        .limit(1))
                .map(e -> e.into(this.pojoClass))
                .switchIfEmpty(Mono.empty());
    }

    public Mono<Stage> getFirstStage(
            String appCode, String clientCode, ULong valueTemplateId, Platform platform, Boolean isParent) {

        return Mono.from(this.dslContext
                        .selectFrom(ENTITY_PROCESSOR_STAGES)
                        .where(DSL.and(getStageConditions(appCode, clientCode, valueTemplateId, platform, isParent)))
                        .orderBy(ENTITY_PROCESSOR_STAGES.ORDER.asc())
                        .limit(1))
                .map(e -> e.into(this.pojoClass))
                .switchIfEmpty(Mono.empty());
    }

    protected List<Condition> getStageConditions(
            String appCode, String clientCode, ULong valueTemplateId, Platform platform, Boolean isParent) {
        return getBaseCommonConditions(appCode, clientCode, platform, valueTemplateId, isParent);
    }
}
