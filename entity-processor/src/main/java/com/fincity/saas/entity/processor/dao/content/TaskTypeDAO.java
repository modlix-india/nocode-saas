package com.fincity.saas.entity.processor.dao.content;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_TASK_TYPES;

import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.content.TaskType;
import com.fincity.saas.entity.processor.enums.content.ContentEntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTaskTypesRecord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TaskTypeDAO extends BaseUpdatableDAO<EntityProcessorTaskTypesRecord, TaskType> {

    protected TaskTypeDAO() {
        super(TaskType.class, ENTITY_PROCESSOR_TASK_TYPES, ENTITY_PROCESSOR_TASK_TYPES.ID);
    }

    public Mono<Boolean> existsByNameAndContentEntitySeries(
            String appCode, String clientCode, ULong id, String name, ContentEntitySeries contentEntitySeries) {

        if (StringUtil.safeIsBlank(name) || contentEntitySeries == null) return Mono.just(Boolean.FALSE);

        List<Condition> baseConditions = new ArrayList<>();
        baseConditions.add(super.appCodeField.eq(appCode));
        baseConditions.add(super.clientCodeField.eq(clientCode));
        baseConditions.add(super.nameField.eq(name));
        baseConditions.add(ENTITY_PROCESSOR_TASK_TYPES.CONTENT_ENTITY_SERIES.eq(contentEntitySeries));

        if (id != null) baseConditions.add(this.idField.ne(id));

        return Mono.from(this.dslContext.selectOne().from(this.table).where(DSL.and(baseConditions)))
                .map(rec -> Boolean.TRUE)
                .defaultIfEmpty(Boolean.FALSE);
    }
}
