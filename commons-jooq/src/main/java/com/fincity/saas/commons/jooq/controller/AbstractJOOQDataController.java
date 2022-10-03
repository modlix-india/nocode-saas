package com.fincity.saas.commons.jooq.controller;

import java.beans.PropertyEditorSupport;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

import org.jooq.UpdatableRecord;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;

import reactor.core.publisher.Mono;

public class AbstractJOOQDataController<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractDTO<I, I>, O extends AbstractDAO<R, I, D>, S extends AbstractJOOQDataService<R, I, D, O>> {

	public static final String PATH_VARIABLE_ID = "id";
	public static final String PATH_ID = "/{" + PATH_VARIABLE_ID + "}";
	public static final String PATH_QUERY = "query";

	@Autowired
	protected S service;
	
	@InitBinder
	public void initBinder(DataBinder binder){
		binder.registerCustomEditor(ULong.class, new PropertyEditorSupport() {
			@Override
			public void setAsText(String text) {
				if (text == null)
					setValue(null);
				setValue(ULong.valueOf(text));
			}
		});
		binder.registerCustomEditor(UInteger.class, new PropertyEditorSupport() {
			@Override
			public void setAsText(String text) {
				if (text == null)
					setValue(null);
				setValue(UInteger.valueOf(text));
			}
		});
		binder.registerCustomEditor(UShort.class, new PropertyEditorSupport() {
			@Override
			public void setAsText(String text) {
				if (text == null)
					setValue(null);
				setValue(UShort.valueOf(text));
			}
		});
    }


	@PostMapping
	public Mono<ResponseEntity<D>> create(@RequestBody D entity) {
		return this.service.create(entity).map(ResponseEntity::ok);
	}

	@GetMapping(PATH_ID)
	public Mono<ResponseEntity<D>> read(@PathVariable(PATH_VARIABLE_ID) final I id, ServerHttpRequest request) {
		return this.service.read(id).map(ResponseEntity::ok);
	}

	@GetMapping()
	public Mono<ResponseEntity<Page<D>>> readPageFilter(Pageable pageable, ServerHttpRequest request) {
		pageable = (pageable == null ? PageRequest.of(0, 10, Direction.ASC, PATH_VARIABLE_ID) : pageable);
		return this.service.readPageFilter(pageable, this.parameterMapToMap(request.getQueryParams()))
				.map(ResponseEntity::ok);
	}

	protected AbstractCondition parameterMapToMap(MultiValueMap<String, String> multiValueMap) {

		List<AbstractCondition> conditions = multiValueMap.entrySet().stream().map(e -> {
			List<String> value = e.getValue();
			if (value == null || value.isEmpty())
				return new FilterCondition().setField(e.getKey()).setOperator(FilterConditionOperator.EQUALS)
						.setValue("");

			if (value.size() == 1)
				return new FilterCondition().setField(e.getKey()).setOperator(FilterConditionOperator.EQUALS)
						.setValue(value.get(0));

			return new FilterCondition().setField(e.getKey()).setOperator(FilterConditionOperator.IN)
					.setValue(value.stream().map(v -> v.replace(",", "\\,")).collect(Collectors.joining(",")));
		}).map(AbstractCondition.class::cast).toList();

		if (conditions.isEmpty())
			return null;

		if (conditions.size() == 1)
			return conditions.get(0);

		return new ComplexCondition().setConditions(conditions).setOperator(ComplexConditionOperator.AND);
	}

	@PostMapping(PATH_QUERY)
	public Mono<ResponseEntity<Page<D>>> readPageFilter(@RequestBody Query query) {

		Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), query.getSort());

		return this.service.readPageFilter(pageable, query.getCondition()).map(ResponseEntity::ok);
	}

	@DeleteMapping(PATH_ID)
	@ResponseStatus(value = HttpStatus.NO_CONTENT)
	public void delete(@PathVariable(PATH_VARIABLE_ID) final I id) {
		this.service.delete(id).subscribe();
	}
}
