package com.fincity.saas.message.dao;

import static com.fincity.saas.message.jooq.Tables.MESSAGE_PROVIDER_IDENTIFIERS;

import com.fincity.saas.message.dao.base.BaseUpdatableDAO;
import com.fincity.saas.message.dto.ProviderIdentifier;
import com.fincity.saas.message.jooq.tables.records.MessageProviderIdentifiersRecord;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import java.util.ArrayList;
import java.util.List;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ProviderIdentifierDAO extends BaseUpdatableDAO<MessageProviderIdentifiersRecord, ProviderIdentifier> {

    protected ProviderIdentifierDAO() {
        super(ProviderIdentifier.class, MESSAGE_PROVIDER_IDENTIFIERS, MESSAGE_PROVIDER_IDENTIFIERS.ID);
    }

    public Mono<ProviderIdentifier> findByIdentifier(
            String appCode,
            String clientCode,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType,
            String identifier) {
        List<Condition> conditions = getBaseConditions(appCode, clientCode, connectionType, connectionSubType);
        conditions.add(MESSAGE_PROVIDER_IDENTIFIERS.IDENTIFIER.eq(identifier));

        return Mono.from(
                        this.dslContext.selectFrom(MESSAGE_PROVIDER_IDENTIFIERS).where(DSL.and(conditions)))
                .map(e -> e.into(this.pojoClass));
    }

    public Mono<ProviderIdentifier> findDefaultIdentifier(
            String appCode, String clientCode, ConnectionType connectionType, ConnectionSubType connectionSubType) {
        List<Condition> conditions = getBaseConditions(appCode, clientCode, connectionType, connectionSubType);
        conditions.add(MESSAGE_PROVIDER_IDENTIFIERS.IS_DEFAULT.eq((byte) 1));

        return Mono.from(
                        this.dslContext.selectFrom(MESSAGE_PROVIDER_IDENTIFIERS).where(DSL.and(conditions)))
                .map(e -> e.into(this.pojoClass));
    }

    public Mono<Integer> clearExistingDefault(
            String appCode,
            String clientCode,
            ConnectionType connectionType,
            ConnectionSubType connectionSubType,
            ULong excludeId) {
        List<Condition> conditions = getBaseConditions(appCode, clientCode, connectionType, connectionSubType);
        conditions.add(MESSAGE_PROVIDER_IDENTIFIERS.IS_DEFAULT.eq((byte) 1));

        if (excludeId != null) conditions.add(MESSAGE_PROVIDER_IDENTIFIERS.ID.ne(excludeId));

        return Mono.from(this.dslContext
                .update(MESSAGE_PROVIDER_IDENTIFIERS)
                .set(MESSAGE_PROVIDER_IDENTIFIERS.IS_DEFAULT, (byte) 0)
                .where(DSL.and(conditions)));
    }

    private List<Condition> getBaseConditions(
            String appCode, String clientCode, ConnectionType connectionType, ConnectionSubType connectionSubType) {

        List<Condition> conditions = new ArrayList<>();

        conditions.add(MESSAGE_PROVIDER_IDENTIFIERS.APP_CODE.eq(appCode));
        conditions.add(MESSAGE_PROVIDER_IDENTIFIERS.CLIENT_CODE.eq(clientCode));
        conditions.add(MESSAGE_PROVIDER_IDENTIFIERS.CONNECTION_TYPE.eq(connectionType));
        conditions.add(MESSAGE_PROVIDER_IDENTIFIERS.CONNECTION_SUB_TYPE.eq(connectionSubType));
        conditions.add(this.isActiveTrue());

        return conditions;
    }
}
