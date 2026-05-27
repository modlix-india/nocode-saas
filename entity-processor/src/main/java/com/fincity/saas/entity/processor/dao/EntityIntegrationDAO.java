package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorIntegrations.ENTITY_PROCESSOR_INTEGRATIONS;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.entity.processor.dto.EntityIntegration;
import com.fincity.saas.entity.processor.jooq.enums.EntityProcessorIntegrationsInSourceType;
import com.fincity.saas.entity.processor.jooq.enums.EntityProcessorIntegrationsStatus;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorIntegrationsRecord;
import org.jooq.Condition;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class EntityIntegrationDAO
        extends AbstractUpdatableDAO<EntityProcessorIntegrationsRecord, ULong, EntityIntegration> {

    protected EntityIntegrationDAO() {
        super(EntityIntegration.class, ENTITY_PROCESSOR_INTEGRATIONS, ENTITY_PROCESSOR_INTEGRATIONS.ID);
    }

    public Mono<EntityIntegration> findByInSourceAndInSourceType(
            String inSource, EntityProcessorIntegrationsInSourceType inSourceType) {

        Condition condition = ENTITY_PROCESSOR_INTEGRATIONS.IN_SOURCE.eq(inSource)
                .and(ENTITY_PROCESSOR_INTEGRATIONS.IN_SOURCE_TYPE.eq(inSourceType))
                .and(ENTITY_PROCESSOR_INTEGRATIONS.STATUS.eq(EntityProcessorIntegrationsStatus.ACTIVE));

        return Mono.from(this.dslContext
                        .selectFrom(ENTITY_PROCESSOR_INTEGRATIONS)
                        .where(condition)
                        .limit(1))
                .map(r -> r.into(EntityIntegration.class));
    }

    /**
     * Per-tenant verify-token lookup for the Meta webhook handshake. Meta sends
     * the {@code hub.verify_token} value the admin configured in their Developer
     * Console — we accept the challenge iff any active integration row has that
     * value in either {@code PRIMARY_VERIFY_TOKEN} or {@code SECONDARY_VERIFY_TOKEN}.
     */
    public Mono<EntityIntegration> findActiveByVerifyToken(
            String verifyToken, EntityProcessorIntegrationsInSourceType inSourceType) {

        Condition condition = ENTITY_PROCESSOR_INTEGRATIONS
                .PRIMARY_VERIFY_TOKEN
                .eq(verifyToken)
                .or(ENTITY_PROCESSOR_INTEGRATIONS.SECONDARY_VERIFY_TOKEN.eq(verifyToken))
                .and(ENTITY_PROCESSOR_INTEGRATIONS.IN_SOURCE_TYPE.eq(inSourceType))
                .and(ENTITY_PROCESSOR_INTEGRATIONS.STATUS.eq(EntityProcessorIntegrationsStatus.ACTIVE));

        return Mono.from(this.dslContext
                        .selectFrom(ENTITY_PROCESSOR_INTEGRATIONS)
                        .where(condition)
                        .limit(1))
                .map(r -> r.into(EntityIntegration.class));
    }
}
