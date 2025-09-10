package com.modlix.saas.commons2.mongo.controller;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.repository.CrudRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.modlix.saas.commons2.model.Query;
import com.modlix.saas.commons2.model.dto.AbstractDTO;
import com.modlix.saas.commons2.mongo.service.AbstractMongoDataService;
import com.modlix.saas.commons2.util.ConditionUtil;

import jakarta.servlet.http.HttpServletRequest;

public class AbstractMongoDataController<I extends Serializable, D extends AbstractDTO<I, I>, R extends CrudRepository<D, I>, S extends AbstractMongoDataService<I, D, R>> {

    public static final String PATH_VARIABLE_ID = "id";
    public static final String PATH_ID = "/{" + PATH_VARIABLE_ID + "}";
    public static final String PATH_QUERY = "query";

    @Autowired
    protected S service;

    @PostMapping
    public ResponseEntity<D> create(@RequestBody D entity) {
        return ResponseEntity.ok(this.service.create(entity));
    }

    @GetMapping(PATH_ID)
    public ResponseEntity<D> read(@PathVariable(PATH_VARIABLE_ID) final I id, HttpServletRequest request) {
        D result = this.service.read(id);
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.notFound().build();
    }

    @GetMapping()
    public ResponseEntity<Page<D>> readPageFilter(Pageable pageable, HttpServletRequest request) {
        pageable = (pageable == null ? PageRequest.of(0, 10, Direction.ASC, PATH_VARIABLE_ID) : pageable);
        Map<String, List<String>> parameterMap = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> parameterMap.put(key, Arrays.asList(values)));
        Page<D> result = this.service.readPageFilter(pageable, ConditionUtil.parameterMapToMap(parameterMap));
        return ResponseEntity.ok(result);
    }

    @PostMapping(PATH_QUERY)
    public ResponseEntity<Page<D>> readPageFilter(@RequestBody Query query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), query.getSort());
        Page<D> result = this.service.readPageFilter(pageable, query.getCondition());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping(PATH_ID)
    public ResponseEntity<Boolean> delete(@PathVariable(PATH_VARIABLE_ID) final I id) {
        boolean result = this.service.delete(id);
        return ResponseEntity.ok(result);
    }
}

