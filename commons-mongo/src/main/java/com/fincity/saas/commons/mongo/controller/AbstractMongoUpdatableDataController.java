package com.fincity.saas.commons.mongo.controller;

import java.io.Serializable;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.mongo.service.AbstractMongoUpdatableDataService;

import reactor.core.publisher.Mono;

public class AbstractMongoUpdatableDataController<I extends Serializable, D extends AbstractUpdatableDTO<I, I>, R extends ReactiveCrudRepository<D, I>, S extends AbstractMongoUpdatableDataService<I, D, R>>
        extends AbstractMongoDataController<I, D, R, S> {

	@PutMapping(AbstractMongoDataController.PATH_ID)
	public Mono<ResponseEntity<D>> put(@PathVariable(name = PATH_VARIABLE_ID, required = false) final I id,
	        @RequestBody D entity) {
		if (id != null)
			entity.setId(id);
		return this.service.update(entity)
		        .map(ResponseEntity::ok);
	}
}
