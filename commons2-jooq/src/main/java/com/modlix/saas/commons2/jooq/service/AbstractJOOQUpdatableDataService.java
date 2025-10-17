package com.modlix.saas.commons2.jooq.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modlix.saas.commons2.configuration.service.AbstractMessageService;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.jooq.dao.AbstractUpdatableDAO;
import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

public abstract class AbstractJOOQUpdatableDataService<
                R extends UpdatableRecord<R>,
                I extends Serializable,
                D extends AbstractUpdatableDTO<I, I>,
                O extends AbstractUpdatableDAO<R, I, D>>
        extends AbstractJOOQDataService<R, I, D, O> {

    @Autowired
    protected ObjectMapper objectMapper;

    public D update(I key, Map<String, Object> fields) {

        D retrievedObject = this.read(key);
        if (retrievedObject == null) {
            return null;
        }

        Class<D> pojoClass = this.dao.getPojoClass();

        fields.forEach((field, value) -> {
            String methodName = "set" + field.substring(0, 1).toUpperCase() + field.substring(1);

            try {

                for (Method method : pojoClass.getDeclaredMethods()) {

                    if (method.getName().equals(methodName)) {

                        Parameter[] params = method.getParameters();

                        method.invoke(retrievedObject, this.objectMapper.convertValue(value, params[0].getType()));
                    }
                }

            } catch (SecurityException
                    | IllegalAccessException
                    | InvocationTargetException
                    | IllegalArgumentException exception) {

                throw new GenericException(HttpStatus.BAD_REQUEST, field + AbstractMessageService.FIELD_NOT_AVAILABLE);
            }
        });

        return this.update(retrievedObject);
    }

    public D update(D entity) {

        D updatableEntity = updatableEntity(entity);
        if (updatableEntity == null) {
            return null;
        }

        I loggedInUserId = this.getLoggedInUserId();
        if (loggedInUserId != null) {
            updatableEntity.setUpdatedBy(loggedInUserId);
        }

        return this.dao.update(updatableEntity);
    }

    protected abstract D updatableEntity(D entity);
}
