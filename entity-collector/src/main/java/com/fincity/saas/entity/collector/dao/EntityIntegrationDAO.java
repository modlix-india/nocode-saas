package com.fincity.saas.entity.collector.dao;

import static com.fincity.saas.entity.collector.jooq.tables.EntityIntegrations.ENTITY_INTEGRATIONS;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsInSourceType;
import com.fincity.saas.entity.collector.jooq.tables.records.EntityIntegrationsRecord;
import org.jooq.Condition;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class EntityIntegrationDAO extends AbstractUpdatableDAO<EntityIntegrationsRecord, ULong, EntityIntegration> {

    protected EntityIntegrationDAO() {
        super(EntityIntegration.class, ENTITY_INTEGRATIONS, ENTITY_INTEGRATIONS.ID);
    }

    public Mono<EntityIntegration> findByInSourceAndInSourceType(
            String inSource, EntityIntegrationsInSourceType inSourceType) {

        Condition condition =
                ENTITY_INTEGRATIONS.IN_SOURCE.eq(inSource).and(ENTITY_INTEGRATIONS.IN_SOURCE_TYPE.eq(inSourceType));

        return Mono.from(this.dslContext
                        .selectFrom(ENTITY_INTEGRATIONS)
                        .where(condition)
                        .limit(1))
                .map(record -> record.into(this.pojoClass));
    }
}
