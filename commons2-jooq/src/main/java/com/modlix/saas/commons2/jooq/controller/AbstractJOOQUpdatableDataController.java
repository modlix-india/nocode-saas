package com.modlix.saas.commons2.jooq.controller;

import java.io.Serializable;
import java.util.Map;

import org.jooq.UpdatableRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.modlix.saas.commons2.jooq.dao.AbstractUpdatableDAO;
import com.modlix.saas.commons2.jooq.service.AbstractJOOQUpdatableDataService;
import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;

public class AbstractJOOQUpdatableDataController<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractUpdatableDTO<I, I>, O extends AbstractUpdatableDAO<R, I, D>, S extends AbstractJOOQUpdatableDataService<R, I, D, O>>
        extends AbstractJOOQDataController<R, I, D, O, S> {

	@PutMapping(AbstractJOOQDataController.PATH_ID)
	public ResponseEntity<D> put(@PathVariable(name = PATH_VARIABLE_ID, required = false) final I id,
	        @RequestBody D entity) {
		if (id != null)
			entity.setId(id);
		D result = this.service.update(entity);
		return ResponseEntity.ok(result);
	}

	@PatchMapping(AbstractJOOQDataController.PATH_ID)
	public ResponseEntity<D> patch(@PathVariable(name = PATH_VARIABLE_ID) final I id,
	        @RequestBody Map<String, Object> entityMap) {
		D result = this.service.update(id, entityMap);
		return ResponseEntity.ok(result);
	}
}

