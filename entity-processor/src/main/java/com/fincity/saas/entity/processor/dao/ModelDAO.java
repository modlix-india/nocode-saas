package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorModels.ENTITY_PROCESSOR_MODELS;

import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.Model;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorModelsRecord;
import java.util.ArrayList;
import java.util.List;
import org.jooq.Condition;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ModelDAO extends BaseProcessorDAO<EntityProcessorModelsRecord, Model> {

    protected ModelDAO() {
        super(Model.class, ENTITY_PROCESSOR_MODELS, ENTITY_PROCESSOR_MODELS.ID);
    }

    public Mono<Model> readByNumberAndEmail(
            String appCode, String clientCode, Integer dialCode, String number, String email) {
        return Mono.from(this.dslContext
                        .selectFrom(ENTITY_PROCESSOR_MODELS)
                        .where(getModelIdentifierConditions(appCode, clientCode, dialCode, number, email))
                        .orderBy(ENTITY_PROCESSOR_MODELS.ID.desc())
                        .limit(1))
                .map(e -> e.into(Model.class));
    }

    private List<Condition> getModelIdentifierConditions(
            String appCode, String clientCode, Integer dialCode, String number, String email) {

        List<Condition> conditions = new ArrayList<>();

        conditions.add(this.appCodeField.eq(appCode));
        conditions.add(this.clientCodeField.eq(clientCode));

        List<Condition> phoneEmailConditions = new ArrayList<>();

        if (number != null)
            phoneEmailConditions.add(ENTITY_PROCESSOR_MODELS
                    .DIAL_CODE
                    .eq(dialCode.shortValue())
                    .and(ENTITY_PROCESSOR_MODELS.PHONE_NUMBER.eq(number)));

        if (email != null) phoneEmailConditions.add(ENTITY_PROCESSOR_MODELS.EMAIL.eq(email));

        if (!phoneEmailConditions.isEmpty())
            conditions.add(
                    phoneEmailConditions.size() > 1
                            ? phoneEmailConditions.get(0).or(phoneEmailConditions.get(1))
                            : phoneEmailConditions.getFirst());

        // we always need Active product entities
        conditions.add(this.isActiveTrue());

        return conditions;
    }
}
