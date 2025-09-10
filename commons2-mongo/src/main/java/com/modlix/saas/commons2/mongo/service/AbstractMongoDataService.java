package com.modlix.saas.commons2.mongo.service;

import static com.modlix.saas.commons2.model.condition.FilterConditionOperator.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.CrudRepository;

import com.modlix.saas.commons2.model.condition.AbstractCondition;
import com.modlix.saas.commons2.model.condition.ComplexCondition;
import com.modlix.saas.commons2.model.condition.ComplexConditionOperator;
import com.modlix.saas.commons2.model.condition.FilterCondition;
import com.modlix.saas.commons2.model.dto.AbstractDTO;

public abstract class AbstractMongoDataService<I extends Serializable, D extends AbstractDTO<I, I>, R extends CrudRepository<D, I>> {

    @Autowired // NOSONAR
    protected R repo;

    @Autowired // NOSONAR
    protected MongoTemplate mongoTemplate;

    protected Class<D> pojoClass;

    protected AbstractMongoDataService(Class<D> pojoClass) {
        this.pojoClass = pojoClass;
    }

    public D create(D entity) {
        entity.setCreatedBy(null);
        entity.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));

        I userId = getLoggedInUserId();
        if (userId != null) {
            entity.setCreatedBy(userId);
        }

        return this.repo.save(entity);
    }

    protected I getLoggedInUserId() {
        return null;
    }

    public D read(I id) {
        return this.repo.findById(id).orElse(null);
    }

    public Page<D> readPageFilter(Pageable pageable, AbstractCondition condition) {
        Criteria crit = filter(condition);

        Query dataQuery = new Query(crit)
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .with(pageable.getSort());

        List<D> list = this.mongoTemplate.find(dataQuery, this.pojoClass);

        Query countQuery = new Query(crit).with(pageable.getSort());
        long count = this.mongoTemplate.count(countQuery, this.pojoClass);

        return new PageImpl<>(list, pageable, count);
    }

    public List<D> readAllFilter(AbstractCondition condition) {
        Criteria crit = filter(condition);
        return this.mongoTemplate.find(new Query(crit), this.pojoClass);
    }

    protected Criteria filter(AbstractCondition condition) {
        if (condition == null)
            return new Criteria();

        Criteria cond;
        if (condition instanceof ComplexCondition cc)
            cond = complexConditionFilter(cc);
        else
            cond = filterConditionFilter((FilterCondition) condition);

        return cond != null ? cond : new Criteria();
    }

    private Criteria filterConditionFilter(FilterCondition fc) { // NOSONAR
        // in order to cover all operators this kind of check is essential

        if (fc == null || fc.getField() == null)
            return null;

        Criteria crit = Criteria.where(fc.getField());

        if (fc.getOperator() == IS_FALSE || fc.getOperator() == IS_TRUE)
            return fc.isNegate() ? crit.is(false) : crit.is(true);

        if (fc.getOperator() == IS_NULL)
            return fc.isNegate() ? crit.ne(null) : crit.orOperator(crit.isNull(), crit.exists(false));

        if (fc.getOperator() == IN) {
            if (fc.getValue() == null && (fc.getMultiValue() == null || fc.getMultiValue().isEmpty()))
                return null;

            return fc.isNegate() ? crit.nin(this.multiFieldValue(fc.getValue(), fc.getMultiValue()))
                    : crit.in(this.multiFieldValue(fc.getValue(), fc.getMultiValue()));
        }

        if (fc.getValue() == null)
            return null;

        if (fc.getOperator() == BETWEEN) {
            var first = fc.isNegate() ? crit.lt(fc.getValue()) : crit.gte(fc.getValue());
            var second = fc.isNegate() ? crit.gt(fc.getToValue()) : crit.lte(fc.getToValue());

            if (fc.isNegate())
                return crit.andOperator(first, second);
            else
                return crit.orOperator(first, second);
        }

        return switch (fc.getOperator()) {
            case EQUALS -> fc.isNegate() ? crit.ne(fc.getValue()) : crit.is(fc.getValue());
            case GREATER_THAN -> fc.isNegate() ? crit.lte(fc.getValue()) : crit.gt(fc.getValue());
            case GREATER_THAN_EQUAL -> fc.isNegate() ? crit.lt(fc.getValue()) : crit.gte(fc.getValue());
            case LESS_THAN -> fc.isNegate() ? crit.gte(fc.getValue()) : crit.lt(fc.getValue());
            case LESS_THAN_EQUAL -> fc.isNegate() ? crit.gt(fc.getValue()) : crit.lte(fc.getValue());
            case LIKE -> fc.isNegate() ? crit.not().regex(fc.getValue().toString())
                    : crit.regex(fc.getValue().toString(), "");
            case STRING_LOOSE_EQUAL -> fc.isNegate() ? crit.not().regex(fc.getValue().toString())
                    : crit.regex(fc.getValue().toString());
            default -> null;
        };
    }

    private List<?> multiFieldValue(Object objValue, List<?> values) {
        if (values != null && !values.isEmpty())
            return values;

        if (objValue == null)
            return List.of();

        int from = 0;
        String iValue = objValue.toString().trim();

        List<Object> obj = new ArrayList<>();
        for (int i = 0; i < iValue.length(); i++) { // NOSONAR
            // Having multiple continue statements is not confusing

            if (iValue.charAt(i) != ',')
                continue;

            if (i != 0 && iValue.charAt(i - 1) == '\\')
                continue;

            String str = iValue.substring(from, i).trim();
            if (str.isEmpty())
                continue;

            obj.add(str);
            from = i + 1;
        }
        String str = iValue.substring(from);
        if (str != null && !str.trim().isEmpty())
            obj.add(str);

        return obj;
    }

    private Criteria complexConditionFilter(ComplexCondition cc) {
        if (cc.getConditions() == null || cc.getConditions().isEmpty())
            return null;

        List<Criteria> conds = new ArrayList<>();
        for (AbstractCondition condition : cc.getConditions()) {
            Criteria cond = filter(condition);
            if (cond != null) {
                conds.add(cond);
            }
        }

        if (conds.isEmpty())
            return null;

        if (cc.getOperator() == ComplexConditionOperator.AND)
            return cc.isNegate() ? new Criteria().orOperator(conds) : new Criteria().andOperator(conds);

        return cc.isNegate() ? new Criteria().andOperator(conds) : new Criteria().orOperator(conds);
    }

    public boolean delete(I id) {
        this.repo.deleteById(id);
        return true;
    }
}
