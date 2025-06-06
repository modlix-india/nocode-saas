package com.fincity.saas.entity.processor.util;

import com.fincity.saas.commons.exeception.GenericException;
import java.lang.reflect.Field;
import org.springframework.http.HttpStatus;

public class ReflectionUtil {

    private ReflectionUtil() {}

    public static <T> T getStaticFieldValue(Class<?> clazz, String fieldName, Class<T> fieldType) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                Field field = currentClass.getDeclaredField(fieldName);
                return fieldType.cast(field.get(null));
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new GenericException(
                        HttpStatus.BAD_REQUEST, "Unable to access the value of the static field: " + fieldName);
            }
        }
        throw new GenericException(HttpStatus.BAD_REQUEST, "Field not found in class hierarchy: " + fieldName);
    }

    public static <T> T getStaticFieldValueNoError(Class<?> clazz, String fieldName, Class<T> fieldType) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                Field field = currentClass.getDeclaredField(fieldName);
                return fieldType.cast(field.get(null));
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }
}
