package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_OWNERS;

import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.Owner;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorOwnersRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.ArrayList;
import java.util.List;
import org.jooq.Condition;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class OwnerDAO extends BaseProcessorDAO<EntityProcessorOwnersRecord, Owner> {

    protected OwnerDAO() {
        super(Owner.class, ENTITY_PROCESSOR_OWNERS, ENTITY_PROCESSOR_OWNERS.ID);
    }

    public Mono<Owner> readByNumberAndEmail(ProcessorAccess access, Integer dialCode, String number, String email) {
        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(this.getOwnerIdentifierConditions(access, dialCode, number, email))
                        .orderBy(this.idField.desc())
                        .limit(1))
                .map(e -> e.into(this.pojoClass));
    }

    private List<Condition> getOwnerIdentifierConditions(
            ProcessorAccess access, Integer dialCode, String number, String email) {

        List<Condition> conditions = new ArrayList<>();

        conditions.add(this.appCodeField.eq(access.getAppCode()));
        conditions.add(this.clientCodeField.eq(access.getEffectiveClientCode()));

        List<Condition> phoneEmailConditions = new ArrayList<>();

        if (number != null && !number.isEmpty())
            phoneEmailConditions.add(ENTITY_PROCESSOR_OWNERS
                    .DIAL_CODE
                    .eq(dialCode.shortValue())
                    .and(ENTITY_PROCESSOR_OWNERS.PHONE_NUMBER.eq(number)));

        if (email != null && !email.isEmpty()) phoneEmailConditions.add(ENTITY_PROCESSOR_OWNERS.EMAIL.eq(email));

        if (!phoneEmailConditions.isEmpty())
            conditions.add(
                    phoneEmailConditions.size() > 1
                            ? phoneEmailConditions.get(0).or(phoneEmailConditions.get(1))
                            : phoneEmailConditions.getFirst());

        // we always need Active product entities
        conditions.add(super.isActiveTrue());

        return conditions;
    }
}
