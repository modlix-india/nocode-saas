package com.modlix.saas.commons2.jooq.controller;

import java.beans.PropertyEditorSupport;
import java.io.Serializable;

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
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.modlix.saas.commons2.jooq.dao.AbstractDAO;
import com.modlix.saas.commons2.jooq.service.AbstractJOOQDataService;
import com.modlix.saas.commons2.model.Query;
import com.modlix.saas.commons2.model.dto.AbstractDTO;
import com.modlix.saas.commons2.util.ConditionUtil;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AbstractJOOQDataController<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractDTO<I, I>, O extends AbstractDAO<R, I, D>, S extends AbstractJOOQDataService<R, I, D, O>> {

    public static final String PATH_VARIABLE_ID = "id";
    public static final String PATH_ID = "/{" + PATH_VARIABLE_ID + "}";
    public static final String PATH_QUERY = "query";

    @Autowired
    protected S service;

    @InitBinder
    public void initBinder(DataBinder binder) {
        binder.registerCustomEditor(ULong.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null)
                    setValue(null);
                else
                    setValue(ULong.valueOf(text));
            }
        });
        binder.registerCustomEditor(UInteger.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null)
                    setValue(null);
                else
                    setValue(UInteger.valueOf(text));
            }
        });
        binder.registerCustomEditor(UShort.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null)
                    setValue(null);
                else
                    setValue(UShort.valueOf(text));
            }
        });
    }

    @PostMapping
    public ResponseEntity<D> create(@RequestBody D entity) {
        D result = this.service.create(entity);
        return ResponseEntity.ok(result);
    }

    @GetMapping(PATH_ID)
    public ResponseEntity<D> read(@PathVariable(PATH_VARIABLE_ID) final I id, HttpServletRequest request) {
        D result = this.service.read(id);
        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.notFound().build();
        }
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
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    public Integer delete(@PathVariable(PATH_VARIABLE_ID) final I id) {
        return this.service.delete(id);
    }
}
