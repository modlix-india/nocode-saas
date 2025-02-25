package com.fincity.saas.notification.controller;

import java.io.Serializable;

import org.jooq.UpdatableRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.notification.dao.AbstractCodeDao;
import com.fincity.saas.notification.service.AbstractCodeService;

import reactor.core.publisher.Mono;

public abstract class AbstractCodeController<R extends UpdatableRecord<R>, I extends Serializable,
		D extends AbstractUpdatableDTO<I, I>, O extends AbstractCodeDao<R, I, D>, S extends AbstractCodeService<R, I, D, O>>
		extends AbstractJOOQDataController<R, I, D, O, S> {

	public static final String PATH_VARIABLE_ID = "code";
	public static final String PATH_ID = "/code/{" + PATH_VARIABLE_ID + "}";

	@GetMapping(PATH_ID)
	public Mono<ResponseEntity<D>> getByCode(
			@PathVariable(PATH_VARIABLE_ID) final String code) {
		return this.service.getByCode(code).map(ResponseEntity::ok)
				.switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
	}
}
