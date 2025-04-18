package com.fincity.saas.commons.jooq.convertor.jooq.converters;

import java.io.Serial;

import org.jooq.JSON;

public class JSONtoClassConverter<T, U> extends AbstractJooqConverter<JSON, U> {

    @Serial
    private static final long serialVersionUID = 4084897018025032842L;

    public JSONtoClassConverter(Class<U> toType) {
        super(JSON.class, toType);
    }

    public JSONtoClassConverter(Class<T> fromType, Class<U> toType) {
        super(JSON.class, toType);
    }

    @Override
    protected String toData(JSON databaseObject) {
        if (databaseObject == null) return null;
        return databaseObject.data();
    }

    @Override
    protected JSON toJson(String string) {
        if (string == null) return null;

        return JSON.jsonOrNull(string);
    }

    @Override
    protected U defaultIfError() {
        return null;
    }

    @Override
    protected JSON valueIfNull() {
        return JSON.jsonOrNull("{}");
    }
}
