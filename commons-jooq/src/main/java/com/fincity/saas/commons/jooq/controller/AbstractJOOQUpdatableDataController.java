package com.fincity.saas.commons.jooq.controller;

import java.io.Serializable;
import java.util.Map;

import org.jooq.UpdatableRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import reactor.core.publisher.Mono;

public class AbstractJOOQUpdatableDataController<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractUpdatableDTO<I, I>, O extends AbstractUpdatableDAO<R, I, D>, S extends AbstractJOOQUpdatableDataService<R, I, D, O>>
        extends AbstractJOOQDataController<R, I, D, O, S> {

	@PutMapping(AbstractJOOQDataController.PATH_ID)
	public Mono<ResponseEntity<D>> put(@PathVariable(name = PATH_VARIABLE_ID, required = false) final I id,
	        @RequestBody D entity) {
		if (id != null)
			entity.setId(id);
		return this.service.update(entity)
		        .map(ResponseEntity::ok);
	}

	@PatchMapping(AbstractJOOQDataController.PATH_ID)
	public Mono<ResponseEntity<D>> patch(@PathVariable(name = PATH_VARIABLE_ID) final I id,
	        @RequestBody Map<String, Object> entityMap) {
		return this.service.update(id, entityMap)
		        .map(ResponseEntity::ok);
	}
}
