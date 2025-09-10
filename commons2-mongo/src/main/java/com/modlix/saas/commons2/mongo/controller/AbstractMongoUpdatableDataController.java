package com.modlix.saas.commons2.mongo.controller;

import java.io.Serializable;

import org.springframework.data.repository.CrudRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;
import com.modlix.saas.commons2.mongo.service.AbstractMongoUpdatableDataService;

public class AbstractMongoUpdatableDataController<I extends Serializable, D extends AbstractUpdatableDTO<I, I>, R extends CrudRepository<D, I>, S extends AbstractMongoUpdatableDataService<I, D, R>>
        extends AbstractMongoDataController<I, D, R, S> {

    @PutMapping(AbstractMongoDataController.PATH_ID)
    public ResponseEntity<D> put(@PathVariable(name = PATH_VARIABLE_ID, required = false) final I id,
            @RequestBody D entity) {
        if (id != null)
            entity.setId(id);
        D result = this.service.update(entity);
        return ResponseEntity.ok(result);
    }
}

