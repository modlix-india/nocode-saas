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

    /**
     * Check if a stage with the same combination of app code, client code, product template ID, and order already exists.
     * Platform is ignored to ensure continuous ordering across all platforms.
     *
     * @param appCode the app code
     * @param clientCode the client code
     * @param productTemplateId the product template ID
     * @param platform the platform (ignored for order uniqueness check)
     * @param order the order to check
     * @param excludeId the ID to exclude from the check (can be null)
     * @return true if a stage with the same combination exists, false otherwise
     */
    public Mono<Boolean> existsByOrder(
            String appCode, String clientCode, ULong productTemplateId, Integer order, ULong excludeId) {

        // Use null for platform to ignore it in the conditions
        List<Condition> conditions = this.getBaseCommonConditions(appCode, clientCode, null, productTemplateId, true);
        conditions.add(this.orderField.eq(order));

        // Exclude the current stage if an ID is provided
        if (excludeId != null) {
            conditions.add(this.idField.ne(excludeId));
        }

        return Mono.from(this.dslContext.selectOne().from(this.table).where(DSL.and(conditions)))
                .map(rec -> Boolean.TRUE)
                .defaultIfEmpty(Boolean.FALSE);
    }
}
