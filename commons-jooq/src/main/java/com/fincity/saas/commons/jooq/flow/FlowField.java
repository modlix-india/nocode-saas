package com.fincity.saas.commons.jooq.flow;

import com.google.gson.JsonObject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface FlowField {
    Class<?> type() default JsonObject.class;
}
