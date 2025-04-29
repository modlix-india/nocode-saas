package com.fincity.saas.commons.jooq.service;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

import org.jooq.UpdatableRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import reactor.core.publisher.Mono;

public abstract class AbstractJOOQUpdatableDataService<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractUpdatableDTO<I, I>, O extends AbstractUpdatableDAO<R, I, D>>
        extends AbstractJOOQDataService<R, I, D, O> {

    @Autowired
    private ObjectMapper objectMapper;

    public Mono<D> update(I key, Map<String, Object> fields) {

        return this.read(key)
                .flatMap(retrievedObject -> {

                    Mono<Class<D>> pojoClass = this.dao.getPojoClass();

                    return pojoClass.flatMap(e -> {

                        fields.forEach((field, value) -> {

                            String methodName = "set" + field.substring(0, 1).toUpperCase()
                                    + field.substring(1);

                            try {

                                for (Method method : e.getDeclaredMethods()) {

                                    if (method.getName().equals(methodName)) {

                                        Parameter[] params = method.getParameters();

                                        method.invoke(retrievedObject,
                                                this.objectMapper.convertValue(value,
                                                        params[0].getType()));
                                    }

                                }

                            } catch (SecurityException
                                     | IllegalAccessException
                                     | InvocationTargetException | IllegalArgumentException exception) {

                                throw new GenericException(HttpStatus.BAD_REQUEST,
                                        field + AbstractMessageService.FIELD_NOT_AVAILABLE);

                            }

                        });

                        return Mono.just(retrievedObject);
                    });

                }).flatMap(this::update);

    }

    public Mono<D> update(D entity) {

        return this.updatableEntity(entity)
                .flatMap(updatableEntity -> this.getLoggedInUserId()
                        .map(e -> {
                            updatableEntity.setUpdatedBy(e);
                            return updatableEntity;
                        })
                        .defaultIfEmpty(updatableEntity)
                        .flatMap(ent -> this.dao.update(ent)));
    }

    protected abstract Mono<D> updatableEntity(D entity);

}
