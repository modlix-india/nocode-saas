package com.fincity.saas.entity.processor.util;

import com.fincity.saas.commons.exeception.GenericException;
import java.lang.reflect.Field;
import org.springframework.http.HttpStatus;

public class ReflectionUtil {

    private ReflectionUtil() {}

    public static <T> T getStaticFieldValue(Class<?> clazz, String fieldName, Class<T> fieldType) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            return fieldType.cast(field.get(null));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new GenericException(
                    HttpStatus.BAD_REQUEST, "Unable to get the value of the static field: " + fieldName);
        }
    }
}
