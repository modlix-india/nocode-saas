package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages.ENTITY_PROCESSOR_STAGES;

import com.fincity.saas.entity.processor.dao.base.BaseValueDAO;
import com.fincity.saas.entity.processor.dto.Stage;
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

    public Mono<Boolean> existsByOrder(
            String appCode, String clientCode, ULong productTemplateId, Integer order, ULong excludeId) {

        List<Condition> conditions = this.getBaseCommonConditions(appCode, clientCode, null, productTemplateId, true);
        conditions.add(this.orderField.eq(order));

        if (excludeId != null) conditions.add(this.idField.ne(excludeId));

        return Mono.from(this.dslContext.selectOne().from(this.table).where(DSL.and(conditions)))
                .map(rec -> Boolean.TRUE)
                .defaultIfEmpty(Boolean.FALSE);
    }
}
