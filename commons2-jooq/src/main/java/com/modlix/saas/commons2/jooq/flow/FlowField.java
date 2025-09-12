package com.modlix.saas.commons2.jooq.flow;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.google.gson.JsonObject;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface FlowField {
    Class<?> type() default JsonObject.class;
}

