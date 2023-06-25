package com.fincity.saas.commons.mongo.service;

import static com.fincity.saas.commons.model.condition.FilterConditionOperator.BETWEEN;
import static com.fincity.saas.commons.model.condition.FilterConditionOperator.IN;
import static com.fincity.saas.commons.model.condition.FilterConditionOperator.IS_FALSE;
import static com.fincity.saas.commons.model.condition.FilterConditionOperator.IS_NULL;
import static com.fincity.saas.commons.model.condition.FilterConditionOperator.IS_TRUE;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.fincity.nocode.kirun.engine.util.string.StringUtil;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.util.LogUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class AbstractMongoDataService<I extends Serializable, D extends AbstractDTO<I, I>, R extends ReactiveCrudRepository<D, I>> {

	@Autowired
	protected R repo;

	@Autowired
	protected ReactiveMongoTemplate mongoTemplate;

	protected Class<D> pojoClass;

	protected AbstractMongoDataService(Class<D> pojoClass) {

		this.pojoClass = pojoClass;
	}

	public Mono<D> create(D entity) {

		entity.setCreatedBy(null);
		entity.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
		return this.getLoggedInUserId()
		        .map(e ->
				{
			        entity.setCreatedBy(e);
			        return entity;
		        })
		        .defaultIfEmpty(entity)
		        .flatMap(e -> this.repo.save(e));

	}

	protected Mono<I> getLoggedInUserId() {
		return Mono.empty();
	}

	public Mono<D> read(I id) {
		return this.repo.findById(id);
	}

	public Mono<Page<D>> readPageFilter(Pageable pageable, AbstractCondition condition) {

		return FlatMapUtil.flatMapMono(

		        () -> this.filter(condition),

		        crit -> Mono.just((new Query(crit)).skip(pageable.getOffset())
		                .limit(pageable.getPageSize())
		                .with(pageable.getSort())),

		        (crit, dataQuery) -> this.mongoTemplate.find(dataQuery, this.pojoClass)
		                .collectList(),

		        (crit, dataQuery, list) -> Mono.just((new Query(crit)).with(pageable.getSort())),

		        (crit, dataQuery, list, countQuery) -> this.mongoTemplate.count(countQuery, this.pojoClass),

		        (crit, dataQuery, list, countQuery, count) -> Mono
		                .just((Page<D>) new PageImpl<>(list, pageable, count)))
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractMongoDataService.readPageFilter"));
	}

	public Flux<D> readAllFilter(AbstractCondition condition) {
		return this.filter(condition)
		        .flatMapMany(crit -> this.mongoTemplate.find(new Query(crit), this.pojoClass));
	}

	protected Mono<Criteria> filter(AbstractCondition condition) {

		if (condition == null)
			return Mono.just(new Criteria());

		Mono<Criteria> cond = null;
		if (condition instanceof ComplexCondition cc)
			cond = complexConditionFilter(cc);
		else
			cond = filterConditionFilter((FilterCondition) condition);

		return cond.defaultIfEmpty(new Criteria());
	}

	private Mono<Criteria> filterConditionFilter(FilterCondition fc) {// NOSONAR
		// in order to cover all operators this kind of check is essential

		if (fc == null || fc.getField() == null)
			return Mono.empty();

		Criteria crit = Criteria.where(fc.getField());

		if (fc.getOperator() == IS_FALSE || fc.getOperator() == IS_TRUE)

			return Mono.just(fc.isNegate() ? crit.is(false) : crit.is(true));

		if (fc.getOperator() == IS_NULL)

			return Mono.just(fc.isNegate() ? crit.ne(null) : crit.orOperator(crit.isNull(), crit.exists(false)));

		if (fc.getOperator() == IN) {

			if (fc.getValue() == null && (fc.getMultiValue() == null || fc.getMultiValue()
			        .isEmpty()))
				return Mono.empty();

			return Mono.just(fc.isNegate() ? crit.nin(this.multiFieldValue(fc.getValue(), fc.getMultiValue()))
			        : crit.in(this.multiFieldValue(fc.getValue(), fc.getMultiValue())));

		}

		if (fc.getValue() == null)
			return Mono.empty();

		if (fc.getOperator() == BETWEEN) {

			var first = fc.isNegate() ? crit.lt(fc.getValue()) : crit.gte(fc.getValue());

			var second = fc.isNegate() ? crit.gt(fc.getToValue()) : crit.lte(fc.getToValue());

			if (fc.isNegate())
				return Mono.just(crit.andOperator(first, second));
			else
				return Mono.just(crit.orOperator(first, second));
		}

		switch (fc.getOperator()) {

		case EQUALS:
			return Mono.just(fc.isNegate() ? crit.ne(fc.getValue()) : crit.is(fc.getValue()));

		case GREATER_THAN:
			return Mono.just(fc.isNegate() ? crit.lte(fc.getValue()) : crit.gt(fc.getValue()));

		case GREATER_THAN_EQUAL:
			return Mono.just(fc.isNegate() ? crit.lt(fc.getValue()) : crit.gte(fc.getValue()));

		case LESS_THAN:
			return Mono.just(fc.isNegate() ? crit.gte(fc.getValue()) : crit.lt(fc.getValue()));

		case LESS_THAN_EQUAL:
			return Mono.just(fc.isNegate() ? crit.gt(fc.getValue()) : crit.lte(fc.getValue()));

		case LIKE:
			return Mono.just(fc.isNegate() ? crit.not()
			        .regex(fc.getValue()
			                .toString())
			        : crit.regex(fc.getValue()
			                .toString(), ""));

		case STRING_LOOSE_EQUAL:

			return Mono.just(fc.isNegate() ? crit.not()
			        .regex(fc.getValue()
			                .toString())
			        : crit.regex(fc.getValue()
			                .toString()));

		default:
			return Mono.empty();
		}

	}

	private List<Object> multiFieldValue(Object objValue, List<Object> values) {

		if (values != null && !values.isEmpty())
			return values;

		if (objValue == null)
			return List.of();

		int from = 0;
		String iValue = objValue.toString()
		        .trim();

		List<Object> obj = new ArrayList<>();
		for (int i = 0; i < iValue.length(); i++) { // NOSONAR
			// Having multiple continue statements is not confusing

			if (iValue.charAt(i) != ',')
				continue;

			if (i != 0 && iValue.charAt(i - 1) == '\\')
				continue;

			String str = iValue.substring(from, i)
			        .trim();
			if (str.isEmpty())
				continue;

			obj.add(str);
			from = i + 1;
		}
		String str = iValue.substring(from);
		if (!StringUtil.isNullOrBlank(str))
			obj.add(str);

		return obj;

	}

	private Mono<Criteria> complexConditionFilter(ComplexCondition cc) {

		if (cc.getConditions() == null || cc.getConditions()
		        .isEmpty())
			return Mono.empty();

		return Flux.concat(cc.getConditions()
		        .stream()
		        .map(this::filter)
		        .toList())
		        .collectList()
		        .map(conds ->
				{
			        if (cc.getOperator() == ComplexConditionOperator.AND)
				        return cc.isNegate() ? new Criteria().orOperator(conds) : new Criteria().andOperator(conds);

			        return cc.isNegate() ? new Criteria().andOperator(conds) : new Criteria().orOperator(conds);
		        });
	}

	public Mono<Boolean> delete(I id) {
		return this.repo.deleteById(id)
		        .thenReturn(true);
	}
}
