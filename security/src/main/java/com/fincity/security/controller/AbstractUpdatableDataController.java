package com.fincity.security.controller;

import java.io.Serializable;
import java.util.Map;

import org.jooq.UpdatableRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.fincity.security.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.AbstractUpdatableDTO;
import com.fincity.security.service.AbstractUpdatableDataService;

import reactor.core.publisher.Mono;

public class AbstractUpdatableDataController<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractUpdatableDTO<I, I>, O extends AbstractUpdatableDAO<R, I, D>, S extends AbstractUpdatableDataService<R, I, D, O>>
        extends AbstractDataController<R, I, D, O, S> {

	@PutMapping(AbstractDataController.PATH_ID)
	public Mono<ResponseEntity<D>> put(@PathVariable(name = PATH_VARIABLE_ID, required = false) final I id,
	        @RequestBody D entity) {
		if (id != null)
			entity.setId(id);
		return this.service.update(entity)
		        .map(ResponseEntity::ok);
	}

	@PatchMapping(AbstractDataController.PATH_ID)
	public Mono<ResponseEntity<D>> patch(@PathVariable(name = PATH_VARIABLE_ID) final I id,
	        @RequestBody Map<String, Object> entityMap) {
		return this.service.update(id, entityMap)
		        .map(ResponseEntity::ok);
	}
}
