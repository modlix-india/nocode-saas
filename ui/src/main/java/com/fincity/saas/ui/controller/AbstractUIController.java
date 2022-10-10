package com.fincity.saas.ui.controller;

import java.util.LinkedList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.mongo.controller.AbstractMongoUpdatableDataController;
import com.fincity.saas.ui.document.AbstractUIDTO;
import com.fincity.saas.ui.document.ListResultObject;
import com.fincity.saas.ui.repository.IUIRepository;
import com.fincity.saas.ui.service.AbstractUIServcie;
import com.fincity.saas.ui.service.UIMessageResourceService;

import reactor.core.publisher.Mono;

public class AbstractUIController<D extends AbstractUIDTO<D>, R extends IUIRepository<D>, S extends AbstractUIServcie<D, R>>
        extends AbstractMongoUpdatableDataController<String, D, R, S> {

	private static final String APPLICATION_NAME = "applicationName";

	@Autowired
	private UIMessageResourceService messageService;

	@Override
	@GetMapping("/nomap2")
	public Mono<ResponseEntity<Page<D>>> readPageFilter(Pageable pageable, ServerHttpRequest request) {
		return Mono.just(ResponseEntity.badRequest()
		        .build());
	}

	@Override
	@PostMapping("/nomap2")
	public Mono<ResponseEntity<Page<D>>> readPageFilter(Query query) {
		return Mono.just(ResponseEntity.badRequest()
		        .build());
	}

	@GetMapping()
	public Mono<ResponseEntity<Page<ListResultObject>>> readPageFilterLRO(Pageable pageable,
	        ServerHttpRequest request) {
		final Pageable finPageable = (pageable == null ? PageRequest.of(0, 10, Direction.ASC, PATH_VARIABLE_ID)
		        : pageable);
		return this.checkForApplicationName(request.getQueryParams())
		        .flatMap(params -> this.service.readPageFilterLRO(finPageable, this.parameterMapToMap(params))
		                .map(ResponseEntity::ok));
	}

	protected Mono<MultiValueMap<String, String>> checkForApplicationName(MultiValueMap<String, String> params) {

		if (params.get(APPLICATION_NAME) == null || params.get(APPLICATION_NAME)
		        .isEmpty()) {
			return this.messageService.throwMessage(HttpStatus.BAD_REQUEST,
			        UIMessageResourceService.APPLICATION_NAME_REQUIRED);
		}

		return Mono.just(params);
	}

	@PostMapping(PATH_QUERY)
	public Mono<ResponseEntity<Page<ListResultObject>>> readPageFilterLRO(@RequestBody Query query) {

		Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), query.getSort());
		return this.checkForApplicationName(query)
		        .flatMap(q -> this.service.readPageFilterLRO(pageable, q.getCondition())
		                .map(ResponseEntity::ok));
	}

	protected Mono<Query> checkForApplicationName(Query query) {

		AbstractCondition cond = query.getCondition();

		if (cond instanceof FilterCondition fc) {
			if (fc.getField()
			        .equals(APPLICATION_NAME))
				return Mono.just(query);

			return this.messageService.throwMessage(HttpStatus.BAD_REQUEST,
			        UIMessageResourceService.APPLICATION_NAME_REQUIRED);
		} else if (cond instanceof ComplexCondition cc) {

			return checkForApplicationNameInComplexCondition(query, cc);

		}
		
		return this.messageService.throwMessage(HttpStatus.BAD_REQUEST,
		        UIMessageResourceService.APPLICATION_NAME_REQUIRED);

	}

	private Mono<Query> checkForApplicationNameInComplexCondition(Query query, ComplexCondition cc) {
		
		LinkedList<ComplexCondition> list = new LinkedList<>();
		list.add(cc);

		while (!list.isEmpty()) {

			ComplexCondition inCc = list.removeFirst();

			for (AbstractCondition ac : inCc.getConditions()) {
				if (ac instanceof FilterCondition fc) {
					if (fc.getField()
					        .equals(APPLICATION_NAME))
						return Mono.just(query);

				} else {
					list.addLast((ComplexCondition) ac);
				}
			}
		}

		return this.messageService.throwMessage(HttpStatus.BAD_REQUEST,
		        UIMessageResourceService.APPLICATION_NAME_REQUIRED);
	}
}
