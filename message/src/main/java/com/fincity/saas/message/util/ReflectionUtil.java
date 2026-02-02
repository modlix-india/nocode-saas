package com.fincity.saas.message.util;

import com.fincity.saas.commons.exeception.GenericException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.springframework.http.HttpStatus;

public class ReflectionUtil {

    private ReflectionUtil() {}

    public static <T> T getInstance(Class<T> clazz) {
        Constructor<T> constructor;
        try {
            constructor = clazz.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new GenericException(
                    HttpStatus.BAD_REQUEST, "No-arg constructor not found for class: " + clazz.getName(), e);
        }
        try {
            return constructor.newInstance();
        } catch (InstantiationException e) {
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Class cannot be instantiated: " + clazz.getName(), e);
        } catch (IllegalAccessException e) {
            throw new GenericException(HttpStatus.FORBIDDEN, "Illegal access to constructor of: " + clazz.getName(), e);
        } catch (InvocationTargetException e) {
            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error during instantiation of: " + clazz.getName(),
                    e.getCause());
        }
    }
}
